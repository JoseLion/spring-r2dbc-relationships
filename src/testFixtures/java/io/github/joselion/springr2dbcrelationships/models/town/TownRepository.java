package io.github.joselion.springr2dbcrelationships.models.town;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface TownRepository extends ReactiveCrudRepository<Town, UUID> {

}
