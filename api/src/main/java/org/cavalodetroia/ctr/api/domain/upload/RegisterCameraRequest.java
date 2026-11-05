package org.cavalodetroia.ctr.api.domain.upload;

import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(description = "SINGLE films the whole pitch; LEFT and RIGHT film one half each",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Camera camera,

        @Schema(description = "How many parts the match is cut into; every camera of the session sends the same",
                example = "40", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Min(CameraRegistration.MIN_PART_COUNT) @Max(CameraRegistration.MAX_PART_COUNT) Integer partCount,

        @Schema(description = "Media type of the clips, a video/* type", example = "video/mp4",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @Pattern(regexp = VIDEO_MEDIA_TYPE, message = "must be a video/* media type") String contentType,

        @Schema(description = "The pitch. Required on the first camera of a session; ignored (and logged) on later "
                + "ones")
        @Valid VenueRequest venue) {

    // RFC 6838 restricted-name characters, case-insensitive, no parameters
    static final String VIDEO_MEDIA_TYPE = "(?i)video/[a-z0-9][a-z0-9!#$&^_.+-]{0,126}";
}
