package io.github.joselion.springr2dbcrelationships;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

@SpringBootApplication
@EnableR2dbcRepositories
public class FixtureApplication {

  public static void main(final String[] args) {
    SpringApplication.run(FixtureApplication.class, args);
  }
}
