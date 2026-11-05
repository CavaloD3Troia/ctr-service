package org.cavalodetroia.ctr.domain.model.upload;

import java.util.Objects;

/**
 * What a camera registration left behind.
 *
 * @param session      the session after the registration, with every camera registered so far
 * @param camera       the camera just registered
 * @param venueIgnored whether the camera sent a venue that was ignored because an earlier camera had set it
 */
public record RegistrationResult(UploadSession session, UploadCamera camera, boolean venueIgnored) {

    public RegistrationResult {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(camera, "camera");
    }
}
