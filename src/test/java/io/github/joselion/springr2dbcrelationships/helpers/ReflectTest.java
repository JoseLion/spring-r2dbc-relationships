package io.github.joselion.springr2dbcrelationships.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import io.github.joselion.springr2dbcrelationships.exceptions.ReflectException;
import io.github.joselion.testing.annotations.UnitTest;
import lombok.AllArgsConstructor;
import lombok.With;

@UnitTest class ReflectTest {

  @Nested class getter {
    @Nested class when_the_field_is_provided {
      @Nested class and_the_field_getter_exists {
        @TestFactory Stream<DynamicTest> invokes_the_getter_and_returns_the_value() {
          final var targetRecord = TestRecord.of("Hello world!");
          final var targetClass = TestPojo.of("Hello world!");

          return Stream
            .of(targetRecord, targetClass)
            .map(target -> dynamicTest(target.getClass().getSimpleName(), () -> {
              final var field = target.getClass().getDeclaredField("foo");
              final var result = Reflect.<String>getter(target, field);

              assertThat(result)
                .isInstanceOf(String.class)
                .isEqualTo("Hello world!");
            }));
        }
      }

      @Nested class and_the_field_getter_does_not_exist {
        @Test void throws_a_ReflectException() throws NoSuchFieldException, SecurityException {
          final var field = TestPojo.class.getDeclaredField("bar");
          final var target = TestPojo.of("hello");

          assertThatCode(() -> Reflect.getter(target, field))
            .isInstanceOf(ReflectException.class)
            .hasMessage("Unable to find getter for field: bar");
        }
      }
    }

    @Nested class when_the_field_name_is_provided {
      @Nested class and_the_field_name_exists {
        @Test void calls_the_field_based_overload() throws NoSuchFieldException, SecurityException {
          try (var mocked = mockStatic(Reflect.class, CALLS_REAL_METHODS)) {
            final var field = TestRecord.class.getDeclaredField("foo");
            final var target = TestRecord.of("Hello world!");
            final var foo = Reflect.<String>getter(target, "foo");

            assertThat(foo).isEqualTo("Hello world!");

            mocked.verify(() -> Reflect.getter(target, field));
          }
        }
      }

      @Nested class and_the_field_name_does_not_exist {
        @Test void throws_a_ReflectException() {
          final var target = TestRecord.of("Hello world!");

          assertThatCode(() -> Reflect.getter(target, "other"))
            .isInstanceOf(ReflectException.class)
            .hasMessage("java.lang.NoSuchFieldException: other");
        }
      }
    }

    @Nested class when_the_method_is_provided {
      @Nested class and_the_field_name_exists {
        @Test void calls_the_field_name_based_overload() throws ReflectiveOperationException, SecurityException {
          try (var mocked = mockStatic(Reflect.class, CALLS_REAL_METHODS)) {
            final var method = TestPojo.class.getDeclaredMethod("getFoo");
            final var target = TestPojo.of("Hello world!");
            final var foo = Reflect.<String>getter(target, method);

            assertThat(foo).isEqualTo("Hello world!");

            mocked.verify(() -> Reflect.getter(target, "foo"));
          }
        }
      }

      @Nested class and_the_method_does_not_exist {
        @Test void throws_a_ReflectException() throws NoSuchMethodException, SecurityException {
          final var target = TestPojo.of("Hello world!");
          final var method = TestPojo.class.getDeclaredMethod("retrieveBar");

          assertThatCode(() -> Reflect.getter(target, method))
            .isInstanceOf(ReflectException.class)
            .hasMessage("java.lang.NoSuchFieldException: retrieveBar");
        }
      }
    }
  }

  @Nested class update {
    @Nested class when_the_field_is_provided {
      @Nested class and_the_field_updater_exists {
        @TestFactory Stream<DynamicTest> updates_the_field_in_the_target() {
          final var targetRecord = TestRecord.of("Hello!");
          final var targetClass = TestPojo.of("Hello!");

          return Stream
            .of(targetRecord, targetClass)
            .map(target -> dynamicTest(target.getClass().getSimpleName(), () -> {
              final var field = target.getClass().getDeclaredField("foo");
              final var updated = Reflect.update(target, field, "Good bye!");

              assertThat(updated)
                .extracting("foo")
                .isEqualTo("Good bye!");
            }));
        }
      }

      @Nested class and_the_field_updater_does_not_exist {
        @Test void throws_a_ReflectException() throws NoSuchFieldException, SecurityException {
          final var field = TestPojo.class.getDeclaredField("bar");
          final var target = TestPojo.of("Hello!");

          assertThatCode(() -> Reflect.update(target, field, true))
            .isInstanceOf(ReflectException.class)
            .hasMessage("Unable to find wither/setter for field: bar");
        }
      }
    }

    @Nested class when_the_field_name_is_provided {
      @Nested class and_the_field_name_exists {
        @Test void calls_the_field_based_overload() throws NoSuchFieldException, SecurityException {
          try (var mocked = mockStatic(Reflect.class, CALLS_REAL_METHODS)) {
            final var field = TestRecord.class.getDeclaredField("foo");
            final var target = TestRecord.of("Hello world!");
            final var updated = Reflect.update(target, "foo", "Good bye!");

            assertThat(updated.foo()).isEqualTo("Good bye!");

            mocked.verify(() -> Reflect.update(target, field, "Good bye!"));
          }
        }
      }

      @Nested class and_the_field_name_does_not_exist {
        @Test void throws_a_ReflectException() {
          final var target = TestRecord.of("Hello!");

          assertThatCode(() -> Reflect.update(target, "other", "Bye!"))
            .isInstanceOf(ReflectException.class)
            .hasMessage("java.lang.NoSuchFieldException: other");
        }
      }
    }
  }

  @Nested class innerTypeOf {
    @Nested class when_the_field_type_has_only_one_generic_inner_type {
      @Test void returns_the_inner_type_of_the_field_type() throws NoSuchFieldException, SecurityException {
        final var field = TestPojo.class.getDeclaredField("textList");
        final var innerType = Reflect.<String>innerTypeOf(field);

        assertThat(innerType).isEqualTo(String.class);
      }
    }

    @Nested class when_the_field_type_has_more_than_one_generic_inner_type {
      @Test void returns_the_first_inner_type_of_the_field_type() throws NoSuchFieldException, SecurityException {
        final var field = TestPojo.class.getDeclaredField("props");
        final var innerType = Reflect.<String>innerTypeOf(field);

        assertThat(innerType).isEqualTo(String.class);
      }
    }

    @Nested class when_the_field_type_has_no_generic_inner_types {
      @Test void throws_a_ReflectException() throws NoSuchFieldException, SecurityException {
        final var field = TestPojo.class.getDeclaredField("foo");

        assertThatCode(() -> Reflect.innerTypeOf(field))
          .isInstanceOf(ReflectException.class)
          .hasMessage("The type of 'foo' field has no parameterized types");
      }
    }
  }

  @With
  record TestRecord(String foo) {

    public static TestRecord of(final String foo) {
      return new TestRecord(foo);
    }
  }

  @AllArgsConstructor
  static final class TestPojo {

    private String foo;

    private final boolean bar = false;

    private final List<String> textList = List.of();

    private final Map<String, Object> props = Map.of();

    public static TestPojo of(final String foo) {
      return new TestPojo(foo);
    }

    public String getFoo() {
      return this.foo;
    }

    public void setFoo(final String foo) {
      this.foo = foo;
    }

    public boolean retrieveBar() {
      return this.bar;
    }

    public List<String> getTextList() {
      return this.textList;
    }

    public Map<String, Object> getProps() {
      return this.props;
    }
  }
}
