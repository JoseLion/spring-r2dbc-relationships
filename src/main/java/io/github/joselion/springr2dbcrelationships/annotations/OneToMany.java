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
   * Used to specify the name of the "foreign key" column on the child table.
   * This is usually optional if the name of the column matches the name of the
   * parent table followed by an {@code _id} suffix.
   *
   * <p>For example, given the parent table is {@code country} and the child
   * table is {@code city}. By default, the annotation will use {@code country_id}
   * as the "foreign key" column of the {@code city} table.
   *
   * @return the name of the "foreign key" column in the child table
   */
  String mappedBy() default "";

  /**
   * Whether the entities on the annotated field are readonly or not. I.e., the
   * children entities are never persisted. Defaults to {@code false}.
   *
   * @return {@code true} if the children entities should be readonly, {@code false}
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
