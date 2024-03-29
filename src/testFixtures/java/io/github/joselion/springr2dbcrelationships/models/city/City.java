package io.github.joselion.springr2dbcrelationships.models.city;

import static io.github.joselion.springr2dbcrelationships.helpers.Constants.UUID_ZERO;

import java.time.LocalDateTime;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.annotation.Id;

import io.github.joselion.springr2dbcrelationships.annotations.ManyToOne;
import io.github.joselion.springr2dbcrelationships.models.country.Country;
import lombok.With;

@With
public record City(
  @Id @Nullable UUID id,
  LocalDateTime createdAt,
  UUID countryId,
  @ManyToOne Country country,
  String name
) {

  public static City empty() {
    return new City(
      null,
      LocalDateTime.now(),
      UUID_ZERO,
      Country.empty(),
      ""
    );
  }

  public static City of(final String name) {
    return City.empty().withName(name);
  }
}
