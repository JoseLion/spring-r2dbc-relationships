package io.github.joselion.springr2dbcrelationships.models.phone.details;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface PhoneDetailsRepository extends ReactiveCrudRepository<PhoneDetails, UUID> {

}
