package io.github.joselion.springr2dbcrelationships.models.features;

import java.time.LocalDateTime;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.annotation.Id;

import io.github.joselion.springr2dbcrelationships.annotations.OneToOne;
import io.github.joselion.springr2dbcrelationships.models.mobile.Mobile;
import lombok.With;
import lombok.experimental.WithBy;

@With
@WithBy
public record Features(
  @Id @Nullable UUID id,
  LocalDateTime createdAt,
  @Nullable UUID mobileId,
  @Nullable @OneToOne(keepOrphan = true) Mobile mobile,
  String technology
) {

  public static Features empty() {
    return new Features(
      null,
      LocalDateTime.now(),
      null,
      null,
      ""
    );
  }

  public static Features of(final String tecnology) {
    return Features.empty().withTechnology(tecnology);
  }
}
