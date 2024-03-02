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
 * Marks a field to have a one-to-many relationship.
 *
 * <p>This annotation also adds the {@link Transient @Transient} and
 * {@link Value @Value("null")} annotations to the field.
 */
@Transient
@Documented
@Value("null") // NOSONAR
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
public @interface OneToMany {

  /**
   * Whether orphan entities are preserved or not. Defaults to {@code false}.
   *
   * <p>Usually, one-to-many relationships have a parent-children configuration,
   * meaning every child needs a parent assigned to it. By default, the
   * annotation will delete orphan entites, or children which are no longer
   * assigned to their parent. You can prevent this behavior by setting this
   * option to {@code true}, in which case the annotation will only remove the
   * link of the orphan entities with the parent.
   *
   * @return {@code true} if orphan entities should be presereved, {@code false}
   *         otherwise
   */
  boolean keepOrphans() default false;

  /**
   * Whether children entities are only linked to the parent or not. Defaults to
   * {@code false}.
   *
   * <p>Link-only means the children entities should already exist. The
   * annotation will only update the "foreign key" link on each entity when
   * required. Other values in the children entities are never updated.
   *
   * @return {@code true} if children entities are only linked, {@code false}
   *         otherwise
   */
  boolean linkOnly() default false;

  /**
   * Specifies the name of the "foreign key" column on the child table. This is
   * optional if the column's name matches the parent's table name followed by
   * an {@code _id} suffix.
   *
   * <p>For example, given a parent table {@code country} and a child table
   * {@code city}, the "foreign key" column of the {@code city} table will be
   * inferred as {@code country_id}.
   *
   * @return the "foreign key" column name in the child table
   */
  String mappedBy() default "";

  /**
   * Whether the children entities are read-only or not, meaning they are never
   * persisted or linked to the parent. Defaults to {@code false}.
   *
   * @return {@code true} if the children entities are read-only, {@code false}
   *         otherwise
   */
  boolean readonly() default false;

  /**
   * Specifies the column used to sort the populated children entities.
   * 
   * <p>By default, the annotation tries to find the field associated with
   * {@link Auditable#getCreatedDate()} or annotated with {@link CreatedDate @CreatedDate}.
   * If none can be found, it'll try to find a {@code "created_at"} column as a
   * last resort. If all of that fails, the children will be unsorted.
   *
   * @return the sorting column name
   */
  String sortBy() default "";

  /**
   * Specifies in which direction the populated children entities are sorted.
   * Defaults to {@link Direction#DESC}.
   *
   * @return the sort direction
   */
  Direction sortIn() default Direction.DESC;
}
