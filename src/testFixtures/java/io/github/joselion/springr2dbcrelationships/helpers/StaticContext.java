package io.github.joselion.springr2dbcrelationships.helpers;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public record StaticContext(ApplicationContext context) implements InitializingBean {

  private static StaticContext holder;

  public static <T> T getBean(final Class<T> beanType) {
    return holder.context.getBean(beanType);
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    StaticContext.holder = this; // NOSONAR
  }
}
