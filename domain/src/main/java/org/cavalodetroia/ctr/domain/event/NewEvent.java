package org.cavalodetroia.ctr.domain.event;

/**
 * What a client provides to create an event.
 */
public record NewEvent(String name, String sport) {

    public NewEvent {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is mandatory");
        }
        if (sport == null || sport.isBlank()) {
            throw new IllegalArgumentException("sport is mandatory");
        }
    }
}
