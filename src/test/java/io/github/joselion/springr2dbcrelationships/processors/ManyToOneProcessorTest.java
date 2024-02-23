package io.github.joselion.springr2dbcrelationships.processors;

import static org.assertj.core.api.Assertions.assertThat;
import static reactor.function.TupleUtils.consumer;

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

  private final City newYork = City.of("New York");

  private final City boston = City.of("Boston");

  private final City chicago = City.of("Chicago");

  private final Country usa = Country.of("United States of America");

  @Nested class populate {
    @Test void populates_the_field_with_the_parent_entity() {
      countryRepo.save(usa)
        .map(Country::id)
        .zipWhen(id ->
          Flux.just(newYork, boston, chicago)
            .map(city -> city.withCountryId(id))
            .publish(cityRepo::saveAll)
            .then(cityRepo.findByName(boston.name()))
        )
        .as(TxStepVerifier::withRollback)
        .assertNext(consumer((countryId, city) -> {
          assertThat(city.countryId()).isEqualTo(countryId);
          assertThat(city.country()).isNotNull();
          assertThat(city.country().id()).isEqualTo(countryId);
          assertThat(city.country().cities())
            .allSatisfy(c -> assertThat(c.country()).isNull())
            .extracting(City::name)
            .containsExactly(newYork.name(), boston.name(), chicago.name());
        }))
        .verifyComplete();
    }
  }

  @Nested class persist {
    @Nested class when_the_annotation_does_not_configure_persist {
      @Test void does_not_persist_the_annotated_field_by_default() {
        countryRepo.save(usa)
          .map(saved -> saved.withName("USA"))
          .map(updated ->
            boston
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
              .containsExactly(boston.name());
          })
          .verifyComplete();

      }
    }

    @Nested class when_the_annotation_sets_persist_to_true {
      @Test void persists_the_annotated_field() {
        final var manhattan = Town.of("Manhattan");

        Mono.just(usa)
          .map(manhattan::withCountry)
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
              .containsExactly(manhattan.name());
          }))
          .verifyComplete();
      }
    }
  }
}
