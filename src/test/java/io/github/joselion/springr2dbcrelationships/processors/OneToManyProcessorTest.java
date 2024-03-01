package io.github.joselion.springr2dbcrelationships.processors;

import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static reactor.function.TupleUtils.consumer;
import static reactor.function.TupleUtils.function;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.joselion.springr2dbcrelationships.exceptions.RelationshipException;
import io.github.joselion.springr2dbcrelationships.models.city.City;
import io.github.joselion.springr2dbcrelationships.models.city.CityRepository;
import io.github.joselion.springr2dbcrelationships.models.country.Country;
import io.github.joselion.springr2dbcrelationships.models.country.CountryRepository;
import io.github.joselion.springr2dbcrelationships.models.town.Town;
import io.github.joselion.springr2dbcrelationships.models.town.TownRepository;
import io.github.joselion.testing.annotations.IntegrationTest;
import io.github.joselion.testing.transactional.TxStepVerifier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@IntegrationTest class OneToManyProcessorTest {

  @Autowired
  private CountryRepository countryRepo;

  @Autowired
  private CityRepository cityRepo;

  @Autowired
  private TownRepository townRepo;

  private final Country usa = Country.of("United States of America");

  private final String newYork = "New York";

  private final String boston = "Boston";

  private final String chicago = "Chicago";

  private final String manhattan = "Manhattan";

  private final String albuquerque = "Albuquerque";

  private final String springfield = "Springfield";

  @Nested class populate {
    @Test void populates_the_field_with_the_children_entities() {
      countryRepo.save(usa)
        .map(Country::id)
        .delayUntil(id ->
          Flux.just(newYork, boston, chicago)
            .map(City::of)
            .delayElements(Duration.ofMillis(1))
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
      @Nested class and_the_keepOrphans_option_is_false {
        @Test void persists_the_children_entities_and_delete_the_orphans() {
          Flux.just(newYork, boston, chicago)
            .map(City::of)
            .collectList()
            .map(usa::withCities)
            .flatMap(countryRepo::save)
            .map(saved ->
              saved.withCitiesBy(cities ->
                cities.stream()
                  .filter(not(city -> city.name().equals(boston)))
                  .toList()
              )
            )
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

      @Nested class and_the_keepOrphans_option_is_true {
        @Test void persists_the_children_entities_and_unlinks_the_orphans() {
          Flux.just(manhattan, albuquerque, springfield)
            .map(Town::of)
            .collectList()
            .map(usa::withTowns)
            .flatMap(countryRepo::save)
            .map(saved ->
              saved.withTownsBy(towns ->
                towns.stream()
                  .filter(not(town -> town.name().equals(albuquerque)))
                  .toList()
              )
            )
            .flatMap(countryRepo::save)
            .zipWhen(x -> townRepo.findAll().collectList())
            .as(TxStepVerifier::withRollback)
            .assertNext(consumer((country, found) -> {
              assertThat(found)
                .extracting(Town::name, Town::countryId)
                .containsExactly(
                  tuple(manhattan, country.id()),
                  tuple(albuquerque, null),
                  tuple(springfield, country.id())
                );
            }))
            .verifyComplete();
        }
      }
    }

    @Nested class when_all_the_children_are_left_orphan {
      @Nested class and_the_keepOrphans_option_is_false {
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

      @Nested class and_the_keepOrphans_option_is_true {
        @Test void unlinks_all_the_orphan_children() {
          Flux.just(manhattan, albuquerque, springfield)
            .map(Town::of)
            .collectList()
            .map(usa::withTowns)
            .flatMap(countryRepo::save)
            .map(saved -> saved.withTowns(List.of()))
            .flatMap(countryRepo::save)
            .then(townRepo.findAll().collectList())
            .as(TxStepVerifier::withRollback)
            .assertNext(found -> {
              assertThat(found)
                .allSatisfy(town -> assertThat(town.countryId()).isNull())
                .extracting(Town::name)
                .containsExactly(manhattan, albuquerque, springfield);
            })
            .verifyComplete();
        }
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

    @Nested class when_the_relation_is_link_only {
      @Nested class and_all_children_entities_exist {
        @Test void links_the_children_without_updating_anything_else() {
          Flux.just(manhattan, albuquerque, springfield)
            .map(Town::of)
            .publish(townRepo::saveAll)
            .map(town -> town.withNameBy(String::toUpperCase))
            .collectList()
            .map(usa.withTowns(null)::withSettlements)
            .flatMap(countryRepo::save)
            .zipWhen(country ->
              Mono.just(country)
                .map(Country::id)
                .flatMapMany(townRepo::findByCountryId)
                .collectList()
            )
            .as(TxStepVerifier::withRollback)
            .assertNext(consumer((country, towns) -> {
              assertThat(country.settlements())
                .allSatisfy(town -> assertThat(town.countryId()).isEqualTo(country.id()))
                .extracting(Town::name)
                .containsExactly(manhattan.toUpperCase(), albuquerque.toUpperCase(), springfield.toUpperCase());
              assertThat(towns)
                .allSatisfy(town -> assertThat(town.countryId()).isEqualTo(country.id()))
                .extracting(Town::name)
                .containsExactly(manhattan, albuquerque, springfield);
            }))
            .verifyComplete();
        }
      }

      @Nested class and_a_child_enity_does_not_exist {
        @Test void raises_a_RelationshipException_error() {
          Flux.just(manhattan, albuquerque)
            .map(Town::of)
            .publish(townRepo::saveAll)
            .concatWithValues(Town.of(springfield))
            .collectList()
            .map(usa.withTowns(null)::withSettlements)
            .flatMap(countryRepo::save)
            .as(TxStepVerifier::withRollback)
            .verifyErrorSatisfies(error -> {
              assertThat(error)
                .isInstanceOf(RelationshipException.class)
                .hasMessageStartingWith("Link-only entity is missing its primary key: Town[id=null,");
            });
        }
      }
    }
  }
}
