package io.github.joselion.springr2dbcrelationships.models.mobile;

import java.util.UUID;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface MobileRepository extends ReactiveCrudRepository<Mobile, UUID> {

}
