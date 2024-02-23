package io.github.joselion.springr2dbcrelationships.models.city;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CityRepository extends ReactiveCrudRepository<City, UUID> {

  Flux<City> findByCountryId(UUID countryId);

  Mono<City> findByName(String name);
}
