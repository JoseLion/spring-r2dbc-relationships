package io.github.joselion.springr2dbcrelationships.models.features;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface FeaturesRepository extends ReactiveCrudRepository<Features, UUID> {

}
