package io.github.joselion.springr2dbcrelationships.models.author;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface AuthorRepository extends ReactiveCrudRepository<Author, UUID> {

}
