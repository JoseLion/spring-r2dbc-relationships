package io.github.joselion.springr2dbcrelationships.models.book;

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
public record Book(
  @Id @Nullable UUID id,
  LocalDateTime createdAt,
  String title,
  @ManyToMany List<Author> authors
) {

  public static Book empty() {
    return new Book(
      null,
      LocalDateTime.now(),
      "",
      List.of()
    );
  }

  public static Book of(final String title) {
    return Book.empty().withTitle(title);
  }
}
