package io.github.joselion.springr2dbcrelationships.processors;

import static org.assertj.core.api.Assertions.assertThat;
import static reactor.function.TupleUtils.consumer;
import static reactor.function.TupleUtils.function;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.joselion.springr2dbcrelationships.models.city.City;
import io.github.joselion.springr2dbcrelationships.models.city.CityRepository;
import io.github.joselion.springr2dbcrelationships.models.country.Country;
import io.github.joselion.springr2dbcrelationships.models.country.CountryRepository;
import io.github.joselion.testing.annotations.IntegrationTest;
import io.github.joselion.testing.transactional.TxStepVerifier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@IntegrationTest class OneToManyProcessorTest {

  @Autowired
  private CountryRepository countryRepo;

  @Autowired
  private CityRepository cityRepo;

  private final Country usa = Country.of("United States of America");

  private final String newYork = "New York";

  private final String boston = "Boston";

  private final String chicago = "Chicago";

  @Nested class populate {
    @Test void populates_the_field_with_the_children_entities() {
      countryRepo.save(usa)
        .map(Country::id)
        .delayUntil(id ->
          Flux.just(newYork, boston, chicago)
            .delayElements(Duration.ofMillis(10))
            .map(City::of)
            .map(city -> city.withCountryId(id))
            .publish(cityRepo::saveAll)
        )
        .flatMap(countryRepo::findById)
        .as(TxStepVerifier::withRollback)
        .assertNext(found -> {
          assertThat(found.id()).isNotNull();
          assertThat(found.cities())
            .allSatisfy(city -> assertThat(city.country()).isNull())
            .extracting(City::name)
            .containsExactly(chicago, boston, newYork);
        })
        .verifyComplete();
    }
  }

  @Nested class persist {
    @Nested class when_there_are_no_orphan_children {
      @Nested class and_the_children_are_new {
        @Test void creates_the_children_entities() {
          Flux.just(newYork, boston, chicago)
            .map(City::of)
            .collectList()
            .map(usa::withCities)
            .flatMap(countryRepo::save)
            .as(TxStepVerifier::withRollback)
            .assertNext(saved -> {
              assertThat(saved.id()).isNotNull();
              assertThat(saved.cities())
                .isNotEmpty()
                .allSatisfy(city -> {
                  assertThat(city.id()).isNotNull();
                  assertThat(city.countryId()).isEqualTo(saved.id());
                })
                .extracting(City::name)
                .containsExactly(newYork, boston, chicago);
            })
            .verifyComplete();
        }
      }

      @Nested class and_the_children_already_exist {
        @Test void updates_the_children_entities() {
          countryRepo.save(usa)
            .zipWhen(saved ->
              Flux.just(newYork, boston, chicago)
                .map(City::of)
                .map(city -> city.withCountryId(saved.id()))
                .publish(cityRepo::saveAll)
                .collectList()
            )
            .flatMap(function((country, cities) ->
              Flux.fromIterable(cities)
                .map(city -> city.withName(city.name().toUpperCase()))
                .collectList()
                .map(country::withCities)
                .flatMap(countryRepo::save)
            ))
            .map(Country::id)
            .flatMapMany(cityRepo::findByCountryId)
            .collectList()
            .as(TxStepVerifier::withRollback)
            .assertNext(cities -> {
              assertThat(cities)
                .isNotEmpty()
                .extracting(City::name)
                .contains(
                  newYork.toUpperCase(),
                  boston.toUpperCase(),
                  chicago.toUpperCase()
                );
            })
            .verifyComplete();
        }
      }
    }

    @Nested class when_there_are_orphan_children {
      @Test void persists_the_children_entities_and_delete_the_orphans() {
        Flux.just(newYork, boston, chicago)
          .map(City::of)
          .collectList()
          .map(usa::withCities)
          .flatMap(countryRepo::save)
          .map(saved -> {
            final var nextCities = saved.cities()
              .stream()
              .filter(city -> city.name().length() > 6)
              .toList();
            return saved.withCities(nextCities);
          })
          .flatMap(countryRepo::save)
          .map(Country::id)
          .flatMapMany(cityRepo::findByCountryId)
          .collectList()
          .as(TxStepVerifier::withRollback)
          .assertNext(result -> {
            assertThat(result)
              .extracting(City::name)
              .containsExactly(newYork, chicago);
          })
          .verifyComplete();
      }
    }

    @Nested class when_all_the_children_are_left_orphan {
      @Test void deletes_all_the_orphan_children() {
        Flux.just(newYork, boston, chicago)
          .map(City::of)
          .collectList()
          .map(usa::withCities)
          .flatMap(countryRepo::save)
          .map(saved -> saved.withCities(List.of()))
          .flatMap(countryRepo::save)
          .map(Country::id)
          .flatMapMany(cityRepo::findByCountryId)
          .collectList()
          .as(TxStepVerifier::withRollback)
          .assertNext(found -> {
            assertThat(found).isEmpty();
          })
          .verifyComplete();
      }
    }

    @Nested class when_the_children_field_is_null {
      @Test void ignores_the_field_associations() {
        Flux.just(newYork, boston, chicago)
          .map(City::of)
          .collectList()
          .map(usa::withCities)
          .flatMap(countryRepo::save)
          .map(saved -> saved.withCities(null))
          .flatMap(countryRepo::save)
          .zipWhen(updated ->
            Mono.just(updated)
              .map(Country::id)
              .flatMapMany(cityRepo::findByCountryId)
              .collectList()
          )
          .as(TxStepVerifier::withRollback)
          .assertNext(consumer((updated, found) -> {
            assertThat(updated.cities()).isNull();
            assertThat(found)
              .extracting(City::name)
              .contains(chicago, boston, newYork);
          }))
          .verifyComplete();
      }
    }
  }
}
