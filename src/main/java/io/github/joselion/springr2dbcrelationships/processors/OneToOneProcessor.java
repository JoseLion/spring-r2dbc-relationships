package io.github.joselion.springr2dbcrelationships.processors;

import static java.util.function.Predicate.not;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;
import static org.springframework.data.relational.core.query.Update.update;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
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
    final var fieldProjection = field.getType();
    final var fieldType = this.domainFor(fieldProjection);
    final var mappedBy = this.inferMappedBy(annotation, field);
    final var isBackReference = this.isBackReference(annotation, field);

    if (isBackReference) {
      final var parentId = this.idColumnOf(fieldType);
      final var mappedField = Commons.toCamelCase(mappedBy);

      return Mono.just(this.entity)
        .mapNotNull(Reflect.getter(mappedField))
        .flatMap(this::breakingCycles)
        .flatMap(fkValue ->
          this.template
            .select(fieldType)
            .as(fieldProjection)
            .matching(query(where(parentId).is(fkValue)))
            .one()
            .contextWrite(this.storeWith(fkValue))
        );
    }

    return Mono.just(this.entity)
      .mapNotNull(this::idValueOf)
      .flatMap(this::breakingCycles)
      .flatMap(entityId ->
        this.template
          .select(fieldType)
          .as(fieldProjection)
          .matching(query(where(mappedBy).is(entityId)))
          .one()
          .contextWrite(this.storeWith(entityId))
      );
  }

  @Override
  public Mono<Object> persist(final OneToOne annotation, final Field field) {
    final var fieldProjection = field.getType();
    final var fieldType = this.domainFor(fieldProjection);
    final var mappedBy = this.inferMappedBy(annotation, field);
    final var mappedField = Commons.toCamelCase(mappedBy);
    final var isBackReference = this.isBackReference(annotation, field);

    if (isBackReference) {
      final var mappedId = Reflect.getter(this.entity, mappedField);

      return Mono.just(this.entity)
        .mapNotNull(Reflect.getter(field))
        .flatMap(this::breakingCycles)
        .flatMap(this::save)
        .flatMap(saved -> {
          final var savedId = this.idValueOf(saved);

          return Mono.just(this.entity)
            .map(Reflect.update(mappedField, savedId))
            .map(Reflect.update(field, saved))
            .contextWrite(this.storeWith(savedId));
        })
        .switchIfEmpty(
          Mono.just(this.entity)
            .map(Reflect.update(mappedField, null))
            .flatMap(this::breakingCycles)
            .delayUntil(x -> {
              if (!annotation.keepOrphan() && mappedId != null) {
                final var parentId = this.idColumnOf(fieldType);

                return this.template
                  .delete(fieldType)
                  .matching(query(where(parentId).is(mappedId)))
                  .all();
              }

              return Mono.empty();
            })
        )
        .defaultIfEmpty(this.entity)
        .contextWrite(this.storeWith(mappedId));
    }

    return Mono.just(this.entity)
      .mapNotNull(this::idValueOf)
      .flatMap(this::breakingCycles)
      .flatMap(entityId ->
        Mono.just(this.entity)
          .mapNotNull(Reflect.getter(field))
          .map(Reflect.update(mappedField, entityId))
          .flatMap(this::save)
          .switchIfEmpty(
            Mono.just(annotation)
              .filter(OneToOne::keepOrphan)
              .flatMap(x ->
                this.template
                  .update(fieldType)
                  .matching(query(where(mappedBy).is(entityId)))
                  .apply(update(mappedBy, null))
              )
              .switchIfEmpty(
                this.template
                  .delete(fieldType)
                  .matching(query(where(mappedBy).is(entityId)))
                  .all()
              )
              .then(Mono.empty())
          )
          .contextWrite(this.storeWith(entityId))
      );
  }

  /**
   * Whether the annotated field is in the backreference side of the
   * relationship or not.
   *
   * @param annotation the one-to-one annotaion instance
   * @param field the annotated field
   * @return {@code true} if the field is a backreference, {@code false}
   *         otherwise
   */
  public boolean isBackReference(final OneToOne annotation, final Field field) {
    final var fieldType = this.domainFor(field.getType());
    final var byTable = this.tableNameOf(fieldType).concat("_id");
    final var byField = Commons.toSnakeCase(field.getName()).concat("_id");

    return Optional.of(annotation)
      .map(OneToOne::mappedBy)
      .filter(not(String::isBlank))
      .map(Commons::toCamelCase)
      .flatMap(this::inferForeignField)
      .or(() -> this.inferForeignField(byTable))
      .or(() -> this.inferForeignField(byField))
      .isPresent();
  }

  /**
   * Curried version of {@link #isBackReference(OneToOne, Field)} method.
   *
   * @param field field the annotated field
   * @return {@code true} if the field is a backreference, {@code false}
   *         otherwise
   */
  public Predicate<OneToOne> isBackReference(final Field field) {
    return annotation -> this.isBackReference(annotation, field);
  }

  private String inferMappedBy(final OneToOne annotation, final Field field) {
    final var entityType = this.domainFor(this.entity.getClass());
    final var fieldType = this.domainFor(field.getType());
    final var isBackreference = this.isBackReference(annotation, field);
    final var inferredType = isBackreference ? entityType : fieldType;
    final var byTable = isBackreference
      ? this.tableNameOf(fieldType).concat("_id")
      : this.table.getReference().concat("_id");
    final var byField = Commons.toSnakeCase(field.getName()).concat("_id");

    return Optional.of(annotation)
      .map(OneToOne::mappedBy)
      .filter(not(String::isBlank))
      .map(Commons::toCamelCase)
      .flatMap(this::inferForeignField)
      .or(() -> this.inferForeignField(byTable, inferredType))
      .or(() -> this.inferForeignField(byField, inferredType))
      .map(this::columnNameOf)
      .orElseThrow(() -> {
        final var message = """
          Unable to infer foreign key for "%s" entity. Neither "%s" nor "%s"
          associated fields could be found
          """
          .formatted(inferredType.getSimpleName(), byTable, byField);
        return RelationshipException.of(message);
      });
  }

  private Mono<Object> breakingCycles(final Object entityId) {
    return Mono.deferContextual(ctx -> {
      final var store = ctx.<List<Object>>getOrDefault(OneToOne.class, List.of());
      final var distinct = store.stream().distinct().toList();

      if (store.size() != distinct.size()) {
        return Mono.empty();
      }

      return Flux.fromIterable(store)
        .filter(entityId::equals)
        .collectList()
        .filter(List::isEmpty)
        .map(x -> entityId);
    });
  }

  private Function<Context, Context> storeWith(final @Nullable Object entityId) {
    return ctx -> {
      if (entityId != null) {
        final var store = ctx.getOrDefault(OneToOne.class, List.<Object>of());
        final var next = Stream.concat(store.stream(), Stream.of(entityId)).toList();

        return ctx.put(OneToOne.class, next);
      }

      return ctx;
    };
  }
}
