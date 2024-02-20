package io.github.joselion.springr2dbcrelationships.models.city;

import static io.github.joselion.springr2dbcrelationships.helpers.Constants.UUID_ZERO;

import java.time.LocalDateTime;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.annotation.Id;

import lombok.With;

@With
public record City(
  @Id @Nullable UUID id,
  LocalDateTime createdAt,
  UUID countryId,
  String name
) {

  public static City empty() {
    return new City(
      null,
      LocalDateTime.now(),
      UUID_ZERO,
      ""
    );
  }

  public static City of(final String name) {
    return City.empty().withName(name);
  }
}
