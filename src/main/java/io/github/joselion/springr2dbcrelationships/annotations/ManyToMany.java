package io.github.joselion.springr2dbcrelationships.annotations;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Auditable;
import org.springframework.data.domain.Sort.Direction;

/**
 * Marks a field to have a many-to-many relationship.
 *
 * <p>This annotation also adds the {@link Transient @Transient} and
 * {@link Value @Value("null")} annotations to the field.
 */
@Transient
@Documented
@Value("null") // NOSONAR
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
public @interface ManyToMany {

  /**
   * Whether "orphan" entities should be deleted or not. Defaults to {@code false}.
   * 
   * <p>Usually, many-to-many relationships are not mutually exclusive to each
   * other, meaning that one can exist without the other even when they are not
   * linked in their join table. In this context, "orphans" refers to all
   * entities no longer linked to the current entity. By default, the
   * annotation will only delete the links to the "orphans" entities in the
   * join table. Setting this option to {@code true} will also delete the
   * "orphan" entities.
   *
   * @return {@code true} if "orphan" entities should also be deleted, {@code false}
   *         otherwise
   * @apiNote given the nature of many-to-many relationships, setting this
   *          option to {@code true} is highly discouraged as it can produce
   *          unexpected results, especially in bidirectional associations
   */
  boolean deleteOrphans() default false;

  /**
   * Used to specify the name of the join table responsible for the
   * many-to-many relationship between two tables. This is usually optional if
   * the name of the join table matches the names of both related tables joined
   * by an underscore (in any order).
   *
   * <p>For example, given a table {@code author} and a table {@code book}, the
   * default join table for the relationship can be either {@code author_book}
   * or {@code book_author}.
   *
   * @return the name of the relationship join table
   */
  String joinTable() default "";

  /**
   * Used to specify the name of the "foreign key" column that maps the join
   * table with the linked table. This is usually optional if the column's name
   * matches the linked table name followed by an {@code _id} suffix.
   *
   * <p>For example, given a table {@code author} and a table {@code book}, and
   * given the annotation is used in a field of {@code author}'s entity, we can
   * say the linked table is {@code book} and its "foreign key" column in the
   * join table will be {@code book_id} by default.
   *
   * @return the name of the column linking the join table
   */
  String linkedBy() default "";

  /**
   * Whether the associated entities are only linked to the join table or not.
   * Defaults to {@code false}.
   *
   * <p>Link-only means the associated entities already exist. The annotation
   * will only create the link in the join table column when required. The
   * associated entities are never updated.
   *
   * @return {@code true} if the associated entities are link-only, {@code false}
   *         otherwise
   */
  boolean linkOnly() default false;

  /**
   * Used to specify the name of the "foreign key" column that maps the
   * annotated field's entity with the join table. This is usually optional if
   * the column's name matches the entity's table name followed by an {@code _id}
   * suffix.
   *
   * <p>For example, given a table {@code author} and a table {@code book}, and
   * given the annotation is used in a field of {@code author}'s entity, the
   * "foreign key" column in the join table will be {@code author_id} by default.
   *
   * @return the name of the column mapping the join table
   */
  String mappedBy() default "";

  /**
   * Whether the associated entities are read-only or not, meaning they are
   * never persisted or linked. Defaults to {@code false}.
   *
   * @return {@code true} if the associated entities are read-only, {@code false}
   *         otherwise
   */
  boolean readonly() default false;

  /**
   * The column used to sort the populated children entities. When not
   * specified, the annotation tries to find the field associated to
   * {@link Auditable#getCreatedDate()} or annotated with {@link CreatedDate}.
   * If none can be resolved, falls back to {@code "created_at"} by default.
   * 
   * <p>If all of the above fails, the children will be unsorted.
   *
   * @return the sorting column name
   */
  String sortBy() default "";

  /**
   * The direction to sort the populated children entities. Defaults to
   * {@link Direction#DESC ascending} direction.
   *
   * @return the sort direction
   */
  Direction sortIn() default Direction.DESC;
}
