package org.cavalodetroia.ctr.api.domain.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import org.cavalodetroia.ctr.api.domain.venue.VenueResponse;
import org.cavalodetroia.ctr.domain.model.upload.SessionStatus;

import java.util.List;
import java.util.UUID;

/**
 * Body of {@code GET /api/v1/upload-sessions/{sessionId}}: where the upload stands, camera by camera and part by
 * part.
 *
 * @param sessionId      the session
 * @param eventId        the event filmed
 * @param status         the session's status
 * @param partCount      how many parts the match is cut into
 * @param partsPublished how many parts were published for extraction
 * @param venue          the pitch
 * @param cameras        the registered cameras, LEFT before RIGHT
 * @param parts          every part, by index from 0 to {@code partCount - 1}
 */
public record UploadSessionResponse(
        @Schema(description = "The upload session (UUIDv7)")
        UUID sessionId,

        UUID eventId,

        SessionStatus status,

        @Schema(description = "How many parts the match is cut into", example = "40")
        int partCount,

        @Schema(description = "How many parts were published for extraction", example = "2")
        int partsPublished,

        VenueResponse venue,

        @Schema(description = "The registered cameras, LEFT before RIGHT")
        List<CameraResponse> cameras,

        @Schema(description = "Every part, by index from 0 to partCount - 1")
        List<PartResponse> parts) {
}
