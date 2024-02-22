package io.github.joselion.springr2dbcrelationships;

import static java.util.function.Predicate.not;

import java.util.List;
import java.util.stream.Stream;

import org.reactivestreams.Publisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.mapping.OutboundRow;
import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import org.springframework.data.r2dbc.mapping.event.AfterSaveCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Component;

import io.github.joselion.springr2dbcrelationships.annotations.OneToMany;
import io.github.joselion.springr2dbcrelationships.annotations.OneToOne;
import io.github.joselion.springr2dbcrelationships.helpers.Reflect;
import io.github.joselion.springr2dbcrelationships.processors.OneToManyProcessor;
import io.github.joselion.springr2dbcrelationships.processors.OneToOneProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuples;

/**
 * Spring component which registers callbacks for all entities to process
 * relationship annotations.
 *
 * @param <T> the entity type
 * @param template the r2dbc entity template
 */
@Component
public record RelationshipCallbacks<T>(
  @Lazy R2dbcEntityTemplate template
) implements AfterConvertCallback<T>, AfterSaveCallback<T> {

  @Override
  public Publisher<T> onAfterConvert(final T entity, final SqlIdentifier table) {
    final var oneToOneProcessor = new OneToOneProcessor(this.template, entity, table);
    final var oneToManyProcessor = new OneToManyProcessor(this.template, entity, table);

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
          .map(value -> Tuples.of(field, value))
      )
      .sequential()
      .reduce(entity, (acc, tuple) -> {
        final var field = tuple.getT1();
        final var value = tuple.getT2();

        return Reflect.update(acc, field, value);
      })
      .defaultIfEmpty(entity)
      .contextWrite(ctx -> {
        final var typeName = entity.getClass().getName();
        final var next = ctx.<List<Class<?>>>getOrEmpty(RelationshipCallbacks.class)
          .map(List::stream)
          .map(prev -> Stream.concat(prev, Stream.of(typeName)))
          .map(Stream::toList)
          .orElse(List.of(typeName));
        return ctx.put(RelationshipCallbacks.class, next);
      });
  }

  @Override
  public Publisher<T> onAfterSave(final T entity, final OutboundRow outboundRow, final SqlIdentifier table) {
    final var oneToOneProcessor = new OneToOneProcessor(this.template, entity, table);
    final var oneToManyProcessor = new OneToManyProcessor(this.template, entity, table);

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
          .filter(not(OneToOne::backReference))
          .flatMap(oneToOneProcessor.persist(field))
          .switchIfEmpty(
            Mono.just(OneToMany.class)
              .mapNotNull(field::getAnnotation)
              .filter(not(OneToMany::readonly))
              .flatMap(oneToManyProcessor.persist(field))
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
}
