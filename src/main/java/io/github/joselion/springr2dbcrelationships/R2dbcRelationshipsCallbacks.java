package io.github.joselion.springr2dbcrelationships;

import static java.util.function.Predicate.not;

import java.util.List;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.springframework.context.ApplicationContext;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.mapping.OutboundRow;
import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import org.springframework.data.r2dbc.mapping.event.AfterSaveCallback;
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import io.github.joselion.springr2dbcrelationships.annotations.ManyToMany;
import io.github.joselion.springr2dbcrelationships.annotations.ManyToOne;
import io.github.joselion.springr2dbcrelationships.annotations.OneToMany;
import io.github.joselion.springr2dbcrelationships.annotations.OneToOne;
import io.github.joselion.springr2dbcrelationships.helpers.Commons;
import io.github.joselion.springr2dbcrelationships.helpers.Reflect;
import io.github.joselion.springr2dbcrelationships.processors.ManyToManyProcessor;
import io.github.joselion.springr2dbcrelationships.processors.ManyToOneProcessor;
import io.github.joselion.springr2dbcrelationships.processors.OneToManyProcessor;
import io.github.joselion.springr2dbcrelationships.processors.OneToOneProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;

/**
 * Implementation of all the callback that process the R2DBC Relationship
 * annotations.
 *
 * @param <T> the entity type
 * @param template the r2dbc entity template
 * @param context the Spring application context
 */
public record R2dbcRelationshipsCallbacks<T>(
  R2dbcEntityTemplate template,
  ApplicationContext context
) implements AfterConvertCallback<T>, AfterSaveCallback<T>, BeforeConvertCallback<T> {

  @Override
  public Publisher<T> onAfterConvert(final T entity, final SqlIdentifier table) {
    final var oneToOneProcessor = new OneToOneProcessor(this.template, entity, table, this.context);
    final var oneToManyProcessor = new OneToManyProcessor(this.template, entity, table, this.context);
    final var manyToOneProcessor = new ManyToOneProcessor(this.template, entity, table, this.context);
    final var manyToManyProcessor = new ManyToManyProcessor(this.template, entity, table, this.context);

    return Mono.just(entity)
      .map(T::getClass)
      .map(Class::getDeclaredFields)
      .flatMapMany(Flux::fromArray)
      .parallel()
      .runOn(Schedulers.parallel())
      .flatMap(field ->
        Mono.just(OneToOne.class)
          .mapNotNull(field::getAnnotation)
          .flatMap(oneToOneProcessor.populate(field))
          .switchIfEmpty(
            Mono.just(OneToMany.class)
              .mapNotNull(field::getAnnotation)
              .flatMap(oneToManyProcessor.populate(field))
          )
          .switchIfEmpty(
            Mono.just(ManyToOne.class)
              .mapNotNull(field::getAnnotation)
              .flatMap(manyToOneProcessor.populate(field))
          )
          .switchIfEmpty(
            Mono.just(ManyToMany.class)
              .mapNotNull(field::getAnnotation)
              .flatMap(manyToManyProcessor.populate(field))
          )
          .map(value -> Tuples.of(field, value))
      )
      .sequential()
      .reduce(entity, (acc, tuple) -> {
        final var field = tuple.getT1();
        final var value = tuple.getT2();

        return Reflect.update(acc, field, value);
      })
      .defaultIfEmpty(entity);
  }

  @Override
  public Publisher<T> onAfterSave(final T entity, final OutboundRow outboundRow, final SqlIdentifier table) {
    final var oneToOneProcessor = new OneToOneProcessor(this.template, entity, table, this.context);
    final var oneToManyProcessor = new OneToManyProcessor(this.template, entity, table, this.context);
    final var manyToManyProcessor = new ManyToManyProcessor(this.template, entity, table, this.context);

    return Mono.just(entity)
      .map(T::getClass)
      .map(Class::getDeclaredFields)
      .flatMapIterable(List::of)
      .parallel()
      .runOn(Schedulers.parallel())
      .flatMap(field ->
        Mono.just(OneToOne.class)
          .mapNotNull(field::getAnnotation)
          .filter(not(OneToOne::readonly))
          .filter(not(oneToOneProcessor.isBackReference(field)))
          .flatMap(oneToOneProcessor.persist(field))
          .switchIfEmpty(
            Mono.just(OneToMany.class)
              .mapNotNull(field::getAnnotation)
              .filter(not(OneToMany::readonly))
              .flatMap(oneToManyProcessor.persist(field))
          )
          .switchIfEmpty(
            Mono.just(ManyToMany.class)
              .mapNotNull(field::getAnnotation)
              .filter(not(ManyToMany::readonly))
              .flatMap(manyToManyProcessor.persist(field))
          )
          .map(value -> Tuples.of(field, value))
      )
      .sequential()
      .reduce(entity, (acc, tuple) -> {
        final var field = tuple.getT1();
        final var value = tuple.getT2();

        return Reflect.update(acc, field, value);
      })
      .defaultIfEmpty(entity);
  }

  @Override
  public Publisher<T> onBeforeConvert(final T entity, final SqlIdentifier table) {
    final var annotations = List.of(OneToOne.class, ManyToOne.class);

    return Mono.just(entity)
      .map(T::getClass)
      .map(Class::getDeclaredFields)
      .flatMapIterable(List::of)
      .filter(field -> annotations.stream().anyMatch(field::isAnnotationPresent))
      .reduce(Mono.just(entity), (acc, field) ->
        acc.flatMap(nextEntity -> {
          final var oneToOneProcessor = new OneToOneProcessor(this.template, nextEntity, table, this.context);
          final var manyToOneProcessor = new ManyToOneProcessor(this.template, nextEntity, table, this.context);

          return Mono.just(OneToOne.class)
            .mapNotNull(field::getAnnotation)
            .filter(not(OneToOne::readonly))
            .filter(oneToOneProcessor.isBackReference(field))
            .flatMap(oneToOneProcessor.persist(field))
            .switchIfEmpty(
              Mono.just(ManyToOne.class)
                .mapNotNull(field::getAnnotation)
                .filter(ManyToOne::persist)
                .flatMap(manyToOneProcessor.persist(field))
            )
            .switchIfEmpty(acc);
        })
        .map(Commons::<T>cast)
      )
      .flatMap(Function.identity())
      .defaultIfEmpty(entity);
  }
}
