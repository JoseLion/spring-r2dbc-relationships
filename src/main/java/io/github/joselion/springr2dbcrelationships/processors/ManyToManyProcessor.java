package io.github.joselion.springr2dbcrelationships.processors;

import static java.util.function.Predicate.isEqual;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static reactor.function.TupleUtils.function;

import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import org.springframework.context.ApplicationContext;
import org.springframework.data.mapping.callback.ReactiveEntityCallbacks;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import io.github.joselion.springr2dbcrelationships.RelationshipCallbacks;
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
    final var partialStatement = """
      SELECT DISTINCT b.* FROM %s AS b
        LEFT JOIN %s AS j ON j.%s = b.%s
      WHERE j.%s = $1
      %s
      """
      .formatted(innerTable, "%s", linkedBy, innerId, mappedBy, orderBy);

    return Mono.just(annotation)
      .map(ManyToMany::joinTable)
      .filter(Predicate.not(String::isBlank))
      .switchIfEmpty(this.findJoinTable(field))
      .map(partialStatement::formatted)
      .flatMap(statement ->
        Mono.just(this.entity)
          .mapNotNull(this::idValueOf)
          .flatMapMany(id ->
            this.template
              .getDatabaseClient()
              .sql(statement)
              .bind(0, id)
              .map((row, meta) -> this.template.getConverter().read(innerType, row, meta))
              .all()
          )
          .flatMap(value ->
            ReactiveEntityCallbacks
              .create(this.context)
              .callback(
                AfterConvertCallback.class,
                value,
                this.tableIdentifierOf(innerType)
              )
          )
          .collectList()
      );
  }

  @Override
  public Mono<List<?>> persist(final ManyToMany annotation, final Field field) {
    final var entityType = this.entity.getClass();
    final var innerType = Reflect.innerTypeOf(field);
    final var entityTable = this.tableNameOf(entityType);
    final var innerTable = this.tableNameOf(innerType);
    final var mappedBy = Optional.of(annotation)
      .map(ManyToMany::mappedBy)
      .filter(not(String::isBlank))
      .orElseGet(() -> entityTable.concat("_id"));
    final var linkedBy = Optional.of(annotation)
      .map(ManyToMany::linkedBy)
      .filter(not(String::isBlank))
      .orElseGet(() -> innerTable.concat("_id"));
    final var values = Optional.of(this.entity)
      .map(Reflect.<List<?>>getter(field))
      .orElseGet(List::of);

    return this.checkingBackRef(innerType, this.entity)
      .mapNotNull(this::idValueOf)
      .zipWith(
        Mono.just(annotation)
          .map(ManyToMany::joinTable)
          .filter(not(String::isBlank))
          .switchIfEmpty(this.findJoinTable(field))
      )
      .flatMap(function((id, joinTable) ->
        Flux.fromIterable(values)
          .flatMap(this::upsert)
          .collectList()
          .filter(not(List::isEmpty))
          .delayUntil(items ->
            Flux.fromIterable(items)
              .filter(item ->
                values
                  .stream()
                  .filter(this::isNew)
                  .map(this::idValueOf)
                  .anyMatch(not(isEqual(this.idValueOf(item))))
              )
              .collectList()
              .filter(not(List::isEmpty))
              .flatMap(newItems -> {
                final var paramsTemplate = IntStream.range(2, newItems.size() + 2)
                  .mapToObj("($1, $%d)"::formatted)
                  .collect(joining(", "));
                final var statement = "INSERT INTO %s (%s, %s) VALUES %s".formatted(
                  joinTable,
                  mappedBy,
                  linkedBy,
                  paramsTemplate
                );
                final var params = IntStream.range(2, newItems.size() + 2)
                  .mapToObj(i -> Map.entry("$" + i, this.idValueOf(newItems.get(i - 2))))
                  .collect(toMap(Entry::getKey, Entry::getValue));

                return this.template
                  .getDatabaseClient()
                  .sql(statement)
                  .bind(0, id)
                  .bindValues(params)
                  .fetch()
                  .rowsUpdated();
              })
          )
          .delayUntil(items -> {
            final var paramsTemplate = IntStream.range(2, items.size() + 2)
              .mapToObj(i -> "$" + i)
              .collect(joining(", "));
            final var statement = "DELETE FROM %s WHERE %s = $1 AND %s NOT IN (%s)".formatted(
              joinTable,
              mappedBy,
              linkedBy,
              paramsTemplate
            );
            final var params = IntStream.range(2, items.size() + 2)
              .mapToObj(i -> Map.entry("$" + i, this.idValueOf(items.get(i - 2))))
              .collect(toMap(Entry::getKey, Entry::getValue));

            return this.template
              .getDatabaseClient()
              .sql(statement)
              .bind(0, id)
              .bindValues(params)
              .fetch()
              .rowsUpdated();
          })
          .switchIfEmpty(
            this.template
              .getDatabaseClient()
              .sql("DELETE FROM %s WHERE %s = $1".formatted(joinTable, mappedBy))
              .bind(0, id)
              .fetch()
              .rowsUpdated()
              .map(x -> List.of())
          )
      ));
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

  private <S> Mono<S> checkingBackRef(final Class<?> type, final S value) {
    return Mono.deferContextual(ctx -> {
      final var stack = ctx.<List<String>>getOrEmpty(RelationshipCallbacks.class);

      return Mono.justOrEmpty(stack)
        .defaultIfEmpty(List.of())
        .filter(not(s -> s.size() >= 2 && s.get(s.size() - 2).equals(type.getName())))
        .map(x -> value);
    });
  }
}
