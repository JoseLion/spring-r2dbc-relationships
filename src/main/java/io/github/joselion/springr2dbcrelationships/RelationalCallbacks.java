package io.github.joselion.springr2dbcrelationships;

import static java.util.function.Predicate.not;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.reactivestreams.Publisher;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.mapping.OutboundRow;
import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import org.springframework.data.r2dbc.mapping.event.AfterSaveCallback;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Component;

import io.github.joselion.springr2dbcrelationships.annotations.OneToOne;
import io.github.joselion.springr2dbcrelationships.annotations.ProjectionOf;
import io.github.joselion.springr2dbcrelationships.exceptions.RelationshipException;
import io.github.joselion.springr2dbcrelationships.helpers.Commons;
import io.github.joselion.springr2dbcrelationships.helpers.Reflect;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Spring component which registers callbacks for all entities to process
 * relationship annotations.
 *
 * @param <T> the entity marked as relational
 * @param context the Spring application context
 * @param template the R2DBC entity template
 */
@Component
public record RelationalCallbacks<T>(
  ApplicationContext context,
  @Lazy R2dbcEntityTemplate template
) implements AfterConvertCallback<T>, AfterSaveCallback<T> {

  private static final String STACK_KEY = "relationship_access_stack";

  @Override
  public Publisher<T> onAfterConvert(final T entity, final SqlIdentifier table) {
    return Mono.just(entity)
      .map(T::getClass)
      .map(Class::getDeclaredFields)
      .flatMapMany(Flux::fromArray)
      .parallel()
      .runOn(Schedulers.parallel())
      .flatMap(entityField ->
        Mono.just(entityField)
          .filter(field -> field.isAnnotationPresent(OneToOne.class))
          .zipWhen(field -> this.populateOneToOne(entity, field, table))
      )
      .sequential()
      .reduce(entity, (acc, tuple) -> {
        final var field = tuple.getT1();
        final var value = tuple.getT2();

        return Reflect.update(acc, field, value);
      })
      .defaultIfEmpty(entity)
      .contextWrite(ctx -> {
        final var typeName = entity.getClass().getName();
        final var next = ctx.<List<Class<?>>>getOrEmpty(STACK_KEY)
          .map(prev -> Stream.concat(prev.stream(), Stream.of(typeName)).toList())
          .orElse(List.of(typeName));
        return ctx.put(STACK_KEY, next);
      });
  }

  @Override
  public Publisher<T> onAfterSave(final T entity, final OutboundRow outboundRow, final SqlIdentifier table) {
    return Mono.just(entity)
      .map(T::getClass)
      .map(Class::getDeclaredFields)
      .flatMapIterable(List::of)
      .parallel()
      .runOn(Schedulers.parallel())
      .flatMap(entityField ->
        Mono.just(entityField)
          .filter(field -> field.isAnnotationPresent(OneToOne.class))
          .filter(field ->
            Optional.of(OneToOne.class)
              .map(field::getAnnotation)
              .filter(not(OneToOne::readonly))
              .filter(not(OneToOne::backReference))
              .isPresent()
          )
          .zipWhen(field -> this.persistOneToOne(entity, field, table))
      )
      .sequential()
      .reduce(entity, (acc, tuple) -> {
        final var field = tuple.getT1();
        final var value = tuple.getT2();

        return Reflect.update(acc, field, value);
      })
      .defaultIfEmpty(entity);
  }

  private <S> Mono<S> populateOneToOne(final T entity, final Field field, final SqlIdentifier table) {
    final var fieldType = field.getType();
    final var isBackReference = Optional.of(OneToOne.class)
      .map(field::getAnnotation)
      .filter(OneToOne::backReference)
      .isPresent();
    final var mappedBy = Optional.of(OneToOne.class)
      .map(field::getAnnotation)
      .map(OneToOne::mappedBy)
      .filter(not(String::isBlank))
      .orElseGet(() -> {
        final var prefix = isBackReference
          ? this.tableNameOf(fieldType)
          : table.getReference();

        return prefix.concat("_id");
      });

    if (isBackReference) {
      final var parentId = this.findIdColumn(fieldType);
      final var mappedField = Commons.toCamelCase(mappedBy);
      final var fkValue = Optional.of(entity)
        .map(Reflect.getter(mappedField))
        .orElseThrow(() -> {
          final var message = "Entity <%s> is missing foreign key in field: %s".formatted(
            entity.getClass().getName(),
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
        )
        .map(Commons::cast);
    }

    return this.idOrEmpty(entity)
      .map(entityId ->
        this.template
          .select(this.domainFor(fieldType))
          .as(fieldType)
          .matching(query(where(mappedBy).is(entityId)))
          .one()
          .map(Commons::<S>cast)
      )
      .orElseGet(Mono::empty);
  }

  private <S> Mono<S> persistOneToOne(final T entity, final Field field, final SqlIdentifier table) {
    return this.idOrEmpty(entity)
      .map(entityId -> {
        final var fieldType = field.getType();
        final var entityName = entity.getClass().getSimpleName();
        final var mappedBy = Optional.of(OneToOne.class)
          .map(field::getAnnotation)
          .map(OneToOne::mappedBy)
          .filter(not(String::isBlank))
          .orElseGet(() -> table.getReference().concat("_id"));
        final var fkFieldName = Commons.uncapitalize(entityName).concat("Id");
        final var initial = Reflect.getter(entity, field);
        final var value = Optional.ofNullable(initial)
          .map(Reflect.update(fkFieldName, entityId))
          .orElse(initial);

        return Mono.justOrEmpty(value)
          .flatMap(this::upsert)
          .map(Commons::<S>cast)
          .switchIfEmpty(
            this.template
              .delete(this.domainFor(fieldType))
              .matching(query(where(mappedBy).is(entityId)))
              .all()
              .then(Mono.<S>empty())
          );
      })
      .orElseGet(Mono::empty);
  }

  private <S> Mono<S> upsert(final S entity) {
    final var type = entity.getClass();
    final var isNew = this.template
      .getConverter()
      .getMappingContext()
      .getRequiredPersistentEntity(type)
      .isNew(entity);

    return isNew
      ? this.template.insert(entity)
      : this.template.update(entity);
  }

  private <S> Class<S> domainFor(final Class<S> type) {
    return Optional.of(ProjectionOf.class)
      .map(type::getAnnotation)
      .map(ProjectionOf::value)
      .map(Commons::<Class<S>>cast)
      .orElse(type);
  }

  private String findIdColumn(final Class<?> type) {
    return this.template
      .getConverter()
      .getMappingContext()
      .getRequiredPersistentEntity(type)
      .getIdColumn()
      .getReference();
  }

  private <S> Optional<S> idOrEmpty(final Object target) {
    final var mapper = this.template.getConverter().getMappingContext();

    return Optional.of(target)
      .map(Object::getClass)
      .map(mapper::getRequiredPersistentEntity)
      .map(PersistentEntity::getIdProperty)
      .map(RelationalPersistentProperty::getField)
      .map(field -> Reflect.<S>getter(target, field));
  }

  private String tableNameOf(final Class<?> type) {
    return this.template
      .getConverter()
      .getMappingContext()
      .getRequiredPersistentEntity(type)
      .getTableName()
      .getReference();
  }

  private Mono<Integer> checkCycles() {
    return Mono.deferContextual(ctx ->
      Mono.just(STACK_KEY)
        .map(ctx::<List<?>>get)
        .filter(stack -> stack.size() == stack.stream().distinct().count())
        .map(List::size)
    );
  }
}
