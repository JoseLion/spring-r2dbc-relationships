package io.github.joselion.springr2dbcrelationships.models.phone;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface PhoneRepository extends ReactiveCrudRepository<Phone, UUID> {

}
