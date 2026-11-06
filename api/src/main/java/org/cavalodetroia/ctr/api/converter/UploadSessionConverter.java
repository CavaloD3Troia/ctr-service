package org.cavalodetroia.ctr.api.converter;

import org.cavalodetroia.ctr.api.domain.upload.CameraResponse;
import org.cavalodetroia.ctr.api.domain.upload.CameraSummaryResponse;
import org.cavalodetroia.ctr.api.domain.upload.ClipResponse;
import org.cavalodetroia.ctr.api.domain.upload.CompleteClipRequest;
import org.cavalodetroia.ctr.api.domain.upload.CompleteClipResponse;
import org.cavalodetroia.ctr.api.domain.upload.PartResponse;
import org.cavalodetroia.ctr.api.domain.upload.PartSummary;
import org.cavalodetroia.ctr.api.domain.upload.RegisterCameraRequest;
import org.cavalodetroia.ctr.api.domain.upload.RegisterCameraResponse;
import org.cavalodetroia.ctr.api.domain.upload.SessionSummary;
import org.cavalodetroia.ctr.api.domain.upload.UploadSessionResponse;
import org.cavalodetroia.ctr.api.domain.venue.VenueRequest;
import org.cavalodetroia.ctr.api.domain.venue.VenueResponse;
import org.cavalodetroia.ctr.domain.exception.VenueSpecException;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.CameraRegistration;
import org.cavalodetroia.ctr.domain.model.upload.ClipCompletion;
import org.cavalodetroia.ctr.domain.model.upload.ClipConfirmation;
import org.cavalodetroia.ctr.domain.model.upload.ObjectKeys;
import org.cavalodetroia.ctr.domain.model.upload.RegistrationResult;
import org.cavalodetroia.ctr.domain.model.upload.UploadCamera;
import org.cavalodetroia.ctr.domain.model.upload.UploadClip;
import org.cavalodetroia.ctr.domain.model.upload.UploadPart;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.model.venue.RequestedVenue;
import org.cavalodetroia.ctr.domain.model.venue.VenueSpec;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class UploadSessionConverter {

    /**
     * The venue goes to the domain as sent: only the first camera's is used, and its format is checked there.
     *
     * @throws VenueSpecException when the venue has only one of its measures
     */
    public CameraRegistration toRegistration(final RegisterCameraRequest request) {
        final VenueRequest venue = request.venue();
        return new CameraRegistration(request.camera(), request.partCount(), request.contentType(),
                venue == null ? null : new RequestedVenue(venue.format(), venue.lengthM(), venue.widthM()));
    }

    public RegisterCameraResponse toResponse(final RegistrationResult result) {
        final UploadSession session = result.session();
        final UploadCamera camera = result.camera();
        return new RegisterCameraResponse(
                session.id(),
                session.eventId(),
                session.status(),
                camera.camera(),
                camera.partCount(),
                camera.prefix(),
                ObjectKeys.CLIP_KEY_TEMPLATE,
                cameras(session));
    }

    public ClipConfirmation toConfirmation(final CompleteClipRequest request) {
        return new ClipConfirmation(request.sizeBytes(), request.etag(), request.startMs(), request.durationMs());
    }

    /**
     * Built from the session as stored after the confirmation, never from the request, so a repeated confirmation
     * gets the same body.
     */
    public CompleteClipResponse toResponse(final ClipCompletion completion) {
        final UploadSession session = completion.after();
        final int partIndex = completion.partIndex();
        return new CompleteClipResponse(
                completion.camera(),
                partIndex,
                completion.clip().status(),
                new PartSummary(completion.partState(), session.part(partIndex).published()),
                new SessionSummary(session.status(), session.partsPublished(), session.partCount()));
    }

    /**
     * Every part by index, each with one clip per registered camera, LEFT before RIGHT. A part's window is only
     * given when the part has one ({@link UploadPart#hasWindow()}).
     */
    public UploadSessionResponse toResponse(final UploadSession session) {
        final VenueSpec venue = session.venue();
        return new UploadSessionResponse(
                session.id(),
                session.eventId(),
                session.status(),
                session.parts().size(),
                session.partsPublished(),
                new VenueResponse(venue.format(), venue.lengthM(), venue.widthM()),
                session.cameras().stream()
                        .sorted(Comparator.comparing(UploadCamera::camera))
                        .map(camera -> new CameraResponse(camera.camera(), camera.partCount(),
                                session.partsUploaded(camera.camera()), camera.prefix()))
                        .toList(),
                parts(session));
    }

    private static List<CameraSummaryResponse> cameras(final UploadSession session) {
        return session.cameras().stream()
                .sorted(Comparator.comparing(UploadCamera::camera))
                .map(camera -> new CameraSummaryResponse(camera.camera(), session.partsUploaded(camera.camera()),
                        camera.partCount()))
                .toList();
    }

    private static List<PartResponse> parts(final UploadSession session) {
        final Map<Integer, List<ClipResponse>> clipsByPart = clipsByPart(session);
        return session.parts().stream()
                .sorted(Comparator.comparingInt(UploadPart::partIndex))
                .map(part -> new PartResponse(
                        part.partIndex(),
                        session.partState(part.partIndex()),
                        part.published(),
                        part.hasWindow() ? part.window().startMs() : null,
                        part.hasWindow() ? part.window().durationMs() : null,
                        clipsByPart.getOrDefault(part.partIndex(), List.of())))
                .toList();
    }

    // Only registered cameras: a camera joining while the session is read may have its clips read but not itself
    private static Map<Integer, List<ClipResponse>> clipsByPart(final UploadSession session) {
        final Set<Camera> registered = session.cameras().stream()
                .map(UploadCamera::camera)
                .collect(Collectors.toSet());
        return session.clips().stream()
                .filter(clip -> registered.contains(clip.camera()))
                .sorted(Comparator.comparing(UploadClip::camera))
                .collect(Collectors.groupingBy(UploadClip::partIndex,
                        Collectors.mapping(UploadSessionConverter::toResponse, Collectors.toList())));
    }

    // A pending clip has no confirmation yet, so it leaves out what the camera reported
    private static ClipResponse toResponse(final UploadClip clip) {
        final ClipConfirmation confirmation = clip.confirmation();
        return confirmation == null
                ? new ClipResponse(clip.camera(), clip.status(), null, null, null, null)
                : new ClipResponse(clip.camera(), clip.status(), confirmation.sizeBytes(), confirmation.startMs(),
                        confirmation.durationMs(), clip.uploadedAt());
    }
}
