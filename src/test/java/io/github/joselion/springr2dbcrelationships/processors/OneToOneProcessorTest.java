package io.github.joselion.springr2dbcrelationships.processors;

import static org.assertj.core.api.Assertions.assertThat;
import static reactor.function.TupleUtils.consumer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.joselion.springr2dbcrelationships.models.details.Details;
import io.github.joselion.springr2dbcrelationships.models.details.DetailsRepository;
import io.github.joselion.springr2dbcrelationships.models.features.Features;
import io.github.joselion.springr2dbcrelationships.models.features.FeaturesRepository;
import io.github.joselion.springr2dbcrelationships.models.mobile.Mobile;
import io.github.joselion.springr2dbcrelationships.models.mobile.MobileRepository;
import io.github.joselion.springr2dbcrelationships.models.phone.Phone;
import io.github.joselion.springr2dbcrelationships.models.phone.PhoneRepository;
import io.github.joselion.testing.annotations.IntegrationTest;
import io.github.joselion.testing.transactional.TxStepVerifier;
import reactor.core.publisher.Mono;

@IntegrationTest class OneToOneProcessorTest {

  @Autowired
  private PhoneRepository phoneRepo;

  @Autowired
  private DetailsRepository detailsRepo;

  @Autowired
  private MobileRepository mobileRepo;

  @Autowired
  private FeaturesRepository featuresRepo;

  private final Details myDetails = Details.of("Movistar", "5G");

  private final Phone myPhone = Phone.of("+593998591484");

  private final Mobile myMobile = Mobile.of("+593998591484");

  private final Features myFeatures = Features.of("5G");

  @Nested class populate {
    @Nested class when_the_annotation_is_in_the_parent {
      @Test void populates_the_field_with_the_child_entity() {
        Mono.just(myDetails)
          .map(myPhone::withDetails)
          .flatMap(phoneRepo::save)
          .map(Phone::id)
          .flatMap(phoneRepo::findById)
          .as(TxStepVerifier::withRollback)
          .assertNext(phone -> {
            final var details = phone.details();

            assertThat(details).isNotNull();
            assertThat(details.phone()).isNull();
            assertThat(details.id()).isNotNull();
            assertThat(details.phoneId()).isEqualTo(phone.id());
            assertThat(details.provider()).isEqualTo(myDetails.provider());
            assertThat(details.technology()).isEqualTo(myDetails.technology());
          })
          .verifyComplete();
      }
    }

    @Nested class when_the_annotation_is_in_the_child {
      @Test void populates_the_field_with_the_parent_entity() {
        Mono.just(myPhone)
          .map(myDetails::withPhone)
          .flatMap(detailsRepo::save)
          .map(Details::id)
          .flatMap(detailsRepo::findById)
          .as(TxStepVerifier::withRollback)
          .assertNext(details -> {
            final var parent = details.phone();

            assertThat(parent).isNotNull();
            assertThat(parent.details()).isNull();
            assertThat(parent.id()).isNotNull();
            assertThat(parent.number()).isEqualTo(myPhone.number());
          })
          .verifyComplete();
      }
    }
  }

  @Nested class persist {
    @Nested class when_the_annotation_is_in_the_parent {
      @Nested class and_the_child_does_not_exist {
        @Test void creates_the_child_entity() {
          Mono.just(myDetails)
            .map(myPhone::withDetails)
            .flatMap(phoneRepo::save)
            .map(Phone::details)
            .mapNotNull(Details::id)
            .flatMap(detailsRepo::findById)
            .zipWhen(x -> phoneRepo.findAll().collectList())
            .as(TxStepVerifier::withRollback)
            .assertNext(consumer((details, allPhones) -> {
              assertThat(allPhones).hasSize(1);
              assertThat(details.id()).isNotNull();
              assertThat(details.phone()).isNotNull();
              assertThat(details.phoneId()).isNotNull();
              assertThat(details.provider()).isEqualTo(myDetails.provider());
              assertThat(details.technology()).isEqualTo(myDetails.technology());
            }))
            .verifyComplete();
        }
      }

      @Nested class and_the_child_already_exists {
        @Test void updates_the_child_entity() {
          Mono.just(myDetails)
            .map(myPhone::withDetails)
            .flatMap(phoneRepo::save)
            .map(phone -> phone.withDetailsBy(details -> details.withTechnology("5G+")))
            .flatMap(phoneRepo::save)
            .map(Phone::details)
            .map(Details::id)
            .flatMap(detailsRepo::findById)
            .zipWhen(x -> phoneRepo.findAll().collectList())
            .as(TxStepVerifier::withRollback)
            .assertNext(consumer((details, allPhones) -> {
              assertThat(allPhones).singleElement()
                .extracting(Phone::id)
                .isEqualTo(details.phoneId());
              assertThat(details.phoneId()).isNotNull();
              assertThat(details.provider()).isEqualTo(myDetails.provider());
              assertThat(details.technology()).isEqualTo("5G+");
            }))
            .verifyComplete();
        }
      }

      @Nested class and_the_child_field_is_null {
        @Nested class and_the_keepOrphan_option_is_false {
          @Test void deletes_the_orphan_child() {
            Mono.just(myDetails)
              .map(myPhone::withDetails)
              .flatMap(phoneRepo::save)
              .delayUntil(phone -> {
                final var updated = phone.withDetails(null);
                return phoneRepo.save(updated);
              })
              .map(Phone::details)
              .map(Details::id)
              .flatMap(detailsRepo::findById)
              .as(TxStepVerifier::withRollback)
              .expectNextCount(0)
              .verifyComplete();
          }
        }

        @Nested class and_the_keepOrphan_option_is_true {
          @Test void unlinks_the_orphan_child() {
             Mono.just(myFeatures)
              .map(myMobile::withFeatures)
              .flatMap(mobileRepo::save)
              .delayUntil(mobile -> {
                final var updated = mobile.withFeatures(null);
                return mobileRepo.save(updated);
              })
              .map(Mobile::features)
              .map(Features::id)
              .flatMap(featuresRepo::findById)
              .as(TxStepVerifier::withRollback)
              .assertNext(features -> {
                assertThat(features.mobile()).isNull();
                assertThat(features.mobileId()).isNull();
              })
              .verifyComplete();
          }
        }
      }
    }

    @Nested class when_the_annotation_is_in_the_child {
      @Nested class and_the_parent_does_not_exist {
        @Test void creates_the_parent_entity() {
          Mono.just(myPhone)
            .map(myDetails::withPhone)
            .flatMap(detailsRepo::save)
            .map(Details::phoneId)
            .flatMap(phoneRepo::findById)
            .zipWhen(x -> detailsRepo.findAll().collectList())
            .as(TxStepVerifier::withRollback)
            .assertNext(consumer((phone, allDetails) -> {
              assertThat(allDetails).hasSize(1);
              assertThat(phone.details()).isNotNull();
              assertThat(phone.details().id()).isNotNull();
              assertThat(phone.number()).isEqualTo(myPhone.number());
            }))
            .verifyComplete();
        }
      }

      @Nested class and_the_parent_already_exists {
        @Test void updates_the_parent_entity() {
          Mono.just(myPhone)
            .map(myDetails::withPhone)
            .flatMap(detailsRepo::save)
            .map(details -> details.withPhoneBy(phone -> phone.withNumber("1234567890")))
            .flatMap(detailsRepo::save)
            .map(Details::phoneId)
            .flatMap(phoneRepo::findById)
            .zipWhen(x -> detailsRepo.findAll().collectList())
            .as(TxStepVerifier::withRollback)
            .assertNext(consumer((phone, allDetails) -> {
              assertThat(allDetails)
                .singleElement()
                .extracting(Details::phoneId)
                .isNotNull()
                .isEqualTo(phone.id());
              assertThat(phone.details()).isNotNull();
              assertThat(phone.details().id()).isNotNull();
              assertThat(phone.number()).isEqualTo("1234567890");
            }))
            .verifyComplete();
        }
      }

      @Nested class and_the_parent_is_null {
        @Nested class and_the_keepOrphan_option_is_false {
          @Test void deletes_the_orphan_parent() {
            Mono.just(myPhone)
              .map(myDetails::withPhone)
              .flatMap(detailsRepo::save)
              .flatMap(details -> {
                final var updated = details.withPhone(null);
                return Mono.zip(
                  detailsRepo.save(updated),
                  phoneRepo.count()
                );
              })
              .as(TxStepVerifier::withRollback)
              .assertNext(consumer((details, phoneCount) -> {
                assertThat(phoneCount).isZero();
                assertThat(details.phone()).isNull();
                assertThat(details.phoneId()).isNull();
              }))
              .verifyComplete();
          }
        }

        @Nested class and_the_keepOrphan_option_is_true {
          @Test void unlinks_the_orphan_parent() {
            Mono.just(myMobile)
              .map(myFeatures::withMobile)
              .flatMap(featuresRepo::save)
              .flatMap(features -> {
                final var updated = features.withMobile(null);
                return Mono.zip(
                  featuresRepo.save(updated),
                  mobileRepo.findById(features.mobileId())
                );
              })
              .as(TxStepVerifier::withRollback)
              .assertNext(consumer((features, mobile) -> {
                assertThat(features.mobile()).isNull();
                assertThat(features.mobileId()).isNull();
                assertThat(mobile.features()).isNull();
              }))
              .verifyComplete();
          }
        }
      }
    }
  }
}
