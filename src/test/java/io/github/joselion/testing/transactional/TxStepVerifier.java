package io.github.joselion.testing.transactional;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Transactional {@link StepVerifier} interface.
 */
public interface TxStepVerifier extends StepVerifier {

  /**
   * Creates a transactional step verifier with rollback from a Mono publisher.
   *
   * @param <T> the type of the publisher
   * @param publisher the Mono publisher
   * @return a transactional stepo verifier
   */
  static <T> FirstStep<T> withRollback(final Mono<T> publisher) {
    return StepVerifier.create(publisher.as(Transactions::withRollback));
  }

  /**
   * Creates a transactional step verifier with rollback from a Flux publisher.
   *
   * @param <T> the type of the publisher
   * @param publisher the Flux publisher
   * @return a transactional stepo verifier
   */
  static <T> FirstStep<T> withRollback(final Flux<T> publisher) {
    return StepVerifier.create(publisher.as(Transactions::withRollback));
  }
}
