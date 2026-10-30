package org.cavalodetroia.ctr.application.wiring;

import org.cavalodetroia.ctr.domain.event.EventService;
import org.cavalodetroia.ctr.domain.event.port.out.EventPublisher;
import org.cavalodetroia.ctr.domain.event.port.out.EventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventWiring {

    @Bean
    public EventService eventService(final EventRepository eventRepository, final EventPublisher eventPublisher) {
        return new EventService(eventRepository, eventPublisher);
    }
}
