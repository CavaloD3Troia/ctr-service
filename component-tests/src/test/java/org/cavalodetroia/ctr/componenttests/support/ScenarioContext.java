package org.cavalodetroia.ctr.componenttests.support;

import io.cucumber.spring.ScenarioScope;

import java.util.UUID;

/**
 * What a scenario remembers between steps that live in different classes. A fresh one per scenario.
 */
@ScenarioScope
public class ScenarioContext {

    private UUID eventId;

    public UUID eventId() {
        if (eventId == null) {
            throw new IllegalStateException("No event was created in this scenario");
        }
        return eventId;
    }

    public void eventId(final UUID eventId) {
        this.eventId = eventId;
    }
}
