package io.github.joselion.springr2dbcrelationships;

import static java.util.Arrays.stream;
import static java.util.function.Predicate.not;
import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Auditable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.mapping.OutboundRow;
import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import org.springframework.data.r2dbc.mapping.event.AfterSaveCallback;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Component;

import io.github.joselion.maybe.Maybe;
import io.github.joselion.springr2dbcrelationships.annotations.OneToMany;
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
        Mono
          .just(entityField)
          .zipWhen(field -> {
            if (field.isAnnotationPresent(OneToOne.class)) {
              return this.populateOneToOne(entity, field, table);
            }

            if (field.isAnnotationPresent(OneToMany.class)) {
              return this.populateOneToMany(entity, field, table);
            }

            return Mono.empty();
          })
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
        Mono
          .just(entityField)
          .zipWhen(field -> {
            final var hasOneToOne = Optional.of(OneToOne.class)
              .filter(field::isAnnotationPresent)
              .map(field::getAnnotation)
              .filter(not(OneToOne::readonly))
              .filter(not(OneToOne::backReference))
              .isPresent();
            final var hasOneToMany = Optional.of(OneToMany.class)
              .filter(field::isAnnotationPresent)
              .map(field::getAnnotation)
              .filter(not(OneToMany::readonly))
              .isPresent();

            if (hasOneToOne) {
              return this.persistOneToOne(entity, field, table);
            }

            if (hasOneToMany) {
              return this.persistOneToMany(entity, entityField, table);
            }

            return Mono.empty();
          })
      )
      .sequential()
      .reduce(entity, (acc, tuple) -> {
        final var field = tuple.getT1();
        final var value = tuple.getT2();

        return Reflect.update(acc, field, value);
      })
      .defaultIfEmpty(entity);
  }

  private Mono<?> populateOneToOne(final T entity, final Field field, final SqlIdentifier table) {
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
        );
    }

    return Mono.just(entity)
      .mapNotNull(this::getIdOf)
      .flatMap(entityId ->
        this.template
          .select(this.domainFor(fieldType))
          .as(fieldType)
          .matching(query(where(mappedBy).is(entityId)))
          .one()
      );
  }

  private Mono<? extends List<?>> populateOneToMany(final T entity, final Field field, final SqlIdentifier table) {
    final var innerType = Reflect.innerTypeOf(field);
    final var mappedBy = Optional.of(OneToMany.class)
      .map(field::getAnnotation)
      .map(OneToMany::mappedBy)
      .filter(not(String::isBlank))
      .orElseGet(() -> table.getReference().concat("_id"));
    final var sortByOrEmpty = Optional.of(OneToMany.class)
      .map(field::getAnnotation)
      .map(OneToMany::sortBy)
      .filter(not(String::isBlank))
      .or(() -> this.createdAtField(entity).map(this::columnNameOf))
      .or(() ->
        Maybe.of("createdAt")
          .solve(innerType::getDeclaredField)
          .map(this::columnNameOf)
          .toOptional()
      );
    final var sortIn = Optional.of(OneToMany.class)
      .map(field::getAnnotation)
      .map(OneToMany::sortIn)
      .orElse(Direction.ASC);
    final var byColumn = sortByOrEmpty
      .map(sortBy -> Sort.by(sortIn, sortBy))
      .orElseGet(Sort::unsorted);

    return Mono.just(entity)
      .mapNotNull(this::getIdOf)
      .flatMap(entityId ->
        this.template
          .select(this.domainFor(innerType))
          .as(innerType)
          .matching(query(where(mappedBy).is(entityId)).sort(byColumn))
          .all()
          .collectList()
      );
  }

  private Mono<?> persistOneToOne(final T entity, final Field field, final SqlIdentifier table) {
    return Mono.just(entity)
      .mapNotNull(this::getIdOf)
      .flatMap(entityId -> {
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
          .switchIfEmpty(
            this.template
              .delete(this.domainFor(fieldType))
              .matching(query(where(mappedBy).is(entityId)))
              .all()
              .then(Mono.empty())
          );
      });
  }

  private Mono<? extends List<?>> persistOneToMany(final T entity, final Field field, final SqlIdentifier table) {
    return Mono.just(entity)
      .mapNotNull(this::getIdOf)
      .flatMap(entityId -> {
        final var innerType = Reflect.innerTypeOf(field);
        final var mappedBy = Optional.of(OneToMany.class)
          .map(field::getAnnotation)
          .map(OneToMany::mappedBy)
          .filter(not(String::isBlank))
          .orElseGet(() -> table.getReference().concat("_id"));
        final var mappedField = Maybe.of(mappedBy)
          .map(Commons::toCamelCase)
          .solve(innerType::getDeclaredField)
          .orThrow(RelationshipException::of);
        final var values = Optional.of(entity)
          .map(Reflect.<List<?>>getter(field))
          .orElseGet(List::of);

        return Flux.fromIterable(values)
          .map(x -> Reflect.update(x, mappedField, entityId))
          .flatMap(this::upsert)
          .collectList()
          .delayUntil(children -> {
            final var innerId = this.findIdColumn(innerType);
            final var ids = children.stream()
              .map(this::getIdOf)
              .toList();

            return this.template
              .delete(innerType)
              .matching(query(where(mappedBy).is(entityId).and(innerId).notIn(ids)))
              .all();
          });
      });
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

  @Nullable
  private Object getIdOf(final Object target) {
    final var mapper = this.template.getConverter().getMappingContext();

    return Optional.of(target)
      .map(Object::getClass)
      .map(mapper::getRequiredPersistentEntity)
      .map(PersistentEntity::getIdProperty)
      .map(RelationalPersistentProperty::getField)
      .map(field -> Reflect.getter(target, field))
      .orElse(null);
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

  private Optional<Field> createdAtField(final Object target) {
    final var targetType = target.getClass();
    final var fields = targetType.getDeclaredFields();

    if (target instanceof Auditable) {
      return Maybe.of("createdDate")
        .solve(targetType::getDeclaredField)
        .toOptional();
    }

    return stream(fields)
      .filter(field -> field.isAnnotationPresent(CreatedDate.class))
      .findFirst();
  }

  private String columnNameOf(final Field field) {
    final var fieldName = field.getName();
    final var targetType = field.getDeclaringClass();

    return this.template
      .getConverter()
      .getMappingContext()
      .getRequiredPersistentEntity(targetType)
      .getRequiredPersistentProperty(fieldName)
      .getColumnName()
      .getReference();
  }
}
