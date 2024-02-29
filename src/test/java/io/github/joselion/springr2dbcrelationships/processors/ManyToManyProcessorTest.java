package io.github.joselion.springr2dbcrelationships.processors;

import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.function.TupleUtils.consumer;
import static reactor.function.TupleUtils.function;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.joselion.springr2dbcrelationships.models.author.Author;
import io.github.joselion.springr2dbcrelationships.models.author.AuthorRepository;
import io.github.joselion.springr2dbcrelationships.models.authorbook.AuthorBook;
import io.github.joselion.springr2dbcrelationships.models.authorbook.AuthorBookRepository;
import io.github.joselion.springr2dbcrelationships.models.book.Book;
import io.github.joselion.springr2dbcrelationships.models.book.BookRepository;
import io.github.joselion.springr2dbcrelationships.models.paper.Paper;
import io.github.joselion.springr2dbcrelationships.models.paper.PaperRepository;
import io.github.joselion.testing.annotations.IntegrationTest;
import io.github.joselion.testing.transactional.TxStepVerifier;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@IntegrationTest class ManyToManyProcessorTest {

  @Autowired
  private AuthorRepository authorRepo;

  @Autowired
  private BookRepository bookRepo;

  @Autowired
  private AuthorBookRepository authorBookRepo;

  @Autowired
  private PaperRepository paperRepo;

  private final Author tolkien = Author.of("J. R. R. Tolkien");

  private final String fellowship = "The Fellowship of the Ring";

  private final String twoTowers = "The Two Towers";

  private final String kingsReturn = "The Return of the King";

  private Author nielDegreese = Author.of("Niel Degreese Tyson");

  private String blackHoles = "Super Masive Black holes";

  private String superNovas = "Effects of Super Novas";

  private String wormHoles = "Theoretical Worm Holes";

  @Nested class populate {
    @Test void populates_the_field_with_the_joined_entities() throws InterruptedException {
      final var chrisTolkin = Author.of("Christopher Tolkien");
      final var ringsWar = "The War of the Ring";

      tolkienTrilogy()
        .delayUntil(function((books, author) ->
          Mono.just(ringsWar)
            .map(Book::of)
            .flatMap(bookRepo::save)
            .map(book -> List.of(books.get(1), book))
            .zipWith(authorRepo.save(chrisTolkin))
            .delayUntil(function((chrisBooks, chris) ->
              Flux.fromIterable(chrisBooks)
                .map(cb -> AuthorBook.of(chris.id(), cb.id()))
                .publish(authorBookRepo::saveAll)
            ))
        ))
        .map(function((books, author) -> author.id()))
        .flatMap(authorRepo::findById)
        .as(TxStepVerifier::withRollback)
        .assertNext(found -> {
          assertThat(found.books())
            .allSatisfy(book -> assertThat(book.authors()).isNotNull())
            .extracting(Book::title)
            .containsExactly(kingsReturn, twoTowers, fellowship);

          final var tolkienKingsReturn = found.books().get(0);
          final var tolkienTwoTowers = found.books().get(1);
          final var tolkienFellowship = found.books().get(2);
          assertThat(tolkienKingsReturn.authors())
            .allSatisfy(author -> assertThat(author.books()).isNull())
            .extracting(Author::name)
            .containsExactly(tolkien.name());
          assertThat(tolkienFellowship.authors())
            .allSatisfy(author -> assertThat(author.books()).isNull())
            .extracting(Author::name)
            .containsExactly(tolkien.name());
          assertThat(tolkienTwoTowers.authors())
            .extracting(Author::name)
            .containsExactly(chrisTolkin.name(), tolkien.name());

          final var twoTowersChris = tolkienTwoTowers.authors().get(0);
          final var twoTowersTolkien = tolkienTwoTowers.authors().get(1);
          assertThat(twoTowersTolkien.books()).isNull();
          assertThat(twoTowersChris.books())
            .extracting(Book::title)
            .containsExactly(ringsWar, twoTowers);

          final var chrisRingsWar = twoTowersChris.books().get(0);
          final var chrisTwoTowers = twoTowersChris.books().get(1);
          assertThat(chrisTwoTowers.authors()).isNull();
          assertThat(chrisRingsWar.authors())
            .extracting(Author::name)
            .containsExactly(chrisTolkin.name());
        })
        .verifyComplete();
    }
  }

  @Nested class persist {
    @Nested class when_there_are_no_orphan_items {
      @Nested class and_the_items_are_new {
        @Test void creates_the_items_and_adds_them_to_the_join_table() {
          Flux.just(fellowship, twoTowers, kingsReturn)
            .map(Book::of)
            .collectList()
            .map(tolkien::withBooks)
            .flatMap(authorRepo::save)
            .zipWhen(saved ->
              authorBookRepo
                .findByAuthorId(saved.id())
                .collectList()
            )
            .as(TxStepVerifier::withRollback)
            .assertNext(consumer((author, joins) -> {
              assertThat(author.books())
                .allSatisfy(book -> assertThat(book.id()).isNotNull())
                .extracting(Book::title)
                .containsExactly(fellowship, twoTowers, kingsReturn);
              assertThat(joins)
                .hasSameSizeAs(author.books())
                .allSatisfy(join -> assertThat(join.authorId()).isEqualTo(author.id()))
                .extracting(AuthorBook::bookId)
                .containsAll(author.books().stream().map(Book::id).toList());
            }))
            .verifyComplete();
        }
      }

      @Nested class and_the_items_already_exist {
        @Test void updates_the_items() {
          tolkienTrilogy()
            .flatMap(function((books, author) ->
              Flux.fromIterable(books)
                .map(book -> book.withTitleBy(String::toUpperCase))
                .collectList()
                .map(author::withBooks)
            ))
            .flatMap(authorRepo::save)
            .zipWhen(author ->
              Mono.just(author)
                .map(Author::id)
                .flatMapMany(authorBookRepo::findByAuthorId)
                .map(AuthorBook::bookId)
                .publish(bookRepo::findAllById)
                .collectList()
            )
            .as(TxStepVerifier::withRollback)
            .assertNext(consumer((author, books) -> {
              assertThat(author.books())
                .allSatisfy(book -> assertThat(book.id()).isNotNull())
                .extracting(Book::title)
                .contains(
                  fellowship.toUpperCase(),
                  twoTowers.toUpperCase(),
                  kingsReturn.toUpperCase()
                );
              assertThat(books)
                .hasSameSizeAs(author.books())
                .allSatisfy(book ->
                  assertThat(author.books())
                    .extracting(Book::id)
                    .containsAnyOf(book.id())
                )
                .extracting(Book::title)
                .contains(
                  fellowship.toUpperCase(),
                  twoTowers.toUpperCase(),
                  kingsReturn.toUpperCase()
                );
            }))
            .verifyComplete();
        }
      }
    }

    @Nested class when_there_are_orphan_items {
      @Nested class and_the_deleteOrphans_option_is_false {
        @Test void persists_the_items_and_delete_the_orphan_join_links() {
          tolkienTrilogy()
            .flatMap(function((books, author) ->
              Flux.fromIterable(books)
                .filter(book -> book.title().length() > 15)
                .collectList()
                .map(author::withBooks)
            ))
            .flatMap(authorRepo::save)
            .zipWhen(author ->
              Mono.just(author)
                .map(Author::id)
                .flatMapMany(authorBookRepo::findByAuthorId)
                .collectList()
            )
            .zipWhen(
              x -> bookRepo.findAll().collectList(),
              (t, books) -> Tuples.of(t.getT1(), t.getT2(), books))
            .as(TxStepVerifier::withRollback)
            .assertNext(consumer((author, joins, books) -> {
              assertThat(author.books())
                .allSatisfy(book -> assertThat(book.id()).isNotNull())
                .extracting(Book::title)
                .contains(fellowship, kingsReturn);
              assertThat(joins)
                .hasSameSizeAs(author.books())
                .allSatisfy(join -> assertThat(join.authorId()).isEqualTo(author.id()))
                .extracting(AuthorBook::bookId)
                .containsAll(author.books().stream().map(Book::id).toList());
              assertThat(books)
                .allSatisfy(book -> assertThat(book.id()).isNotNull())
                .extracting(Book::title)
                .containsExactly(fellowship, twoTowers, kingsReturn);
            }))
            .verifyComplete();
        }
      }

      @Nested class and_the_deleteOrphans_option_is_true {
        @Test void persists_the_items_deletes_the_orphan_join_links_and_delete_the_orphan_items() {
          Flux.just(blackHoles, superNovas, wormHoles)
            .map(Paper::of)
            .delayElements(Duration.ofMillis(1))
            .collectList()
            .map(nielDegreese::withPapers)
            .flatMap(authorRepo::save)
            .map(saved ->
              saved.withPapersBy(papers ->
                papers.stream()
                  .filter(not(paper -> paper.title().equals(superNovas)))
                  .toList()
              )
            )
            .flatMap(authorRepo::save)
            .map(Author::id)
            .flatMap(authorRepo::findById)
            .zipWhen(x -> paperRepo.findAll().collectList())
            .as(TxStepVerifier::withRollback)
            .assertNext(consumer((author, papers) -> {
              assertThat(author.papers())
                .extracting(Paper::title)
                .containsExactly(wormHoles, blackHoles);
              assertThat(papers)
                .extracting(Paper::title)
                .containsExactly(blackHoles, wormHoles);
            }))
            .verifyComplete();
        }
      }
    }

    @Nested class when_all_the_items_are_left_orphan {
      @Nested class and_the_deleteOrphans_option_is_false {
        @Test void deletes_all_the_orphan_join_links() {
          tolkienTrilogy()
            .map(function((books, author) -> author.withBooks(List.of())))
            .flatMap(authorRepo::save)
            .zipWhen(author ->
              Mono.just(author)
                .map(Author::id)
                .flatMapMany(authorBookRepo::findByAuthorId)
                .collectList()
            )
            .zipWhen(
              x -> bookRepo.findAll().collectList(),
              (t, books) -> Tuples.of(t.getT1(), t.getT2(), books)
            )
            .as(TxStepVerifier::withRollback)
            .assertNext(consumer((author, joins, books) -> {
              assertThat(author.books()).isEmpty();
              assertThat(joins).isEmpty();
              assertThat(books)
                .allSatisfy(book -> assertThat(book.id()).isNotNull())
                .extracting(Book::title)
                .containsExactly(fellowship, twoTowers, kingsReturn);
            }))
            .verifyComplete();
        }
      }

      @Nested class and_the_deleteOrphans_option_is_true {
        @Test void deletes_all_the_orphan_join_links_and_all_the_orphan_items() {
          Flux.just(blackHoles, superNovas, wormHoles)
            .map(Paper::of)
            .collectList()
            .map(nielDegreese::withPapers)
            .flatMap(authorRepo::save)
            .map(saved -> saved.withPapers(List.of()))
            .flatMap(authorRepo::save)
            .map(Author::id)
            .flatMap(authorRepo::findById)
            .zipWhen(x -> paperRepo.findAll().collectList())
            .as(TxStepVerifier::withRollback)
            .assertNext(consumer((author, papers) -> {
              assertThat(author.papers()).isEmpty();
              assertThat(papers).isEmpty();
            }))
            .verifyComplete();
        }
      }
    }

    @Nested class when_the_items_field_is_null {
      @Test void ignores_the_field_associations() {
        tolkienTrilogy()
          .map(function((books, author) -> author.withBooks(null)))
          .flatMap(authorRepo::save)
          .zipWhen(author ->
            Mono.just(author)
              .map(Author::id)
              .flatMapMany(authorBookRepo::findByAuthorId)
              .collectList()
          )
          .zipWhen(
            x -> bookRepo.findAll().collectList(),
            (t, books) -> Tuples.of(t.getT1(), t.getT2(), books)
          )
          .as(TxStepVerifier::withRollback)
          .assertNext(consumer((author, joins, books) -> {
            assertThat(author.books()).isNull();
            assertThat(joins).hasSameSizeAs(books);
            assertThat(books)
              .allSatisfy(book -> assertThat(book.id()).isNotNull())
              .extracting(Book::title)
              .containsExactly(fellowship, twoTowers, kingsReturn);
          }))
          .verifyComplete();
      }
    }
  }

  private Mono<Tuple2<List<Book>, Author>> tolkienTrilogy() {
    return Flux.just(fellowship, twoTowers, kingsReturn)
      .map(Book::of)
      .delayElements(Duration.ofMillis(1))
      .publish(bookRepo::saveAll)
      .collectList()
      .zipWith(authorRepo.save(tolkien))
      .delayUntil(function((books, author) ->
        Flux.fromIterable(books)
          .map(book -> AuthorBook.of(author.id(), book.id()))
          .publish(authorBookRepo::saveAll)
      ));
  }
}
