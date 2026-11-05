package org.cavalodetroia.ctr.api.converter;

import org.cavalodetroia.ctr.api.domain.upload.CameraSummaryResponse;
import org.cavalodetroia.ctr.api.domain.upload.RegisterCameraRequest;
import org.cavalodetroia.ctr.api.domain.upload.RegisterCameraResponse;
import org.cavalodetroia.ctr.api.domain.venue.VenueRequest;
import org.cavalodetroia.ctr.domain.exception.VenueSpecException;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.CameraRegistration;
import org.cavalodetroia.ctr.domain.model.upload.SessionStatus;
import org.cavalodetroia.ctr.domain.model.venue.VenueFormat;
import org.cavalodetroia.ctr.domain.model.venue.VenueSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cavalodetroia.ctr.api.Registrations.EVENT_ID;
import static org.cavalodetroia.ctr.api.Registrations.SESSION_ID;
import static org.cavalodetroia.ctr.api.Registrations.leftOpened;
import static org.cavalodetroia.ctr.api.Registrations.rightJoinedLeft;

/**
 * AC-1, AC-2: the request to the domain registration, and the registration result to the response body.
 */
class UploadSessionConverterTest {

    private static final String SESSION_FOLDER = "s3://cdt-videos/events/" + EVENT_ID + "/sessions/" + SESSION_ID + "/";

    private final UploadSessionConverter converter = new UploadSessionConverter();

    @Test
    void should_carryCameraPartCountAndMeasuredVenue_when_convertingTheRequest() {
        final RegisterCameraRequest request = new RegisterCameraRequest(Camera.LEFT, 40, "video/mp4",
                new VenueRequest(VenueFormat.FOOTBALL_11V11, 105.0, 68.0));

        assertThat(converter.toRegistration(request)).isEqualTo(
                new CameraRegistration(Camera.LEFT, 40, new VenueSpec(VenueFormat.FOOTBALL_11V11, 105, 68)));
    }

    @Test
    void should_useTheStandardDimensions_when_theRequestVenueHasNoMeasures() {
        final RegisterCameraRequest request = new RegisterCameraRequest(Camera.SINGLE, 10, "video/mp4",
                new VenueRequest(VenueFormat.FUTSAL_5V5, null, null));

        assertThat(converter.toRegistration(request).venue()).isEqualTo(new VenueSpec(VenueFormat.FUTSAL_5V5, 40, 20));
    }

    @Test
    void should_haveNoVenue_when_theRequestHasNone() {
        final RegisterCameraRequest request = new RegisterCameraRequest(Camera.RIGHT, 40, "video/mp4", null);

        assertThat(converter.toRegistration(request).hasVenue()).isFalse();
    }

    @Test
    void should_rejectTheVenue_when_onlyOneMeasureIsGiven() {
        final RegisterCameraRequest request = new RegisterCameraRequest(Camera.LEFT, 40, "video/mp4",
                new VenueRequest(VenueFormat.FOOTBALL_7V7, 60.0, null));

        assertThatThrownBy(() -> converter.toRegistration(request)).isInstanceOf(VenueSpecException.class);
    }

    @Test
    void should_givePrefixClipKeyAndCameras_when_convertingTheFirstCamera() {
        final RegisterCameraResponse response = converter.toResponse(leftOpened());

        assertThat(response).isEqualTo(new RegisterCameraResponse(SESSION_ID, EVENT_ID, SessionStatus.OPEN,
                Camera.LEFT, 40, SESSION_FOLDER + "LEFT/", "{partIndex}.mp4",
                List.of(new CameraSummaryResponse(Camera.LEFT, 0, 40))));
    }

    @Test
    void should_giveTheJoiningCameraAndListLeftBeforeRight_when_convertingTheSecondCamera() {
        final RegisterCameraResponse response = converter.toResponse(rightJoinedLeft());

        assertThat(response.camera()).isEqualTo(Camera.RIGHT);
        assertThat(response.prefix()).isEqualTo(SESSION_FOLDER + "RIGHT/");
        assertThat(response.cameras()).containsExactly(
                new CameraSummaryResponse(Camera.LEFT, 0, 40),
                new CameraSummaryResponse(Camera.RIGHT, 0, 40));
    }
}
