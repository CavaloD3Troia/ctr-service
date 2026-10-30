package org.cavalodetroia.ctr.domain.job;

import java.util.UUID;

public class JobStatusNotFoundException extends RuntimeException {

    public JobStatusNotFoundException(final UUID eventId) {
        super("No job status for event " + eventId + " yet");
    }
}
