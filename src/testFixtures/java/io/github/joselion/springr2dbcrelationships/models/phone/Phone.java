package io.github.joselion.springr2dbcrelationships.models.phone;

import java.time.LocalDateTime;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.annotation.Id;

import io.github.joselion.springr2dbcrelationships.annotations.OneToOne;
import io.github.joselion.springr2dbcrelationships.models.phone.details.PhoneDetails;
import lombok.With;

@With
public record Phone(
  @Id @Nullable UUID id,
  LocalDateTime createdAt,
  String number,
  @Nullable @OneToOne PhoneDetails phoneDetails
) {

  public static Phone empty() {
    return new Phone(
      null,
      LocalDateTime.now(),
      "",
      null
    );
  }
}
