package org.cavalodetroia.ctr.api.converter;

import org.cavalodetroia.ctr.api.domain.upload.CameraSummaryResponse;
import org.cavalodetroia.ctr.api.domain.upload.RegisterCameraRequest;
import org.cavalodetroia.ctr.api.domain.upload.RegisterCameraResponse;
import org.cavalodetroia.ctr.api.domain.venue.VenueRequest;
import org.cavalodetroia.ctr.domain.exception.VenueSpecException;
import org.cavalodetroia.ctr.domain.model.upload.CameraRegistration;
import org.cavalodetroia.ctr.domain.model.upload.ObjectKeys;
import org.cavalodetroia.ctr.domain.model.upload.RegistrationResult;
import org.cavalodetroia.ctr.domain.model.upload.UploadCamera;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.model.venue.VenueSpec;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class UploadSessionConverter {

    /**
     * @throws VenueSpecException when the venue has no format or only one of its measures
     */
    public CameraRegistration toRegistration(final RegisterCameraRequest request) {
        final VenueRequest venue = request.venue();
        return new CameraRegistration(request.camera(), request.partCount(),
                venue == null ? null : VenueSpec.of(venue.format(), venue.lengthM(), venue.widthM()));
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

    private static List<CameraSummaryResponse> cameras(final UploadSession session) {
        return session.cameras().stream()
                .sorted(Comparator.comparing(UploadCamera::camera))
                .map(camera -> new CameraSummaryResponse(camera.camera(), session.partsUploaded(camera.camera()),
                        camera.partCount()))
                .toList();
    }
}
