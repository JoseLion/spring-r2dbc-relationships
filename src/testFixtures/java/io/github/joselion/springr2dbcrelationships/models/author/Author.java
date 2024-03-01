package io.github.joselion.springr2dbcrelationships.models.author;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.springframework.data.annotation.Id;

import io.github.joselion.springr2dbcrelationships.annotations.ManyToMany;
import io.github.joselion.springr2dbcrelationships.models.book.Book;
import io.github.joselion.springr2dbcrelationships.models.paper.Paper;
import lombok.With;
import lombok.experimental.WithBy;

@With
@WithBy
public record Author(
  @Id @Nullable UUID id,
  LocalDateTime createdAt,
  String name,
  @ManyToMany List<Book> books,
  @ManyToMany(deleteOrphans = true) @Nullable List<Paper> papers,
  @ManyToMany(linkOnly = true) @Nullable List<Paper> studies
) {

  public static Author empty() {
    return new Author(
      null,
      LocalDateTime.now(),
      "",
      List.of(),
      null,
      null
    );
  }

  public static Author of(final String name) {
    return Author.empty().withName(name);
  }
}
