package org.cavalodetroia.ctr.api.domain.upload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.cavalodetroia.ctr.api.domain.venue.VenueRequest;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.CameraRegistration;

/**
 * Body of {@code POST /api/v1/events/{eventId}/upload-session/cameras}.
 *
 * @param camera      which camera registers
 * @param partCount   how many parts the match is cut into, the same for every camera of the session
 * @param contentType the media type of the clips, a {@code video/*} type
 * @param venue       the pitch; required on the first camera, ignored on later ones
 */
public record RegisterCameraRequest(
        @NotNull Camera camera,
        @NotNull @Min(CameraRegistration.MIN_PART_COUNT) @Max(CameraRegistration.MAX_PART_COUNT) Integer partCount,
        @NotNull @Pattern(regexp = VIDEO_MEDIA_TYPE, message = "must be a video/* media type") String contentType,
        @Valid VenueRequest venue) {

    // RFC 6838 restricted-name characters, case-insensitive, no parameters
    static final String VIDEO_MEDIA_TYPE = "(?i)video/[a-z0-9][a-z0-9!#$&^_.+-]{0,126}";
}
