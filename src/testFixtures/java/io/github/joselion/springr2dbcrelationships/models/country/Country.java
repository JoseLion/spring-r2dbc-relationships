package io.github.joselion.springr2dbcrelationships.models.country;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.annotation.Id;

import io.github.joselion.springr2dbcrelationships.annotations.OneToMany;
import io.github.joselion.springr2dbcrelationships.models.city.City;
import io.github.joselion.springr2dbcrelationships.models.town.Town;
import lombok.With;
import lombok.experimental.WithBy;

@With
@WithBy
public record Country(
  @Id @Nullable UUID id,
  LocalDateTime createdAt,
  String name,
  @OneToMany List<City> cities,
  @OneToMany(keepOrphans = true) List<Town> towns
) {

  public static Country empty() {
    return new Country(
      null,
      LocalDateTime.now(),
      "",
      List.of(),
      List.of()
    );
  }

  public static Country of(final String name) {
    return Country.empty().withName(name);
  }
}
