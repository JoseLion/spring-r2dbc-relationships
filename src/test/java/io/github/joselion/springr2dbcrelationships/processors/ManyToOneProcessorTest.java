package io.github.joselion.springr2dbcrelationships.processors;

import static org.assertj.core.api.Assertions.assertThat;
import static reactor.function.TupleUtils.consumer;

import java.time.Duration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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

@IntegrationTest class ManyToOneProcessorTest {

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

  @Nested class populate {
    @Test void populates_the_field_with_the_parent_entity() {
      countryRepo.save(usa)
        .map(Country::id)
        .zipWhen(id ->
          Flux.just(newYork, boston, chicago)
            .map(City::of)
            .delayElements(Duration.ofMillis(1))
            .map(city -> city.withCountryId(id))
            .publish(cityRepo::saveAll)
            .then(cityRepo.findByName(boston))
        )
        .as(TxStepVerifier::withRollback)
        .assertNext(consumer((countryId, city) -> {
          assertThat(city.countryId()).isEqualTo(countryId);
          assertThat(city.country()).isNotNull();
          assertThat(city.country().id()).isEqualTo(countryId);
          System.err.println("*********** " + city.country());
          assertThat(city.country().cities())
            .allSatisfy(c -> assertThat(c.country()).isNull())
            .extracting(City::name)
            .containsExactly(chicago, boston, newYork);
        }))
        .verifyComplete();
    }
  }

  @Nested class persist {
    @Nested class when_the_persist_option_is_false {
      @Test void does_not_persist_the_annotated_field_by_default() {
        countryRepo.save(usa)
          .map(saved -> saved.withName("USA"))
          .map(updated ->
            City.of(boston)
              .withCountry(updated)
              .withCountryId(updated.id())
          )
          .flatMap(cityRepo::save)
          .map(City::countryId)
          .flatMap(countryRepo::findById)
          .as(TxStepVerifier::withRollback)
          .assertNext(found -> {
            assertThat(found.name()).isEqualTo(usa.name());
            assertThat(found.cities())
              .extracting(City::name)
              .containsExactly(boston);
          })
          .verifyComplete();

      }
    }

    @Nested class when_the_persist_option_is_true {
      @Nested class and_the_parent_does_not_exist {
        @Test void creates_the_parent_entity() {
          Mono.just(usa)
            .map(Town.of(manhattan)::withCountry)
            .flatMap(townRepo::save)
            .map(Town::id)
            .flatMap(townRepo::findById)
            .zipWhen(saved -> countryRepo.findById(saved.countryId()))
            .as(TxStepVerifier::withRollback)
            .assertNext(consumer((town, country) -> {
              assertThat(town.countryId()).isEqualTo(country.id());
              assertThat(town.country().id()).isEqualTo(country.id());
              assertThat(country.towns())
                .extracting(Town::name)
                .containsExactly(manhattan);
            }))
            .verifyComplete();
        }
      }

      @Nested class and_the_parent_does_already_exists {
        @Test void updates_the_parent_entity() {
          Mono.just(usa)
            .map(Town.of(manhattan)::withCountry)
            .flatMap(townRepo::save)
            .map(town -> town.withCountryBy(country -> country.withName("USA")))
            .flatMap(townRepo::save)
            .map(Town::id)
            .flatMap(townRepo::findById)
            .zipWhen(saved -> countryRepo.findById(saved.countryId()))
            .as(TxStepVerifier::withRollback)
            .assertNext(consumer((town, country) -> {
              assertThat(town.countryId()).isEqualTo(country.id());
              assertThat(town.country().id()).isEqualTo(country.id());
              assertThat(country.name()).isEqualTo("USA");
              assertThat(country.towns())
                .extracting(Town::name)
                .containsExactly(manhattan);
            }))
            .verifyComplete();
        }
      }

      @Nested class and_the_parent_is_null {
        @Test void unlinks_the_entity_from_the_parent() {
          Mono.just(usa)
            .map(Town.of(manhattan)::withCountry)
            .flatMap(townRepo::save)
            .map(town -> town.withCountry(null))
            .flatMap(townRepo::save)
            .map(Town::id)
            .flatMap(townRepo::findById)
            .as(TxStepVerifier::withRollback)
            .assertNext(town -> {
              assertThat(town.country()).isNull();
              assertThat(town.countryId()).isNull();
            })
            .verifyComplete();
        }
      }
    }
  }
}
