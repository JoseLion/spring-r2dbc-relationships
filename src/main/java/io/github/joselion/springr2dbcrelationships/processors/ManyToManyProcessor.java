package io.github.joselion.springr2dbcrelationships.processors;

import static java.util.Arrays.stream;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static reactor.function.TupleUtils.function;

import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.springframework.context.ApplicationContext;
import org.springframework.data.mapping.callback.ReactiveEntityCallbacks;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import io.github.joselion.springr2dbcrelationships.annotations.ManyToMany;
import io.github.joselion.springr2dbcrelationships.annotations.OneToMany;
import io.github.joselion.springr2dbcrelationships.exceptions.RelationshipException;
import io.github.joselion.springr2dbcrelationships.helpers.Reflect;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The {@link OneToMany} annotation processor.
 *
 * @param template the r2dbc entity template
 * @param entity the processed field entity
 * @param table the processed field entity table
 * @param context the Spring application context
 */
public record ManyToManyProcessor(
  R2dbcEntityTemplate template,
  Object entity,
  SqlIdentifier table,
  ApplicationContext context
) implements Processable<ManyToMany, List<?>> {

  @Override
  public Mono<List<?>> populate(final ManyToMany annotation, final Field field) {
    final var entityType = this.domainFor(this.entity().getClass());
    final var innerType = this.domainFor(Reflect.innerTypeOf(field));
    final var entityTable = this.tableNameOf(entityType);
    final var innerTable = this.tableNameOf(innerType);
    final var innerId = this.idColumnOf(innerType);
    final var mappedBy = Optional.of(annotation)
      .map(ManyToMany::mappedBy)
      .filter(not(String::isBlank))
      .orElseGet(() -> entityTable.concat("_id"));
    final var linkedBy = Optional.of(annotation)
      .map(ManyToMany::linkedBy)
      .filter(not(String::isBlank))
      .orElseGet(() -> innerTable.concat("_id"));
    final var sortIn = annotation.sortIn().name();
    final var orderBy = Optional.of(annotation)
      .map(ManyToMany::sortBy)
      .filter(not(String::isBlank))
      .or(() -> this.createdColumnOf(innerType))
      .map(sortBy -> "ORDER BY b.%s %s".formatted(sortBy, sortIn))
      .orElse("");
    final var columns = stream(innerType.getDeclaredFields())
      .map(this::columnNameOrNull)
      .filter(Objects::nonNull)
      .map("b."::concat)
      .collect(joining(", "));
    final var partialStatement = """
      SELECT %s FROM %s AS b
        LEFT JOIN %s AS j ON j.%s = b.%s
      WHERE j.%s = $1
      %s
      """
      .formatted(columns, innerTable, "%s", linkedBy, innerId, mappedBy, orderBy);

    return Mono.just(annotation)
      .map(ManyToMany::joinTable)
      .filter(Predicate.not(String::isBlank))
      .switchIfEmpty(this.findJoinTable(field))
      .map(partialStatement::formatted)
      .flatMap(statement ->
        Mono.just(this.entity)
          .mapNotNull(this::idValueOf)
          .flatMap(entityId ->
            Mono.deferContextual(ctx -> {
              final var store = ctx.getOrDefault(ManyToMany.class, List.<Object>of());

              return Flux.fromIterable(store)
                .filter(entityId::equals)
                .collectList()
                .filter(List::isEmpty)
                .map(x -> entityId);
            })
          )
          .flatMap(entityId ->
            this.template
              .getDatabaseClient()
              .sql(statement)
              .bind(0, entityId)
              .map((row, meta) -> this.template.getConverter().read(innerType, row, meta))
              .all()
              .flatMap(this.withCallbacksOf(innerType), 1)
              .collectList()
              .contextWrite(ctx -> {
                final var store = ctx.getOrDefault(ManyToMany.class, List.<Object>of());
                final var next = Stream.concat(store.stream(), Stream.of(entityId)).toList();

                return ctx.put(ManyToMany.class, next);
              })
          )
      );
  }

  @Override
  public Mono<List<?>> persist(final ManyToMany annotation, final Field field) {
    final var values = Reflect.<List<Object>>getter(this.entity, field);

    if (values == null) {
      return Mono.empty();
    }

    return Mono.just(this.entity)
      .mapNotNull(this::idValueOf)
      .zipWith(
        Mono.just(annotation)
          .map(ManyToMany::joinTable)
          .filter(not(String::isBlank))
          .switchIfEmpty(this.findJoinTable(field))
      )
      .flatMap(function((entityId, joinTable) -> {
        final var entityType = this.domainFor(this.entity.getClass());
        final var innerType = this.domainFor(Reflect.innerTypeOf(field));
        final var entityTable = this.tableNameOf(entityType);
        final var innerTable = this.tableNameOf(innerType);
        final var innerId = this.idColumnOf(innerType);
        final var mappedBy = Optional.of(annotation)
          .map(ManyToMany::mappedBy)
          .filter(not(String::isBlank))
          .orElseGet(() -> entityTable.concat("_id"));
        final var linkedBy = Optional.of(annotation)
          .map(ManyToMany::linkedBy)
          .filter(not(String::isBlank))
          .orElseGet(() -> innerTable.concat("_id"));
        final var orphansStatement = """
          DELETE FROM %s
          WHERE %s NOT IN (
            SELECT j.%s FROM %s AS j
            WHERE j.%s = $1
          )
          """
          .formatted(innerTable, innerId, linkedBy, joinTable, mappedBy);
        final var deleteOrphans = Mono.just(annotation)
          .filter(ManyToMany::deleteOrphans)
          .flatMap(y ->
            this.template
              .getDatabaseClient()
              .sql(orphansStatement)
              .bind(0, entityId)
              .fetch()
              .rowsUpdated()
          );

        if (values.isEmpty()) {
          return this.template
            .getDatabaseClient()
            .sql("DELETE FROM %s WHERE %s = $1".formatted(joinTable, mappedBy))
            .bind(0, entityId)
            .fetch()
            .rowsUpdated()
            .delayUntil(x -> deleteOrphans)
            .map(x -> List.of());
        }

        return Mono.just(annotation)
          .filter(not(ManyToMany::linkOnly))
          .flatMap(x ->
            Flux.fromIterable(values)
              .map(this::toPreventingCycles)
              .flatMap(this::save)
              .collectList()
          )
          .defaultIfEmpty(values)
          .delayUntil(items ->
            items.stream()
              .filter(item -> this.idValueOf(item) == null)
              .findFirst()
              .map(Object::toString)
              .map("Link-only entity is missing its primary key: "::concat)
              .map(RelationshipException::of)
              .map(Mono::error)
              .orElseGet(Mono::empty)
          )
          .delayUntil(newItems -> {
            final var paramsTemplate = IntStream.range(0, newItems.size())
              .mapToObj("(:entityId, :link[%d])"::formatted)
              .collect(joining(", "));
            final var params = IntStream.range(0, newItems.size())
              .mapToObj(i -> Map.entry("link[%d]".formatted(i), this.idValueOf(newItems.get(i))))
              .collect(toMap(Entry::getKey, Entry::getValue));
            final var statement = MessageFormat.format(
              """
              INSERT INTO {0} ({1}, {2}) (
                SELECT t.* FROM (VALUES {3}) AS t(mapped, linked)
                WHERE t.linked NOT IN (SELECT {2} FROM {0} WHERE {1} = :entityId)
              )
              """,
              joinTable, mappedBy, linkedBy, paramsTemplate
            );

            return this.template
              .getDatabaseClient()
              .sql(statement)
              .bind("entityId", entityId)
              .bindValues(params)
              .fetch()
              .rowsUpdated();
          })
          .delayUntil(items -> {
            final var paramsTemplate = IntStream.range(2, items.size() + 2)
              .mapToObj(i -> "$" + i)
              .collect(joining(", "));
            final var statement = """
              DELETE FROM %s
              WHERE %s = $1 AND %s NOT IN (%s)
              """
              .formatted(joinTable, mappedBy, linkedBy, paramsTemplate);
            final var params = IntStream.range(2, items.size() + 2)
              .mapToObj(i -> Map.entry("$" + i, this.idValueOf(items.get(i - 2))))
              .collect(toMap(Entry::getKey, Entry::getValue));

            return this.template
              .getDatabaseClient()
              .sql(statement)
              .bind(0, entityId)
              .bindValues(params)
              .fetch()
              .rowsUpdated()
              .delayUntil(x -> deleteOrphans);
          });
      }));
  }

  private Mono<String> findJoinTable(final String left, final String right) {
    final var client = this.template.getDatabaseClient();
    final var joinTable = left.concat("_").concat(right);
    final var tableStatement = """
      SELECT count(*) FROM information_schema.tables
      WHERE upper(table_name) = upper($1)
      LIMIT 1
      """;

    return client.sql(tableStatement)
      .bind(0, joinTable)
      .mapValue(Long.class)
      .one()
      .filter(value -> value > 0)
      .map(x -> joinTable);
  }

  private Mono<String> findJoinTable(final Field field) {
    final var entityType = this.domainFor(this.entity().getClass());
    final var innerType = this.domainFor(Reflect.innerTypeOf(field));
    final var entityTable = this.tableNameOf(entityType);
    final var innerTable = this.tableNameOf(innerType);

    return this.findJoinTable(entityTable, innerTable)
      .switchIfEmpty(this.findJoinTable(innerTable, entityTable))
      .switchIfEmpty(
        Mono.just("Unable to infer join table. Neither {0}_{1} nor {1}_{0} exists")
          .map(message -> MessageFormat.format(message, entityTable, innerTable))
          .map(RelationshipException::of)
          .flatMap(Mono::error)
      );
  }

  private <T> Function<T, Mono<T>> withCallbacksOf(final Class<?> type) {
    return value -> {
      final var tableId = this.tableIdentifierOf(type);

      return ReactiveEntityCallbacks.create(this.context)
        .callback(AfterConvertCallback.class, value, tableId)
        .defaultIfEmpty(value);
    };
  }

  private <T> T toPreventingCycles(final T value) {
    final var entityType = this.domainFor(this.entity.getClass());
    final var valueFields = value.getClass().getDeclaredFields();

    return stream(valueFields)
      .filter(field -> field.isAnnotationPresent(ManyToMany.class))
      .filter(field -> this.domainFor(Reflect.innerTypeOf(field)).equals(entityType))
      .reduce(
        value,
        (acc, field) -> Reflect.update(acc, field, null),
        (a, b) -> b
      );
  }
}
