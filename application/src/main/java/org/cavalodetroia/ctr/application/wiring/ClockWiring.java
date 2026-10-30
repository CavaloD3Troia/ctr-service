package org.cavalodetroia.ctr.application.wiring;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockWiring {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
