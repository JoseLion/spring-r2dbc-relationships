package io.github.joselion.springr2dbcrelationships.processors;

import static java.util.Arrays.stream;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Auditable;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import io.github.joselion.maybe.Maybe;
import io.github.joselion.springr2dbcrelationships.annotations.ProjectionOf;
import io.github.joselion.springr2dbcrelationships.helpers.Commons;
import io.github.joselion.springr2dbcrelationships.helpers.Reflect;
import reactor.core.publisher.Mono;

/**
 * Describes how a relationship annotation is processed.
 *
 * @param <T> the type of the processed annotation
 * @param <U> the type of value populated or persisted
 */
public interface Processable<T extends Annotation, U> {

  /**
   * Returns a {@link Mono} containing the value used to populate a field
   * annotated with a relationship annotation.
   *
   * @param annotation the field's annotation instance
   * @param field the field to be populated
   * @return a publisher containing the value to populate
   */
  Mono<U> populate(T annotation, Field field);

  /**
   * Curried version of {@link #populate(Annotation, Field)} method.
   *
   * @param field field the field to be populated
   * @return a function taht takes the annotation {@code T} and returns a
   *         publisher containing the value to populate
   */
  default Function<T, Mono<U>> populate(final Field field) {
    return annotation -> this.populate(annotation, field);
  }

  /**
   * Persists the value of a field annotated with a relationship annotation.
   * Then returns a {@link Mono} containing the result of the operation.
   *
   * @param annotation the field's annotation instance
   * @param field the field to be persisted
   * @return a publisher containing the value os the persist operation
   */
  Mono<U> persist(T annotation, Field field);

  /**
   * Curried version of {@link #persist(Annotation, Field)} method.
   *
   * @param field the field to be persisted
   * @return a function that takes the annotation {@code T} and returns a
   *         publisher containing the value os the persist operation
   */
  default Function<T, Mono<U>> persist(Field field) {
    return annotation -> this.persist(annotation, field);
  }

  /**
   * Returns the r2dbc entity template injected to the processor.
   *
   * @return the r2dbc entity template
   */
  R2dbcEntityTemplate template();

  /**
   * Returns the entity where the annotated field is being processed.
   *
   * @return the field's entity
   */
  Object entity();

  /**
   * Returns the SQL identifier of the table associated with the entity where
   * the annotated field is being processed.
   *
   * @return the entity's table SQL identifier
   */
  SqlIdentifier table();

  /**
   * Returns {@code true} if the entity is considered to be new, {@code false}
   * otherwise.
   *
   * @param entity the entity to check if it's new
   * @return true if the entity is new, false otherwise
   */
  default boolean isNew(final Object entity) {
    final var type = this.domainFor(entity.getClass());

    return this.template()
      .getConverter()
      .getMappingContext()
      .getRequiredPersistentEntity(type)
      .isNew(entity);
  }

  /**
   * Inserts an entity when it's new or updates it otherwise.
   *
   * @param <S> the entity type
   * @param entity the entity to insert/update
   * @return a publisher containing the inserted/updated entity
   */
  default <S> Mono<S> upsert(final S entity) {
    final var template = this.template();

    return this.isNew(entity)
      ? template.insert(entity)
      : template.update(entity);
  }

  /**
   * Returns the actual domain of an entity which may or may not be projected
   * by another type.
   *
   * @param <S> the type of {@code type}'s class
   * @param type the type to get the domain from
   * @return the actual domain type
   */
  default <S> Class<?> domainFor(final Class<S> type) {
    return Optional.of(ProjectionOf.class)
      .map(type::getAnnotation)
      .map(ProjectionOf::value)
      .map(Commons::<Class<S>>cast)
      .orElse(type);
  }

  /**
   * Returns the table {@link SqlIdentifier} of an entity type.
   *
   * @param type the type of the entity
   * @return the table identifier
   */
  default SqlIdentifier tableIdentifierOf(final Class<?> type) {
    return this.template()
      .getConverter()
      .getMappingContext()
      .getRequiredPersistentEntity(type)
      .getTableName();
  }

  /**
   * Returns the table name of an entity type.
   *
   * @param type the type of the entity
   * @return the table name
   */
  default String tableNameOf(final Class<?> type) {
    return this
      .tableIdentifierOf(type)
      .getReference();
  }

  /**
   * Returns the primary key column name of an entity type.
   *
   * @param type the entity type
   * @return the primary key column name
   */
  default String idColumnOf(final Class<?> type) {
    return this.template()
      .getConverter()
      .getMappingContext()
      .getRequiredPersistentEntity(type)
      .getIdColumn()
      .getReference();
  }

  /**
   * Returns the primary key value of an entity if exists, otherwise {@code null}.
   *
   * @param target the target entity
   * @return the {@code id} value of the target
   */
  @Nullable
  default Object idValueOf(final Object target) {
    final var mapper = this.template().getConverter().getMappingContext();

    return Optional.of(target)
      .map(Object::getClass)
      .map(mapper::getRequiredPersistentEntity)
      .map(PersistentEntity::getIdProperty)
      .map(RelationalPersistentProperty::getField)
      .map(field -> Reflect.getter(target, field))
      .orElse(null);
  }

  /**
   * Returns an {@link Optional} containing the auditable column used to mark
   * the date of creation or empty.
   *
   * @param type the target's type to find the field
   * @return an optional with the creation date column or empty
   */
  default Optional<String> createdColumnOf(final Class<?> type) {
    final var fields = type.getDeclaredFields();

    if (Auditable.class.isAssignableFrom(type)) {
      return Maybe.of("createdDate")
        .solve(type::getDeclaredField)
        .toOptional()
        .map(this::columnNameOf);
    }

    return stream(fields)
      .filter(field -> field.isAnnotationPresent(CreatedDate.class))
      .findFirst()
      .or(() ->
        Maybe.of("createdAt")
          .solve(type::getDeclaredField)
          .toOptional()
      )
      .map(this::columnNameOf);
  }

  /**
   * Returns an {@link Optional} containing the auditable column used to mark
   * the date of creation or empty.
   *
   * @param <S> the type of the target
   * @param target the target to find the field
   * @return an optional with the creation date column or empty
   */
  default <S> Optional<String> createdColumnOf(final S target) {
    final var targetType = target.getClass();

    return this.createdColumnOf(targetType);
  }

  /**
   * Returns the column name representation of a field.
   *
   * @param field the field that maps the column
   * @return the column name of the field
   */
  default String columnNameOf(final Field field) {
    final var fieldName = field.getName();
    final var targetType = field.getDeclaringClass();

    return this.template()
      .getConverter()
      .getMappingContext()
      .getRequiredPersistentEntity(targetType)
      .getRequiredPersistentProperty(fieldName)
      .getColumnName()
      .getReference();
  }

  /**
   * Returns the column name representation of a field or null if the
   * persistent property does not exist.
   *
   * @param field the field that maps the column
   * @return the column name of the field or null
   */
  @Nullable
  default String columnNameOrNull(final Field field) {
    final var fieldName = field.getName();
    final var targetType = field.getDeclaringClass();
    final var entity = this.template()
      .getConverter()
      .getMappingContext()
      .getRequiredPersistentEntity(targetType);

    return Optional.of(fieldName)
      .map(entity::getPersistentProperty)
      .map(RelationalPersistentProperty::getColumnName)
      .map(SqlIdentifier::getReference)
      .orElse(null);
  }

  /**
   * Tries to infer the foreign key field in the specified entity type.
   *
   * @param foreignKey the foreign key column name
   * @param entityType the entity type to find the field from
   * @return the foreign key field
   */
  default Optional<Field> inferForeignField(final String foreignKey, final Class<?> entityType) {
    return Maybe.of(foreignKey)
      .map(Commons::toCamelCase)
      .solve(entityType::getDeclaredField)
      .toOptional();
  }

  /**
   * Tries to infer the foreign key field in the current entity type.
   *
   * @param foreignKey the foreign key column name
   * @return the foreign key field
   */
  default Optional<Field> inferForeignField(final String foreignKey) {
    final var entityType = this.domainFor(this.entity().getClass());

    return this.inferForeignField(foreignKey, entityType);
  }
}
