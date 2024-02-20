package io.github.joselion.springr2dbcrelationships;

import static org.assertj.core.api.Assertions.assertThat;
import static reactor.function.TupleUtils.function;

import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.joselion.springr2dbcrelationships.models.city.City;
import io.github.joselion.springr2dbcrelationships.models.city.CityRepository;
import io.github.joselion.springr2dbcrelationships.models.country.Country;
import io.github.joselion.springr2dbcrelationships.models.country.CountryRepository;
import io.github.joselion.springr2dbcrelationships.models.phone.Phone;
import io.github.joselion.springr2dbcrelationships.models.phone.PhoneRepository;
import io.github.joselion.springr2dbcrelationships.models.phone.details.PhoneDetails;
import io.github.joselion.springr2dbcrelationships.models.phone.details.PhoneDetailsRepository;
import io.github.joselion.testing.annotations.IntegrationTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@IntegrationTest class RelationalCallbacksTest {

  @Autowired
  private PhoneRepository phoneRepo;

  @Autowired
  private PhoneDetailsRepository phoneDetailsRepo;

  @Autowired
  private CountryRepository countryRepo;

  @Autowired
  private CityRepository cityRepo;

  private final PhoneDetails details = PhoneDetails.empty().withProvider("Movistar").withTechnology("5G");

  private final Phone phone = Phone.empty().withNumber("+593998591484");

  private final City newYork = City.of("New York");

  private final City boston = City.of("Boston");

  private final City chicago = City.of("Chicago");

  private final Country usa = Country.of("United States of America");

  @Nested class afterConvert {
    @Nested class when_a_field_has_a_OneToOne_annotation {
      @Nested class and_the_annotation_is_in_the_parent {
        @Test void populates_the_field_with_the_child_entity() {
          phoneRepo.save(phone)
            .map(Phone::id)
            .delayUntil(id ->
              Mono.just(id)
                .map(details::withPhoneId)
                .flatMap(phoneDetailsRepo::save)
            )
            .flatMap(phoneRepo::findById)
            .as(StepVerifier::create)
            .assertNext(found -> {
              final var phoneDetails = found.phoneDetails();

              assertThat(found.id()).isNotNull();
              assertThat(phoneDetails).isNotNull();
              assertThat(phoneDetails.id()).isNotNull();
              assertThat(phoneDetails.phoneId()).isEqualTo(found.id());
              assertThat(phoneDetails.provider()).isEqualTo(details.provider());
              assertThat(phoneDetails.technology()).isEqualTo(details.technology());
            })
            .verifyComplete();
        }
      }

      @Nested class and_the_annotation_is_in_the_child {
        @Test void populates_the_field_with_the_parent_entity() {
          phoneRepo.save(phone)
            .map(Phone::id)
            .map(details::withPhoneId)
            .flatMap(phoneDetailsRepo::save)
            .map(PhoneDetails::id)
            .flatMap(phoneDetailsRepo::findById)
            .as(StepVerifier::create)
            .assertNext(found -> {
              assertThat(found.phone()).isNotNull();
              assertThat(found.phone().id()).isNotNull();
              assertThat(found.phone().number()).isEqualTo(phone.number());
              assertThat(found.phone().phoneDetails().phone()).isNull();
              assertThat(found.phone().phoneDetails())
                .usingRecursiveComparison()
                .ignoringFields("phone")
                .isEqualTo(found);
            })
            .verifyComplete();
        }
      }
    }

    @Nested class when_a_field_has_a_OneToMany_annotation {
      @Test void populates_the_field_with_the_children_entities() {
        countryRepo.save(usa)
          .map(Country::id)
          .delayUntil(id ->
            Flux.just(newYork, boston, chicago)
              .map(city -> city.withCountryId(id))
              .publish(cityRepo::saveAll)
          )
          .flatMap(countryRepo::findById)
          .as(StepVerifier::create)
          .assertNext(found -> {
            assertThat(found.id()).isNotNull();
            assertThat(found.cities())
              .isNotEmpty()
              .extracting(City::name)
              .containsExactly("New York", "Boston", "Chicago");
          })
          .verifyComplete();
      }
    }
  }

  @Nested class onAfterSave {
    @Nested class when_a_field_has_a_OneToOne_annotation {
      @Nested class and_the_annotation_is_in_the_parent {
        @Nested class and_the_child_does_not_exist {
          @Test void creates_the_child_entity() {
            Mono.just(details)
              .map(phone::withPhoneDetails)
              .flatMap(phoneRepo::save)
              .map(Phone::phoneDetails)
              .mapNotNull(PhoneDetails::id)
              .flatMap(phoneDetailsRepo::findById)
              .as(StepVerifier::create)
              .assertNext(found -> {
                assertThat(found.id()).isNotNull();
                assertThat(found.provider()).isEqualTo(details.provider());
                assertThat(found.technology()).isEqualTo(details.technology());
              })
              .verifyComplete();
          }
        }

        @Nested class and_the_child_already_exists {
          @Test void updates_the_child_entity() {
            phoneRepo.save(phone)
              .map(Phone::id)
              .map(details::withPhoneId)
              .flatMap(phoneDetailsRepo::save)
              .map(saved -> saved.withTechnology("5G"))
              .map(phone::withPhoneDetails)
              .flatMap(phoneRepo::save)
              .map(Phone::phoneDetails)
              .map(PhoneDetails::id)
              .flatMap(phoneDetailsRepo::findById)
              .as(StepVerifier::create)
              .assertNext(found -> {
                assertThat(found.id()).isNotNull();
                assertThat(found.provider()).isEqualTo(details.provider());
                assertThat(found.technology()).isEqualTo("5G");
              })
              .verifyComplete();
          }
        }

        @Nested class and_the_child_field_is_null {
          @Test void deletes_the_orphan_child() {
            Mono.just(details)
              .map(phone::withPhoneDetails)
              .flatMap(phoneRepo::save)
              .delayUntil(saved -> {
                final var updated = saved.withPhoneDetails(null);
                return phoneRepo.save(updated);
              })
              .map(Phone::phoneDetails)
              .map(PhoneDetails::id)
              .flatMap(phoneDetailsRepo::findById)
              .as(StepVerifier::create)
              .expectNextCount(0)
              .verifyComplete();
          }
        }
      }

      @Nested class and_the_annotation_is_in_the_child {
        @Test void never_persists_the_parent() {
          phoneRepo.save(phone)
            .flatMap(saved ->
              Mono.just(saved)
                .map(Phone::id)
                .map(details::withPhoneId)
                .flatMap(phoneDetailsRepo::save)
                .map(pd -> pd.withPhone(saved.withNumber("N/A")))
            )
            .flatMap(phoneDetailsRepo::save)
            .map(PhoneDetails::phone)
            .map(Phone::id)
            .flatMap(phoneRepo::findById)
            .as(StepVerifier::create)
            .assertNext(found -> {
              assertThat(found.number()).isEqualTo(phone.number());
            })
            .verifyComplete();
        }
      }
    }

    @Nested class when_a_field_has_a_OneToMany_annotation {
      @Nested class and_there_are_no_orphan_children {
        @Nested class and_the_children_are_new {
          @Test void creates_the_children_entities() {
            final var cities = List.of(newYork, boston, chicago);

            Mono.just(cities)
              .map(usa::withCities)
              .flatMap(countryRepo::save)
              .as(StepVerifier::create)
              .assertNext(saved -> {
                assertThat(saved.id()).isNotNull();
                assertThat(saved.cities())
                  .isNotEmpty()
                  .allSatisfy(city -> {
                    assertThat(city.id()).isNotNull();
                    assertThat(city.countryId()).isEqualTo(saved.id());
                  })
                  .extracting(City::name)
                  .containsExactly("New York", "Boston", "Chicago");
              })
              .verifyComplete();
          }
        }

        @Nested class and_the_children_already_exist {
          @Test void updates_the_children_entities() {
            countryRepo.save(usa)
              .zipWhen(saved ->
                Flux.just(newYork, boston, chicago)
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
              .as(StepVerifier::create)
              .assertNext(cities -> {
                assertThat(cities)
                  .isNotEmpty()
                  .extracting(City::name)
                  .containsExactly("NEW YORK", "BOSTON", "CHICAGO");
              })
              .verifyComplete();
          }
        }
      }

      @Nested class and_there_are_orphan_children {
        @Test void persists_the_children_entities_and_delete_the_orphans() {
          final var cities = List.of(newYork, boston, chicago);

          Mono.just(cities)
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
            .as(StepVerifier::create)
            .assertNext(result -> {
              assertThat(result)
                .extracting(City::name)
                .containsExactly("New York", "Chicago");
            })
            .verifyComplete();
        }
      }

      @Nested class and_all_the_children_are_left_orphan {
        @Test void deletes_all_the_orphan_children() {
          final var cities = List.of(newYork, boston, chicago);

          Mono.just(cities)
            .map(usa::withCities)
            .flatMap(countryRepo::save)
            .map(saved -> saved.withCities(List.of()))
            .flatMap(countryRepo::save)
            .map(Country::id)
            .flatMapMany(cityRepo::findByCountryId)
            .collectList()
            .as(StepVerifier::create)
            .assertNext(found -> {
              assertThat(found).isEmpty();
            })
            .verifyComplete();
        }
      }

      @Nested class and_the_children_field_is_null {
        @Test void deletes_all_the_orphan_children() {
          final var cities = List.of(newYork, boston, chicago);

          Mono.just(cities)
            .map(usa::withCities)
            .flatMap(countryRepo::save)
            .map(saved -> saved.withCities(null))
            .flatMap(countryRepo::save)
            .map(Country::id)
            .flatMapMany(cityRepo::findByCountryId)
            .collectList()
            .as(StepVerifier::create)
            .assertNext(found -> {
              assertThat(found).isEmpty();
            })
            .verifyComplete();
        }
      }
    }
  }
}
