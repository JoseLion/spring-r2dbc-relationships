package io.github.joselion.springr2dbcrelationships.models.paper;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface PaperRepository extends ReactiveCrudRepository<Paper, UUID> {

}
