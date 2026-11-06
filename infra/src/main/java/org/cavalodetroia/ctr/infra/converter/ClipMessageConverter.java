package org.cavalodetroia.ctr.infra.converter;

import com.google.protobuf.Message;
import org.cavalodetroia.contracts.clip.v1.Clip;
import org.cavalodetroia.contracts.clip.v1.ClipPairUploaded;
import org.cavalodetroia.contracts.clip.v1.ClipReady;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.ObjectKeys;
import org.cavalodetroia.ctr.domain.model.upload.UploadCamera;
import org.cavalodetroia.ctr.domain.model.upload.UploadPart;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.model.upload.Window;
import org.cavalodetroia.ctr.domain.model.venue.VenueFormat;
import org.cavalodetroia.ctr.domain.model.venue.VenueSpec;
import org.springframework.stereotype.Component;

/**
 * A completed part and the message that hands it over to extraction: {@link ClipPairUploaded} for a LEFT and RIGHT
 * session, {@link ClipReady} for a SINGLE one. The ids, the part index and the part count come from the session; the
 * window is the part's (the overlap of the two clips for LEFT and RIGHT, the clip's own for SINGLE); each clip is the
 * camera's {@code prefix} with its key filled in, its confirmed size, and the content type the camera registered.
 */
@Component
public class ClipMessageConverter {

    /**
     * @param session   the session as stored
     * @param partIndex the part, 0-based, {@code COMPLETE}
     * @return a {@link ClipPairUploaded} or a {@link ClipReady}, by the session's expected cameras
     * @throws IllegalArgumentException when the part is not {@code COMPLETE}, so has no window to publish
     */
    public Message toMessage(final UploadSession session, final int partIndex) {
        final UploadPart part = session.part(partIndex);
        if (!part.hasWindow()) {
            throw new IllegalArgumentException("Part " + partIndex + " of session '" + session.id() + "' is "
                    + part.status() + ": only a COMPLETE part has a message");
        }
        final Window window = part.window();
        if (session.expectedCameras().contains(Camera.SINGLE)) {
            return ClipReady.newBuilder()
                    .setSessionId(session.id().toString())
                    .setEventId(session.eventId().toString())
                    .setPartIndex(partIndex)
                    .setPartCount(session.partCount())
                    .setStartMs(window.startMs())
                    .setDurationMs(window.durationMs())
                    .setClip(toClip(session, Camera.SINGLE, partIndex))
                    .setVenue(toVenue(session.venue()))
                    .build();
        }
        return ClipPairUploaded.newBuilder()
                .setSessionId(session.id().toString())
                .setEventId(session.eventId().toString())
                .setPartIndex(partIndex)
                .setPartCount(session.partCount())
                .setStartMs(window.startMs())
                .setDurationMs(window.durationMs())
                .setLeft(toClip(session, Camera.LEFT, partIndex))
                .setRight(toClip(session, Camera.RIGHT, partIndex))
                .setVenue(toVenue(session.venue()))
                .build();
    }

    /**
     * @return {@code <session_id>:<part_index>}, the key of every message about the part, whatever its topic
     */
    public String toKey(final UploadSession session, final int partIndex) {
        return session.id() + ":" + partIndex;
    }

    /**
     * @return the sport extraction needs for the format: {@code futsal} for {@code FUTSAL_5V5}, {@code football} for
     *         the {@code FOOTBALL_*} formats
     */
    static String sport(final VenueFormat format) {
        // Not a switch: one over an enum compiles to a synthetic ClipMessageConverter$1, which CONVERTERS_ARE_NAMED_
        // CONVERTER rejects. ClipMessageConverterTest covers every format
        return format == VenueFormat.FUTSAL_5V5 ? "futsal" : "football";
    }

    private static Clip toClip(final UploadSession session, final Camera camera, final int partIndex) {
        final UploadCamera registered = session.camera(camera);
        return Clip.newBuilder()
                .setUri(ObjectKeys.clipUri(registered.prefix(), partIndex))
                .setSizeBytes(session.clip(camera, partIndex).confirmation().sizeBytes())
                .setContentType(registered.contentType())
                .build();
    }

    // The proto's VenueSpec shares its simple name with the domain's, so it is spelled out
    private static org.cavalodetroia.contracts.venue.v1.VenueSpec toVenue(final VenueSpec venue) {
        return org.cavalodetroia.contracts.venue.v1.VenueSpec.newBuilder()
                .setSport(sport(venue.format()))
                .setFormat(venue.format().name())
                .setLengthM((float) venue.lengthM())
                .setWidthM((float) venue.widthM())
                .build();
    }
}
