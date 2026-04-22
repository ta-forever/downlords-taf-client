package com.faforever.client.config;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

/**
 * This configuration has to be imported by other configurations and should only contain beans that are necessary to run
 * the application.
 */
@Slf4j
@Configuration
public class BaseConfig {

  @Bean
  ReloadableResourceBundleMessageSource messageSource() {
    ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
    messageSource.setDefaultEncoding("utf-8");
    messageSource.setBasename("classpath:i18n/messages");
    messageSource.setFallbackToSystemLocale(false);
    return messageSource;
  }

  @Bean
  EventBus eventBus() {
    EventBus bus = new EventBus((exception, context) -> log.warn("Exception in '{}#{}' while handling event: {}",
        context.getSubscriber().getClass(), context.getSubscriberMethod().getName(), context.getEvent(), exception));
    bus.register(new DeadEventHandler());
    return bus;
  }

  private static class DeadEventHandler {
    @Subscribe
    public void onDeadEvent(DeadEvent deadEvent) {
      // Debug, not warn: Spring lazily instantiates controllers on
      // first page navigation, so any eventBus.post() before that page
      // has been opened produces a DeadEvent. E.g. a server-driven
      // RefreshTournamentsEvent arrives at login but the Tournaments
      // tab hasn't been opened yet, so no subscriber is registered.
      // That's expected behaviour, not an error.
      Object unhandledEvent = deadEvent.getEvent();
      log.debug("No event handler registered for event of type '{}'", unhandledEvent.getClass().getSimpleName());
    }
  }
}
