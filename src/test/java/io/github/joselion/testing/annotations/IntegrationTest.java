package io.github.joselion.testing.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;

import io.github.joselion.springr2dbcrelationships.FixtureApplication;

@UnitTest
@Inherited
@Target(TYPE)
@Retention(RUNTIME)
@SpringBootTest(classes = FixtureApplication.class)
public @interface IntegrationTest {

}
