package org.cavalodetroia.ctr.application.wiring;

import org.cavalodetroia.ctr.domain.event.EventService;
import org.cavalodetroia.ctr.domain.event.port.out.EventPublisher;
import org.cavalodetroia.ctr.domain.event.port.out.EventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class EventWiring {

    /**
     * Serves both inbound ports, {@code CreateEvent} and {@code GetEvent}.
     */
    @Bean
    public EventService eventService(final EventRepository eventRepository,
                                     final EventPublisher eventPublisher,
                                     final Clock clock) {
        return new EventService(eventRepository, eventPublisher, clock);
    }
}
