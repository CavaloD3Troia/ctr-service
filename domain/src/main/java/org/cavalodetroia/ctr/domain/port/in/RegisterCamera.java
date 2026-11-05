package org.cavalodetroia.ctr.domain.port.in;

import org.cavalodetroia.ctr.domain.exception.CameraSetConflictException;
import org.cavalodetroia.ctr.domain.exception.EventNotFoundException;
import org.cavalodetroia.ctr.domain.exception.PartCountMismatchException;
import org.cavalodetroia.ctr.domain.exception.VenueSpecException;
import org.cavalodetroia.ctr.domain.model.upload.CameraRegistration;
import org.cavalodetroia.ctr.domain.model.upload.RegistrationResult;

import java.util.UUID;

/**
 * A camera registers to upload an event's clips: it opens the event's upload session when there is no open one,
 * otherwise it joins it.
 */
public interface RegisterCamera {

    /**
     * @param eventId      the event filmed
     * @param registration the camera and what it will upload
     * @return the session as left by the registration, and the camera registered
     * @throws EventNotFoundException      when the event is unknown
     * @throws VenueSpecException          when the camera opens the session without a venue
     * @throws CameraSetConflictException  when the camera is already registered or does not fit the session's cameras
     * @throws PartCountMismatchException  when the camera brings a different number of parts than the session's
     */
    RegistrationResult register(UUID eventId, CameraRegistration registration);
}
