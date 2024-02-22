package io.github.joselion.springr2dbcrelationships.helpers;

import static java.lang.invoke.LambdaMetafactory.metafactory;
import static java.lang.invoke.MethodType.methodType;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.eclipse.jdt.annotation.Nullable;

import io.github.joselion.maybe.Maybe;
import io.github.joselion.springr2dbcrelationships.exceptions.ReflectException;

/**
 * Reflection helpers.
 */
public final class Reflect {

  private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

  private Reflect() {
    throw new UnsupportedOperationException("Reflect is a helper class");
  }

  /**
   * Finds and invokes the getter of a field in the provided target.
   *
   * @param <T> the return type of the getter
   * @param target the target instance were the getter is invoked
   * @param field the field to invoke the getter
   * @return the result of invoking the getter
   * @throws ReflectException if the getter method cannot be found or fails to
   *                          be invoked
   */
  @Nullable
  public static <T> T getter(final Object target, final Field field) {
    final var targetType = field.getDeclaringClass();
    final var fieldType = field.getType();
    final var getterMethod = Reflect.findGetterMethod(field);
    final var getterFn = Maybe
      .from(() -> metafactory(
        LOOKUP,
        "apply",
        methodType(Function.class),
        methodType(Object.class, Object.class),
        getterMethod,
        methodType(fieldType, targetType)
      ))
      .map(CallSite::getTarget)
      .solve(method -> method.invoke()) // NOSONAR
      .solve(Commons::<Function<Object, T>>cast)
      .orThrow(ReflectException::of);

    return getterFn.apply(target);
  }

  /**
   * Curried version of {@link Reflect#getter(Object, Field)} overload.
   *
   * @param <T> the return type of the getter
   * @param field the field to invoke the getter
   * @return a function that takes the target as parameter and returns the
   *         result of invoking the getter of the field
   */
  public static <T> Function<Object, @Nullable T> getter(final Field field) {
    return target -> Reflect.getter(target, field);
  }

  /**
   * Finds and invokes the getter of a field in the provided target.
   *
   * @param <T> the return type of the getter
   * @param target the target instance were the getter is invoked
   * @param fieldName the name of the field to invoke the getter
   * @return the result of invoking the getter
   * @throws ReflectException if the getter method cannot be found or fails to
   *                          be invoked
   */
  @Nullable
  public static <T> T getter(final Object target, final String fieldName) {
    final var field = Maybe.of(fieldName)
      .solve(target.getClass()::getDeclaredField)
      .orThrow(ReflectException::of);

    return getter(target, field);
  }

  /**
   * Curried version of {@link Reflect#getter(Object, String)} overload.
   *
   * @param <T> the return type of the getter
   * @param fieldName the name of the field to invoke the getter
   * @return a function that takes the target as parameter and returns the
   *         result of invoking the getter of the field
   */
  public static <T> Function<Object, @Nullable T> getter(final String fieldName) {
    return target -> Reflect.getter(target, fieldName);
  }

  /**
   * Finds and invokes the getter of a field in the provided target.
   *
   * @param <T> the return type of the getter
   * @param target the target instance were the getter is invoked
   * @param method the method of the getter to invoke
   * @return the result of invoking the getter
   * @throws ReflectException if the getter method cannot be found or fails to
   *                          be invoked
   */
  @Nullable
  public static <T> T getter(final Object target, final Method method) {
    final var unprefixed = method.getName().replace("get", "");
    final var fieldName = Commons.uncapitalize(unprefixed);

    return Reflect.getter(target, fieldName);
  }

  /**
   * Updates a field using either a wither or a setter method. Then returns the
   * target with the updated field.
   *
   * @param <T> the target type
   * @param target the target instance where the field is updated
   * @param field the field to update
   * @param value the value to update the field to
   * @return the updated target
   * @throws ReflectException if the wither/setter method cannot be found or
   *                          fails to be invoked
   */
  @Nullable
  public static <T> T update(final T target, final Field field, final Object value) {
    final var targetType = field.getDeclaringClass();
    final var fieldType = field.getType();

    return Reflect
      .findWitherMethod(field)
      .map(witherMethod -> {
        final var witherFn = Maybe
          .from(() -> metafactory(
            LOOKUP,
            "apply",
            methodType(BiFunction.class),
            methodType(Object.class, Object.class, Object.class),
            witherMethod,
            methodType(targetType, targetType, fieldType)
          ))
          .map(CallSite::getTarget)
          .solve(method -> method.invoke()) // NOSONAR
          .solve(Commons::<BiFunction<T, Object, T>>cast)
          .orThrow(ReflectException::of);

        return witherFn.apply(target, value);
      })
      .orElseGet(() -> {
        final var setterMethod = Reflect
          .findSetterMethod(field)
          .orElseThrow(() -> ReflectException.of("Unable to find wither/setter for field: ".concat(field.getName())));
        final var setterFn = Maybe
          .from(() -> metafactory(
            LOOKUP,
            "accept",
            methodType(BiConsumer.class),
            methodType(void.class, Object.class, Object.class),
            setterMethod,
            methodType(void.class, targetType, fieldType)
          ))
          .map(CallSite::getTarget)
          .solve(method -> method.invoke()) // NOSONAR
          .solve(Commons::<BiConsumer<T, Object>>cast)
          .orThrow(ReflectException::of);

        setterFn.accept(target, value);
        return target;
      });
  }

  /**
   * Updates a field using either a wither or a setter method. Then returns the
   * target with the updated field.
   *
   * @param <T> the target type
   * @param target the target instance where the field is updated
   * @param fieldName the field name to update
   * @param value the value to update the field to
   * @return the updated target
   * @throws ReflectException if the wither/setter method cannot be found or
   *                          fails to be invoked
   */
  @Nullable
  public static <T> T update(final T target, final String fieldName, final Object value) {
    final var field = Maybe.of(fieldName)
      .solve(target.getClass()::getDeclaredField)
      .orThrow(ReflectException::of);

    return Reflect.update(target, field, value);
  }

  /**
   * Curried version of {@link Reflect#update(Object, String, Object)} overload.
   *
   * @param <T> the target type
   * @param fieldName the field name to update
   * @param value the value to update the field to
   * @return a function that takes the target as argument and returns the
   *         updated target
   */
  public static <T> Function<T, @Nullable T> update(final String fieldName, final Object value) {
    return target -> Reflect.update(target, fieldName, value);
  }

  /**
   * Returns the first inner type of a field's type.
   *
   * @param <T> the type of the generic
   * @param field the field to find the inner type
   * @return the inner type of a field's type
   * @throws ReflectException if the field's type does not have an inner
   *                          generic type
   */
  public static <T> Class<T> innerTypeOf(final Field field) {
    return Maybe.of(field)
      .solve(Field::getGenericType)
      .cast(ParameterizedType.class, (x, e) -> {
        final var message = "The type of '%s' field has no parameterized types".formatted(field.getName());
        return ReflectException.of(message);
      })
      .solve(ParameterizedType::getActualTypeArguments)
      .solve(type -> type[0])
      .solve(Commons::<Class<T>>cast)
      .orThrow();
  }

  private static MethodHandle findGetterMethod(final Field field) {
    final var targetType = field.getDeclaringClass();
    final var fieldName = field.getName();
    final var getterName = "get".concat(Commons.capitalize(fieldName));
    final var getterType = methodType(field.getType());

    return Maybe
      .from(() -> LOOKUP.findVirtual(targetType, fieldName, getterType))
      .onErrorSolve(NoSuchMethodException.class, e -> LOOKUP.findVirtual(targetType, getterName, getterType))
      .orThrow(e -> ReflectException.of("Unable to find getter for field: ".concat(fieldName)));
  }

  private static Optional<MethodHandle> findWitherMethod(final Field field) {
    final var targetType = field.getDeclaringClass();
    final var witherName = "with".concat(Commons.capitalize(field.getName()));
    final var witherType = methodType(targetType, field.getType());

    return Maybe
      .from(() -> LOOKUP.findVirtual(targetType, witherName, witherType))
      .toOptional();
  }

  private static Optional<MethodHandle> findSetterMethod(final Field field) {
    final var targetType = field.getDeclaringClass();
    final var setterName = "set".concat(Commons.capitalize(field.getName()));
    final var setterType = methodType(void.class, field.getType());

    return Maybe
      .from(() -> LOOKUP.findVirtual(targetType, setterName, setterType))
      .toOptional();
  }
}
