package io.github.joselion.springr2dbcrelationships.processors;

import static java.util.function.Predicate.not;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import java.lang.reflect.Field;
import java.util.Optional;

import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import io.github.joselion.springr2dbcrelationships.annotations.OneToOne;
import io.github.joselion.springr2dbcrelationships.exceptions.RelationshipException;
import io.github.joselion.springr2dbcrelationships.helpers.Commons;
import io.github.joselion.springr2dbcrelationships.helpers.Reflect;
import reactor.core.publisher.Mono;

/**
 * The {@link OneToOne} annotation processor.
 *
 * @param template the r2dbc entity template
 * @param entity the processed field entity
 * @param table the processed field entity table
 */
public record OneToOneProcessor(
  R2dbcEntityTemplate template,
  Object entity,
  SqlIdentifier table
) implements Processable<OneToOne, Object> {

  @Override
  public Mono<Object> populate(final OneToOne annotation, final Field field) {
    final var fieldType = field.getType();
    final var isBackReference = Optional.of(annotation)
      .filter(OneToOne::backReference)
      .isPresent();
    final var mappedBy = Optional.of(annotation)
      .map(OneToOne::mappedBy)
      .filter(not(String::isBlank))
      .orElseGet(() -> {
        final var prefix = isBackReference
          ? this.tableNameOf(fieldType)
          : this.table.getReference();

        return prefix.concat("_id");
      });

    if (isBackReference) {
      final var parentId = this.idColumnOf(fieldType);
      final var mappedField = Commons.toCamelCase(mappedBy);
      final var fkValue = Optional.of(this.entity)
        .map(Reflect.getter(mappedField))
        .orElseThrow(() -> {
          final var message = "Entity <%s> is missing foreign key in field: %s".formatted(
            this.entity.getClass().getName(),
            mappedField
          );

          return RelationshipException.of(message);
        });

      return this.checkCycles()
        .flatMap(x ->
          this.template
            .select(this.domainFor(fieldType))
            .as(fieldType)
            .matching(query(where(parentId).is(fkValue)))
            .one()
        );
    }

    return Mono.just(this.entity)
      .mapNotNull(this::idValueOf)
      .flatMap(entityId ->
        this.template
          .select(this.domainFor(fieldType))
          .as(fieldType)
          .matching(query(where(mappedBy).is(entityId)))
          .one()
      );
  }

  @Override
  public Mono<Object> persist(final OneToOne annotation, final Field field) {
    return Mono.just(this.entity)
      .mapNotNull(this::idValueOf)
      .flatMap(entityId -> {
        final var fieldType = field.getType();
        final var entityName = this.entity.getClass().getSimpleName();
        final var mappedBy = Optional.of(annotation)
          .map(OneToOne::mappedBy)
          .filter(not(String::isBlank))
          .orElseGet(() -> this.table.getReference().concat("_id"));
        final var fkFieldName = Commons.uncapitalize(entityName).concat("Id");
        final var initial = Reflect.getter(this.entity, field);
        final var value = Optional.ofNullable(initial)
          .map(Reflect.update(fkFieldName, entityId))
          .orElse(initial);

        return Mono.justOrEmpty(value)
          .flatMap(this::upsert)
          .switchIfEmpty(
            this.template
              .delete(this.domainFor(fieldType))
              .matching(query(where(mappedBy).is(entityId)))
              .all()
              .then(Mono.empty())
          );
      });
  }
}
