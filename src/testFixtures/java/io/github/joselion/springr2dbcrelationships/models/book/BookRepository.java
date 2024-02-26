package io.github.joselion.springr2dbcrelationships.models.book;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface BookRepository extends ReactiveCrudRepository<Book, UUID> {

}
