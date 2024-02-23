package io.github.joselion.testing.transactional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.reactive.TransactionalOperator;

import io.github.joselion.springr2dbcrelationships.helpers.StaticContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class Transactions {

  static <T> Mono<T> withRollback(final Mono<T> publisher) {
    final var rxtx = StaticContext.getBean(TransactionalOperator.class);

    return rxtx.execute(tx -> {
      tx.setRollbackOnly();
      return publisher;
    })
    .next();
  }

  static <T> Flux<T> withRollback(final Flux<T> publisher) {
    final var rxtx = StaticContext.getBean(TransactionalOperator.class);

    return rxtx.execute(tx -> {
      tx.setRollbackOnly();
      return publisher;
    });
  }
}
