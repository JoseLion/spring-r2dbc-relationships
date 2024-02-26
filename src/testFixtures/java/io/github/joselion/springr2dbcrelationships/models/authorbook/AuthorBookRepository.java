package io.github.joselion.springr2dbcrelationships.models.authorbook;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;

public interface AuthorBookRepository extends ReactiveCrudRepository<AuthorBook, UUID> {

  Flux<AuthorBook> findByAuthorId(UUID authorId);
}
