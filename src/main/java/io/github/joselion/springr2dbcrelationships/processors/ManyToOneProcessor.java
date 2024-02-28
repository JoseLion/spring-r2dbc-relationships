package io.github.joselion.springr2dbcrelationships.processors;

import static java.util.function.Predicate.not;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.context.ApplicationContext;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import io.github.joselion.springr2dbcrelationships.annotations.ManyToOne;
import io.github.joselion.springr2dbcrelationships.annotations.OneToMany;
import io.github.joselion.springr2dbcrelationships.exceptions.RelationshipException;
import io.github.joselion.springr2dbcrelationships.helpers.Commons;
import io.github.joselion.springr2dbcrelationships.helpers.Reflect;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The {@link ManyToOne} annotation processor.
 *
 * @param template the r2dbc entity template
 * @param entity the processed field entity
 * @param table the processed field entity table
 * @param context the Spring application context
 */
public record ManyToOneProcessor(
  R2dbcEntityTemplate template,
  Object entity,
  SqlIdentifier table,
  ApplicationContext context
) implements Processable<ManyToOne, Object> {

  @Override
  public Mono<Object> populate(final ManyToOne annotation, final Field field) {
    final var entityType = this.domainFor(this.entity.getClass());
    final var fieldProjection = field.getType();
    final var fieldType = this.domainFor(fieldProjection);
    final var byTable = this.tableNameOf(fieldType).concat("_id");
    final var byField = Commons.toSnakeCase(field.getName()).concat("_id");
    final var foreignField = Optional.of(annotation)
      .map(ManyToOne::foreignKey)
      .map(Commons::toCamelCase)
      .filter(not(String::isBlank))
      .or(() -> this.inferForeignField(byTable).map(Field::getName))
      .or(() -> this.inferForeignField(byField).map(Field::getName))
      .orElseThrow(() -> {
        final var message = """
          Unable to infer foreign key for "%s" entity. Neither "%s" nor "%s"
          associated fields could be found
          """
          .formatted(entityType.getSimpleName(), byTable, byField);
        return RelationshipException.of(message);
      });
    final var parentId = this.idColumnOf(fieldType);

    return Mono.just(this.entity)
      .mapNotNull(Reflect.getter(foreignField))
      .flatMap(fkValue ->
        Mono.deferContextual(ctx -> {
          final var store = ctx.<List<Object>>getOrDefault(OneToMany.class, List.of());

          return Flux.fromIterable(store)
            .filter(fkValue::equals)
            .collectList()
            .filter(List::isEmpty)
            .map(x -> fkValue);
        })
      )
      .flatMap(fkValue ->
        this.template
          .select(fieldType)
          .as(fieldProjection)
          .matching(query(where(parentId).is(fkValue)))
          .one()
          .contextWrite(ctx -> {
            final var store = ctx.<List<Object>>getOrDefault(ManyToOne.class, List.of());
            final var next = Stream.concat(store.stream(), Stream.of(fkValue)).toList();

            return ctx.put(ManyToOne.class, next);
          })
      );
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
      .flatMap(this::save)
      .map(saved -> {
        final var savedId = this.idValueOf(saved);
        final var newEntity = Reflect.update(this.entity, field, saved);
        return Reflect.update(newEntity, foreignField, savedId);
      });
  }
}
