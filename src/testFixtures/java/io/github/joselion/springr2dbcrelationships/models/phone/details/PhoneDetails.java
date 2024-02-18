package io.github.joselion.springr2dbcrelationships.models.phone.details;

import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.annotation.Id;

import io.github.joselion.springr2dbcrelationships.annotations.OneToOne;
import io.github.joselion.springr2dbcrelationships.models.phone.Phone;
import lombok.With;

@With
public record PhoneDetails(
  @Id @Nullable UUID id,
  UUID phoneId,
  @OneToOne(backReference = true) Phone phone,
  String provider,
  String technology
) {

  public static PhoneDetails empty() {
    return new PhoneDetails(
      null,
      UUID.randomUUID(),
      Phone.empty(),
      "",
      ""
    );
  }
}
