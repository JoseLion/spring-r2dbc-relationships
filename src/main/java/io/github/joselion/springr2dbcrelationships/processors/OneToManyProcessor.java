package io.github.joselion.springr2dbcrelationships.processors;

import static java.util.function.Predicate.not;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

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

/**
 * The {@link OneToMany} annotation processor.
 *
 * @param template the r2dbc entity template
 * @param entity the processed field entity
 * @param table the processed field entity table
 */
public record OneToManyProcessor(
  R2dbcEntityTemplate template,
  Object entity,
  SqlIdentifier table
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
      .flatMap(entityId ->
        this.template
          .select(innerType)
          .as(innerProjection)
          .matching(query(where(mappedBy).is(entityId)).sort(byColumn))
          .all()
          .collectList()
      );
  }

  @Override
  public Mono<List<?>> persist(final OneToMany annotation, final Field field) {
    return Mono.just(this.entity)
      .mapNotNull(this::idValueOf)
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
        final var values = Optional.of(this.entity)
          .map(Reflect.<List<?>>getter(field))
          .orElseGet(List::of);

        return Flux.fromIterable(values)
          .map(x -> Reflect.update(x, mappedField, entityId))
          .flatMap(this::upsert)
          .collectList()
          .delayUntil(children -> {
            final var innerId = this.idColumnOf(innerType);
            final var ids = children.stream()
              .map(this::idValueOf)
              .toList();

            return this.template
              .delete(innerType)
              .matching(query(where(mappedBy).is(entityId).and(innerId).notIn(ids)))
              .all();
          });
      });
  }
}
