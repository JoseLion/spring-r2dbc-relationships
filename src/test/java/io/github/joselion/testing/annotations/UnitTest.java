package io.github.joselion.testing.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;

@Target(TYPE)
@Retention(RUNTIME)
@DisplayNameGeneration(ReplaceUnderscores.class)
public @interface UnitTest {

}
