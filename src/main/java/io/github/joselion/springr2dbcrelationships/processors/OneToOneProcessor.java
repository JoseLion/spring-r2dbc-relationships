package io.github.joselion.springr2dbcrelationships.processors;

import static java.util.function.Predicate.not;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import org.springframework.context.ApplicationContext;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import io.github.joselion.springr2dbcrelationships.annotations.OneToOne;
import io.github.joselion.springr2dbcrelationships.exceptions.RelationshipException;
import io.github.joselion.springr2dbcrelationships.helpers.Commons;
import io.github.joselion.springr2dbcrelationships.helpers.Reflect;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * The {@link OneToOne} annotation processor.
 *
 * @param template the r2dbc entity template
 * @param entity the processed field entity
 * @param table the processed field entity table
 * @param context the Spring application context
 */
public record OneToOneProcessor(
  R2dbcEntityTemplate template,
  Object entity,
  SqlIdentifier table,
  ApplicationContext context
) implements Processable<OneToOne, Object> {

  @Override
  public Mono<Object> populate(final OneToOne annotation, final Field field) {
    final var entityType = this.domainFor(this.entity.getClass());
    final var fieldProjection = field.getType();
    final var fieldType = this.domainFor(fieldProjection);
    final var isBackReference = Optional.of(annotation)
      .filter(OneToOne::backreference)
      .isPresent();
    final var mappedBy = Optional.of(annotation)
      .map(OneToOne::mappedBy)
      .filter(not(String::isBlank));

    if (isBackReference) {
      final var parentId = this.idColumnOf(fieldType);
      final var byTable = this.tableNameOf(fieldType).concat("_id");
      final var byField = Commons.toSnakeCase(field.getName()).concat("_id");
      final var mappedField = mappedBy
        .map(Commons::toCamelCase)
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

      return Mono.just(this.entity)
        .mapNotNull(Reflect.getter(mappedField))
        .flatMap(this::breakOnCycle)
        .flatMap(fkValue ->
          this.template
            .select(fieldType)
            .as(fieldProjection)
            .matching(query(where(parentId).is(fkValue)))
            .one()
            .contextWrite(this.storeOf(fkValue))
        );
    }

    return Mono.just(this.entity)
      .mapNotNull(this::idValueOf)
      .flatMap(this::breakOnCycle)
      .flatMap(entityId -> {
        final var byTable = this.table.getReference().concat("_id");
        final var byField = Commons.toSnakeCase(field.getName()).concat("_id");
        final var mappedField = mappedBy
          .or(() -> this.inferForeignField(byTable, fieldType).map(this::columnNameOf))
          .or(() -> this.inferForeignField(byField, fieldType).map(this::columnNameOf))
          .orElseThrow(() -> {
            final var message = """
              Unable to infer foreign key for "%s" entity. Neither "%s" nor "%s"
              associated fields could be found
              """
              .formatted(fieldType.getSimpleName(), byTable, byField);
            return RelationshipException.of(message);
          });

        return this.template
          .select(fieldType)
          .as(fieldProjection)
          .matching(query(where(mappedField).is(entityId)))
          .one()
          .contextWrite(this.storeOf(entityId));
      });
  }

  @Override
  public Mono<Object> persist(final OneToOne annotation, final Field field) {
    return Mono.just(this.entity)
      .mapNotNull(this::idValueOf)
      .flatMap(entityId -> {
        final var fieldType = this.domainFor(field.getType());
        final var entityType = this.domainFor(this.entity.getClass());
        final var entityName = entityType.getSimpleName();
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
          .flatMap(this::save)
          .switchIfEmpty(
            this.template
              .delete(fieldType)
              .matching(query(where(mappedBy).is(entityId)))
              .all()
              .then(Mono.empty())
          );
      });
  }

  private Mono<Object> breakOnCycle(final Object entityId) {
    return Mono.deferContextual(ctx -> {
      final var store = ctx.<List<Object>>getOrDefault(OneToOne.class, List.of());

      return Flux.fromIterable(store)
        .filter(entityId::equals)
        .collectList()
        .filter(List::isEmpty)
        .map(x -> entityId);
    });
  }

  private Function<Context, Context> storeOf(final Object entityId) {
    return ctx -> {
      final var store = ctx.getOrDefault(OneToOne.class, List.<Object>of());
      final var next = Stream.concat(store.stream(), Stream.of(entityId)).toList();

      return ctx.put(OneToOne.class, next);
    };
  }
}
