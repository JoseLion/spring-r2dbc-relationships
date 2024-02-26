package io.github.joselion.springr2dbcrelationships.processors;

import static java.util.function.Predicate.not;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import java.lang.reflect.Field;
import java.util.Optional;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import io.github.joselion.springr2dbcrelationships.annotations.ManyToOne;
import io.github.joselion.springr2dbcrelationships.exceptions.RelationshipException;
import io.github.joselion.springr2dbcrelationships.helpers.Commons;
import io.github.joselion.springr2dbcrelationships.helpers.Reflect;
import reactor.core.publisher.Mono;

/**
 * The {@link ManyToOne} annotation processor.
 *
 * @param template the r2dbc entity template
 * @param entity the processed field entity
 * @param table the processed field entity table
 */
public record ManyToOneProcessor(
  R2dbcEntityTemplate template,
  Object entity,
  SqlIdentifier table
) implements Processable<ManyToOne, Object> {

  @Override
  public Mono<Object> populate(final ManyToOne annotation, final Field field) {
    final var fieldProjection = field.getType();
    final var fieldType = this.domainFor(fieldProjection);
    final var foreignKey = Optional.of(annotation)
      .map(ManyToOne::foreignKey)
      .filter(not(String::isBlank))
      .orElseGet(() -> this.tableNameOf(fieldType).concat("_id"));
    final var foreignField = Commons.toCamelCase(foreignKey);
    final var parentId = this.idColumnOf(fieldType);
    final var keyValue = Optional.of(this.entity)
      .map(Reflect.getter(foreignField))
      .orElseThrow(() -> {
        final var entityType = this.domainFor(this.entity.getClass());
        final var message = "Entity <%s> is missing foreign key in field: %s".formatted(
          entityType.getName(),
          foreignField
        );

        return RelationshipException.of(message);
      });

    return this.template
      .select(fieldType)
      .as(fieldProjection)
      .matching(query(where(parentId).is(keyValue)))
      .one()
      .map(Commons::cast);
  }

  @Override
  public Mono<Object> persist(final ManyToOne annotation, final Field field) {
    final var fieldType = this.domainFor(field.getType());
    final var foreignKey = Optional.of(annotation)
      .map(ManyToOne::foreignKey)
      .filter(not(String::isBlank))
      .orElseGet(() -> this.tableNameOf(fieldType).concat("_id"));
    final var foreignField = Commons.toCamelCase(foreignKey);

    return Mono.just(this.entity)
      .mapNotNull(Reflect.getter(field))
      .flatMap(this::upsert)
      .map(saved -> {
        final var savedId = this.idValueOf(saved);
        final var newEntity = Reflect.update(this.entity, field, saved);
        return Reflect.update(newEntity, foreignField, savedId);
      });
  }
}