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
 * Marks a field to have a many-to-one relationship.
 *
 * <p>This annotation also adds the {@link Transient @Transient} and
 * {@link Value @Value("null")} annotations to the field.
 */
@Transient
@Documented
@Value("null") // NOSONAR
@Retention(RUNTIME)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
public @interface ManyToOne {

  /**
   * Specifies the name of the "foreign key" column in the current entity's
   * table. This is optional because the annotation can infer the "foreign key"
   * column name in two different ways:
   *
   * <p>1. The column's name matches the parent table's name followed by an
   * {@code _id} suffix.
   *
   * <p>2. The column's name matches the name of the annotated field followed
   * by an {@code _id} suffix.
   *
   * <p>For example, given a parent table {@code country}, a child table {@code city},
   * and the annotated field {@code @OneToMany Country originCountry;}. The
   * "foreign key" column of the {@code city} table will be inferred as either
   * {@code country_id} using option (1) or as {@code origin_country_id} using
   * option (2).
   *
   * @return the "foreign key" column name
   */
  String foreignKey() default "";

  /**
   * Whether the associated entity persists or not. Defaults to {@code false}.
   *
   * <p>Many-to-one relationships are a backreference of a one-to-many
   * relationship. They are usually expected to be link-only, meaning the
   * parent should exist to link the entity through their {@link #foreignKey},
   * and no changes are made to the parent entity. Setting this option to
   * {@code true} creates/updates the parent before linking the entity.
   *
   * @return {@code true} if the associated entity persists, {@code false}
   *         otherwise
   */
  boolean persist() default false;
}
