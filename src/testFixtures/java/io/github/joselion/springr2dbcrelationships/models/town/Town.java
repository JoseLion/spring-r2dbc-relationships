package io.github.joselion.springr2dbcrelationships.models.town;

import static io.github.joselion.springr2dbcrelationships.helpers.Constants.UUID_ZERO;

import java.time.LocalDateTime;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.annotation.Id;

import io.github.joselion.springr2dbcrelationships.annotations.ManyToOne;
import io.github.joselion.springr2dbcrelationships.models.country.Country;
import lombok.With;

@With
public record Town(
  @Id @Nullable UUID id,
  LocalDateTime createdAt,
  UUID countryId,
  @ManyToOne(persist = true) Country country,
  String name
) {

  public static Town empty() {
    return new Town(
      null,
      LocalDateTime.now(),
      UUID_ZERO,
      Country.empty(),
      ""
    );
  }

  public static Town of(final String name) {
    return Town.empty().withName(name);
  }
}
