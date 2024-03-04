package io.github.joselion.springr2dbcrelationships.annotations;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.annotation.Transient;

/**
 * Marks a field to have a one-to-one relationship.
 *
 * <p>This annotation also adds the {@link Transient @Transient} and
 * {@link Value @Value("null")} annotations to the field.
 */
@Transient
@Documented
@Value("null") // NOSONAR
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
public @interface OneToOne {

  /**
   * Whether the orphan entity is preserved or not. Defaults to {@code false}.
   *
   * <p>Usually, one-to-one relationships have a parent-child configuration,
   * meaning the child needs to have the parent assigned to it. By default, the
   * annotation will delete the associated entity when it becomes an orphan or
   * the child is no longer assigned to the parent. You can prevent this
   * behavior by setting this option to {@code true}, in which case the
   * annotation will only remove the link of the orphan entity with the parent.
   *
   * @return {@code true} if orphan entities should be presereved, {@code false}
   *         otherwise
   */
  boolean keepOrphan() default false;

  /**
   * Specifies the name of the "foreign key" column in the associated table.
   * This is optional because the annotation can infer the "foreign key" column
   * name in two different ways:
   *
   * <p>1. The column's name matches the parent table's name followed by an
   * {@code _id} suffix.
   *
   * <p>2. The column's name matches the name of the annotated field followed
   * by an {@code _id} suffix.
   * 
   * <p>For example, given a parent table {@code phone}, a child table {@code details},
   * and the annotated field {@code @OneToOne Details details;}. The
   * "foreign key" column of the {@code details} table will be inferred
   * as either {@code phone_id} using option (1) or as {@code details_id} using
   * option (2).
   *
   * @return the "foreign key" column name in the associated table
   */
  String mappedBy() default "";

  /**
   * Whether the associated entity are read-only or not, meaning it's never
   * persisted or linked. Defaults to {@code false}.
   *
   * @return {@code true} if the associated entity is read-only, {@code false}
   *         otherwise
   */
  boolean readonly() default false;
}
