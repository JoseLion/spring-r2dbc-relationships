package io.github.joselion.springr2dbcrelationships.models.authorbook;

import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.annotation.Id;

public record AuthorBook(
  @Id @Nullable UUID id,
  UUID authorId,
  UUID bookId
) {

  public static AuthorBook of(final UUID authorId, final UUID bookId) {
    return new AuthorBook(null, authorId, bookId);
  }
}
