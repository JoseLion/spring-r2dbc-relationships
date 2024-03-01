package io.github.joselion.springr2dbcrelationships.models.paper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.annotation.Id;

import io.github.joselion.springr2dbcrelationships.annotations.ManyToMany;
import io.github.joselion.springr2dbcrelationships.models.author.Author;
import lombok.With;
import lombok.experimental.WithBy;

@With
@WithBy
public record Paper(
  @Id @Nullable UUID id,
  LocalDateTime createdAt,
  String title,
  @ManyToMany List<Author> authors
) {

  public static Paper empty() {
    return new Paper(
      null,
      LocalDateTime.now(),
      "",
      List.of()
    );
  }

  public static Paper of(final String title) {
    return Paper.empty().withTitle(title);
  }
}
