package io.github.joselion.springr2dbcrelationships;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

import lombok.extern.slf4j.Slf4j;

/**
 * R2DBC Relationships auto-configuration.
 */
@Slf4j
@Configuration
public class R2dbcRelationshipsAutoConfiguration {

  /**
   * Default configuration constructor.
   */
  protected R2dbcRelationshipsAutoConfiguration() {
    log.info("R2DBC Relationships auto-configuration loaded.");
  }

  /**
   * Creates the {@link R2dbcRelationshipsCallbacks} bean.
   *
   * @param <T> the type of the entity in the callback
   * @param template the r2dbc entity template
   * @param context the Spring application context
   * @return the relationship callbacks bean
   */
  @Bean
  public <T> R2dbcRelationshipsCallbacks<T> relationshipsCallbacks(
    final @Lazy R2dbcEntityTemplate template,
    final ApplicationContext context
  ) {
    return new R2dbcRelationshipsCallbacks<>(template, context);
  }
}
