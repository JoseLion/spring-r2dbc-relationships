package io.github.joselion.springr2dbcrelationships.models.mobile;

import java.time.LocalDateTime;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.annotation.Id;

import io.github.joselion.springr2dbcrelationships.annotations.OneToOne;
import io.github.joselion.springr2dbcrelationships.models.features.Features;
import lombok.With;

@With
public record Mobile(
  @Id @Nullable UUID id,
  LocalDateTime createdAt,
  String number,
  @OneToOne(keepOrphan = true) @Nullable Features features
) {

  public static Mobile empty() {
    return new Mobile(
      null,
      LocalDateTime.now(),
      "",
      null
    );
  }

  public static Mobile of(final String number) {
    return Mobile.empty().withNumber(number);
  }
}
