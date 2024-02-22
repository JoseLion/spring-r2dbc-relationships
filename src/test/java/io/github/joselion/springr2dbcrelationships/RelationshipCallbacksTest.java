package io.github.joselion.springr2dbcrelationships;

import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import io.github.joselion.testing.annotations.UnitTest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@UnitTest class RelationshipCallbacksTest {

  @Nested class onAfterConvert {
    @Nested class when_an_entity_goes_through_the_callback {
      @Test void adds_the_entity_type_name_to_publisher_context() {
        final var entity = TestEntity.of("Bono");
        final var table = SqlIdentifier.unquoted("test_entity");
        final var template = mock(R2dbcEntityTemplate.class);
        final var callbacks = new RelationshipCallbacks<>(template);
        final var publisher = callbacks.onAfterConvert(entity, table);

        Mono.from(publisher)
          .as(StepVerifier::create)
          .expectAccessibleContext()
          .contains(RelationshipCallbacks.class, List.of(TestEntity.class.getName()))
          .then()
          .expectNext(entity)
          .verifyComplete();
      }
    }
  }

  record TestEntity(@Nullable UUID id, String name) {

    public static TestEntity of(final String name) {
      return new TestEntity(null, name);
    }
  }
}
