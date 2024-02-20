package io.github.joselion.springr2dbcrelationships.models.country;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface CountryRepository extends ReactiveCrudRepository<Country, UUID> {

}
