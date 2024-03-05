package io.github.joselion.springr2dbcrelationships.models.details;

import static io.github.joselion.springr2dbcrelationships.helpers.Constants.UUID_ZERO;

import java.time.LocalDateTime;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.annotation.Id;

import io.github.joselion.springr2dbcrelationships.annotations.OneToOne;
import io.github.joselion.springr2dbcrelationships.models.phone.Phone;
import lombok.With;
import lombok.experimental.WithBy;

@With
@WithBy
public record Details(
  @Id @Nullable UUID id,
  LocalDateTime createdAt,
  UUID phoneId,
  @OneToOne Phone phone,
  String provider,
  String technology
) {

  public static Details empty() {
    return new Details(
      null,
      LocalDateTime.now(),
      UUID_ZERO,
      Phone.empty(),
      "",
      ""
    );
  }

  public static Details of(final String provider, final String technology) {
    return Details.empty()
      .withProvider(provider)
      .withTechnology(technology);
  }
}
