package io.github.joselion.springr2dbcrelationships.processors;

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

  private final Author tolkien = Author.of("J. R. R. Tolkien");

  private final String fellowship = "The Fellowship of the Ring";

  private final String twoTowers = "The Two Towers";

  private final String kingsReturn = "The Return of the King";

  @Nested class populate {
    @Test void populates_the_field_with_the_joined_entities() throws InterruptedException {
      tolkienTrilogy()
        .map(function((books, author) -> author.id()))
        .flatMap(authorRepo::findById)
        .as(TxStepVerifier::withRollback)
        .assertNext(author -> {
          assertThat(author.books())
            .extracting(Book::title)
            .containsExactly(kingsReturn, twoTowers, fellowship);
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

    @Nested class when_all_the_items_are_left_orphan {
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

    @Nested class when_the_items_field_is_null {
      @Test void deletes_all_the_orphan_join_links() {
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
  }

  private Mono<Tuple2<List<Book>, Author>> tolkienTrilogy() {
    return Flux.just(fellowship, twoTowers, kingsReturn)
      .delayElements(Duration.ofMillis(10))
      .map(Book::of)
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
