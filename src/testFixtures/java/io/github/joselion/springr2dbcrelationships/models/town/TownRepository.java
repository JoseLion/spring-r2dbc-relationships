package io.github.joselion.springr2dbcrelationships.models.town;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;

public interface TownRepository extends ReactiveCrudRepository<Town, UUID> {

  Flux<Town> findByCountryId(UUID countryId);
}
