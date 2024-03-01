package io.github.joselion.springr2dbcrelationships.models.town;

import java.time.LocalDateTime;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.annotation.Id;

import io.github.joselion.springr2dbcrelationships.annotations.ManyToOne;
import io.github.joselion.springr2dbcrelationships.models.country.Country;
import lombok.With;
import lombok.experimental.WithBy;

@With
@WithBy
public record Town(
  @Id @Nullable UUID id,
  LocalDateTime createdAt,
  @Nullable UUID countryId,
  @ManyToOne(persist = true) @Nullable Country country,
  String name
) {

  public static Town empty() {
    return new Town(
      null,
      LocalDateTime.now(),
      null,
      null,
      ""
    );
  }

  public static Town of(final String name) {
    return Town.empty().withName(name);
  }
}
