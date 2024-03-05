package io.github.joselion.springr2dbcrelationships.processors;

import static java.util.function.Predicate.not;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;
import static org.springframework.data.relational.core.query.Update.update;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import io.github.joselion.maybe.Maybe;
import io.github.joselion.springr2dbcrelationships.annotations.OneToMany;
import io.github.joselion.springr2dbcrelationships.exceptions.RelationshipException;
import io.github.joselion.springr2dbcrelationships.helpers.Commons;
import io.github.joselion.springr2dbcrelationships.helpers.Reflect;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * The {@link OneToMany} annotation processor.
 *
 * @param template the r2dbc entity template
 * @param entity the processed field entity
 * @param table the processed field entity table
 * @param context the Spring application context
 */
public record OneToManyProcessor(
  R2dbcEntityTemplate template,
  Object entity,
  SqlIdentifier table,
  ApplicationContext context
) implements Processable<OneToMany, List<?>> {

  @Override
  public Mono<List<?>> populate(final OneToMany annotation, final Field field) {
    final var innerProjection = Reflect.innerTypeOf(field);
    final var innerType = this.domainFor(innerProjection);
    final var mappedBy = Optional.of(annotation)
      .map(OneToMany::mappedBy)
      .filter(not(String::isBlank))
      .orElseGet(() -> this.table.getReference().concat("_id"));
    final var byColumn = Optional.of(annotation)
      .map(OneToMany::sortBy)
      .filter(not(String::isBlank))
      .or(() -> this.createdColumnOf(this.entity))
      .map(sortBy -> Sort.by(annotation.sortIn(), sortBy))
      .orElseGet(Sort::unsorted);

    return Mono.just(this.entity)
      .mapNotNull(this::idValueOf)
      .flatMap(this::breackingCycles)
      .flatMap(entityId ->
        this.template
          .select(innerType)
          .as(innerProjection)
          .matching(query(where(mappedBy).is(entityId)).sort(byColumn))
          .all()
          .collectList()
          .contextWrite(this.storeWith(entityId))
      );
  }

  @Override
  public Mono<List<?>> persist(final OneToMany annotation, final Field field) {
    final var values = Reflect.<List<?>>getter(this.entity, field);

    if (values == null) {
      return Mono.empty();
    }

    return Mono.just(this.entity)
      .mapNotNull(this::idValueOf)
      .flatMap(this::breackingCycles)
      .flatMap(entityId -> {
        final var innerType = this.domainFor(Reflect.innerTypeOf(field));
        final var mappedBy = Optional.of(annotation)
          .map(OneToMany::mappedBy)
          .filter(not(String::isBlank))
          .orElseGet(() -> this.table.getReference().concat("_id"));
        final var mappedField = Maybe.of(mappedBy)
          .map(Commons::toCamelCase)
          .solve(innerType::getDeclaredField)
          .orThrow(RelationshipException::of);

        return Flux.fromIterable(values)
          .flatMap(value -> {
            if (annotation.linkOnly()) {
              final var innerTable = this.tableNameOf(innerType);
              final var innerId = this.idColumnOf(innerType);
              final var statement = "UPDATE %s SET %s = $1 WHERE %s = $2".formatted(innerTable, mappedBy, innerId);
              final var linked = Reflect.update(value, mappedField, entityId);
              final var missingId = RelationshipException.of("Link-only entity is missing its primary key: " + linked);

              return Mono.just(value)
                .mapNotNull(this::idValueOf)
                .flatMap(valueId ->
                  this.template
                    .getDatabaseClient()
                    .sql(statement)
                    .bind(0, entityId)
                    .bind(1, valueId)
                    .fetch()
                    .rowsUpdated()
                )
                .map(x -> linked)
                .switchIfEmpty(Mono.error(missingId));
            }

            return Mono.just(value)
              .map(Reflect.update(mappedField, entityId))
              .flatMap(this::save);
          })
          .collectList()
          .delayUntil(children -> {
            final var keepOrphans = annotation.keepOrphans();
            final var innerId = this.idColumnOf(innerType);
            final var ids = children.stream().map(this::idValueOf).toList();
            final var allOrphans = query(where(mappedBy).is(entityId).and(innerId).notIn(ids));

            if (keepOrphans) {
              return this.template
                .update(innerType)
                .matching(allOrphans)
                .apply(update(mappedBy, null));
            }

            return this.template
              .delete(innerType)
              .matching(allOrphans)
              .all();
          })
          .contextWrite(this.storeWith(entityId));
      });
  }

  private <T> Mono<T> breackingCycles(final T entityId) {
    return Mono.deferContextual(ctx -> {
      final var store = ctx.<List<Object>>getOrDefault(OneToMany.class, List.of());

      return Flux.fromIterable(store)
        .filter(entityId::equals)
        .collectList()
        .filter(List::isEmpty)
        .map(x -> entityId);
    });
  }

  private Function<Context, Context> storeWith(final Object entityId) {
    return ctx -> {
      final var store = ctx.<List<Object>>getOrDefault(OneToMany.class, List.of());
      final var next = Stream.concat(store.stream(), Stream.of(entityId)).toList();

      return ctx.put(OneToMany.class, next);
    };
  }
}
