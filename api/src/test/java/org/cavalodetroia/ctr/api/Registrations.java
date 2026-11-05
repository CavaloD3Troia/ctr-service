package org.cavalodetroia.ctr.api;

import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.CameraRegistration;
import org.cavalodetroia.ctr.domain.model.upload.RegistrationResult;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.model.venue.RequestedVenue;

import java.time.Instant;
import java.util.UUID;

/**
 * Registration results as the domain returns them: 40 parts, an 11-a-side pitch.
 */
public final class Registrations {

    public static final UUID EVENT_ID = UUID.fromString("0b5c3c38-4a8e-4f53-8c0e-5b0b8d8f2a11");
    public static final UUID SESSION_ID = UUID.fromString("019a5b2e-7f3c-7000-8000-000000000001");
    public static final String BUCKET = "cdt-videos";
    public static final int PART_COUNT = 40;
    private static final Instant NOW = Instant.parse("2026-11-05T10:00:00Z");

    private Registrations() {
    }

    public static RegistrationResult leftOpened() {
        return UploadSession.open(SESSION_ID, EVENT_ID,
                new CameraRegistration(Camera.LEFT, PART_COUNT, new RequestedVenue("FOOTBALL_11V11", null, null)),
                BUCKET, NOW);
    }

    public static RegistrationResult rightJoinedLeft() {
        return leftOpened().session().join(new CameraRegistration(Camera.RIGHT, PART_COUNT, null), BUCKET, NOW);
    }
}
