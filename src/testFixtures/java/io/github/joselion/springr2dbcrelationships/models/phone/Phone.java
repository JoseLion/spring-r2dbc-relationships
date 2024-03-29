package io.github.joselion.springr2dbcrelationships.models.phone;

import java.time.LocalDateTime;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.annotation.Id;

import io.github.joselion.springr2dbcrelationships.annotations.OneToOne;
import io.github.joselion.springr2dbcrelationships.models.details.Details;
import lombok.With;
import lombok.experimental.WithBy;

@With
@WithBy
public record Phone(
  @Id @Nullable UUID id,
  LocalDateTime createdAt,
  String number,
  @OneToOne @Nullable Details details
) {

  public static Phone empty() {
    return new Phone(
      null,
      LocalDateTime.now(),
      "",
      null
    );
  }

  public static Phone of(final String number) {
    return Phone.empty().withNumber(number);
  }
}
