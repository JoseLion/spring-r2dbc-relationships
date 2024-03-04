package io.github.joselion.springr2dbcrelationships.models.details;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface DetailsRepository extends ReactiveCrudRepository<Details, UUID> {

}
