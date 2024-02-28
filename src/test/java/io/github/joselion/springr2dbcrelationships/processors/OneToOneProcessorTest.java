package io.github.joselion.springr2dbcrelationships.processors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.joselion.springr2dbcrelationships.models.phone.Phone;
import io.github.joselion.springr2dbcrelationships.models.phone.PhoneRepository;
import io.github.joselion.springr2dbcrelationships.models.phone.details.PhoneDetails;
import io.github.joselion.springr2dbcrelationships.models.phone.details.PhoneDetailsRepository;
import io.github.joselion.testing.annotations.IntegrationTest;
import io.github.joselion.testing.transactional.TxStepVerifier;
import reactor.core.publisher.Mono;

@IntegrationTest class OneToOneProcessorTest {

  @Autowired
  private PhoneRepository phoneRepo;

  @Autowired
  private PhoneDetailsRepository phoneDetailsRepo;

  private final PhoneDetails details = PhoneDetails.empty()
    .withProvider("Movistar")
    .withTechnology("5G");

  private final Phone phone = Phone.of("+593998591484");

  @Nested class populate {
    @Nested class when_the_annotation_is_in_the_parent {
      @Test void populates_the_field_with_the_child_entity() {
        phoneRepo.save(phone)
          .map(Phone::id)
          .delayUntil(id ->
            Mono.just(id)
              .map(details::withPhoneId)
              .flatMap(phoneDetailsRepo::save)
          )
          .flatMap(phoneRepo::findById)
          .as(TxStepVerifier::withRollback)
          .assertNext(found -> {
            final var phoneDetails = found.phoneDetails();

            assertThat(found.id()).isNotNull();
            assertThat(phoneDetails).isNotNull();
            assertThat(phoneDetails.phone()).isNull();
            assertThat(phoneDetails.id()).isNotNull();
            assertThat(phoneDetails.phoneId()).isEqualTo(found.id());
            assertThat(phoneDetails.provider()).isEqualTo(details.provider());
            assertThat(phoneDetails.technology()).isEqualTo(details.technology());
          })
          .verifyComplete();
      }
    }

    @Nested class when_the_annotation_is_in_the_child {
      @Test void populates_the_field_with_the_parent_entity() {
        phoneRepo.save(phone)
          .map(Phone::id)
          .map(details::withPhoneId)
          .flatMap(phoneDetailsRepo::save)
          .map(PhoneDetails::id)
          .flatMap(phoneDetailsRepo::findById)
          .as(TxStepVerifier::withRollback)
          .assertNext(found -> {
            final var parent = found.phone();

            assertThat(parent).isNotNull();
            assertThat(parent.phoneDetails()).isNull();
            assertThat(parent.id()).isNotNull();
            assertThat(parent.number()).isEqualTo(phone.number());
          })
          .verifyComplete();
      }
    }
  }

  @Nested class persist {
    @Nested class when_the_annotation_is_in_the_parent {
      @Nested class and_the_child_does_not_exist {
        @Test void creates_the_child_entity() {
          Mono.just(details)
            .map(phone::withPhoneDetails)
            .flatMap(phoneRepo::save)
            .map(Phone::phoneDetails)
            .mapNotNull(PhoneDetails::id)
            .flatMap(phoneDetailsRepo::findById)
            .as(TxStepVerifier::withRollback)
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
            .as(TxStepVerifier::withRollback)
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
            .as(TxStepVerifier::withRollback)
            .expectNextCount(0)
            .verifyComplete();
        }
      }
    }

    @Nested class when_the_annotation_is_in_the_child {
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
          .as(TxStepVerifier::withRollback)
          .assertNext(found -> {
            assertThat(found.number()).isEqualTo(phone.number());
          })
          .verifyComplete();
      }
    }
  }
}
