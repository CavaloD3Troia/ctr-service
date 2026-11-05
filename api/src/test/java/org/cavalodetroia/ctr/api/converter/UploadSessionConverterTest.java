package org.cavalodetroia.ctr.api.converter;

import org.cavalodetroia.ctr.api.domain.upload.CameraSummaryResponse;
import org.cavalodetroia.ctr.api.domain.upload.CompleteClipRequest;
import org.cavalodetroia.ctr.api.domain.upload.CompleteClipResponse;
import org.cavalodetroia.ctr.api.domain.upload.PartSummary;
import org.cavalodetroia.ctr.api.domain.upload.RegisterCameraRequest;
import org.cavalodetroia.ctr.api.domain.upload.RegisterCameraResponse;
import org.cavalodetroia.ctr.api.domain.upload.SessionSummary;
import org.cavalodetroia.ctr.api.domain.venue.VenueRequest;
import org.cavalodetroia.ctr.domain.exception.VenueSpecException;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.CameraRegistration;
import org.cavalodetroia.ctr.domain.model.upload.ClipConfirmation;
import org.cavalodetroia.ctr.domain.model.upload.ClipStatus;
import org.cavalodetroia.ctr.domain.model.upload.PartState;
import org.cavalodetroia.ctr.domain.model.upload.SessionStatus;
import org.cavalodetroia.ctr.domain.model.venue.RequestedVenue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cavalodetroia.ctr.api.Registrations.EVENT_ID;
import static org.cavalodetroia.ctr.api.Registrations.SESSION_ID;
import static org.cavalodetroia.ctr.api.Registrations.leftConfirmedPart3;
import static org.cavalodetroia.ctr.api.Registrations.leftOpened;
import static org.cavalodetroia.ctr.api.Registrations.rightConfirmedPart3AfterLeft;
import static org.cavalodetroia.ctr.api.Registrations.rightJoinedLeft;

/**
 * TH-137 AC-1, AC-2: the request to the domain registration, and the registration result to the response body.
 * TH-142 AC-1, AC-2: the clip confirmation to the domain, and the completion to the response body.
 */
class UploadSessionConverterTest {

    private static final String SESSION_FOLDER = "s3://cdt-videos/events/" + EVENT_ID + "/sessions/" + SESSION_ID + "/";

    private final UploadSessionConverter converter = new UploadSessionConverter();

    @Test
    void should_carryCameraPartCountAndTheVenueAsSent_when_convertingTheRequest() {
        final RegisterCameraRequest request = new RegisterCameraRequest(Camera.LEFT, 40, "video/mp4",
                new VenueRequest("FOOTBALL_11V11", 105.0, 68.0));

        assertThat(converter.toRegistration(request)).isEqualTo(
                new CameraRegistration(Camera.LEFT, 40, new RequestedVenue("FOOTBALL_11V11", 105.0, 68.0)));
    }

    // The format is the domain's to check, and only on the first camera of a session
    @Test
    void should_notCheckTheFormat_when_convertingTheRequest() {
        final RegisterCameraRequest request = new RegisterCameraRequest(Camera.RIGHT, 40, "video/mp4",
                new VenueRequest("HOCKEY", null, null));

        assertThat(converter.toRegistration(request).venue()).isEqualTo(new RequestedVenue("HOCKEY", null, null));
    }

    @Test
    void should_haveNoVenue_when_theRequestHasNone() {
        final RegisterCameraRequest request = new RegisterCameraRequest(Camera.RIGHT, 40, "video/mp4", null);

        assertThat(converter.toRegistration(request).hasVenue()).isFalse();
    }

    @Test
    void should_rejectTheVenue_when_onlyOneMeasureIsGiven() {
        final RegisterCameraRequest request = new RegisterCameraRequest(Camera.LEFT, 40, "video/mp4",
                new VenueRequest("FOOTBALL_7V7", 60.0, null));

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

    @Test
    void should_carryTheBodyAsSent_when_convertingAClipConfirmation() {
        final CompleteClipRequest request = new CompleteClipRequest(1_073_741_824L, "\"9b2cf5\"", 900_000L, 300_000L);

        assertThat(converter.toConfirmation(request))
                .isEqualTo(new ClipConfirmation(1_073_741_824L, "\"9b2cf5\"", 900_000, 300_000));
    }

    @Test
    void should_waitForTheOtherCamera_when_convertingTheFirstClipOfAPart() {
        final CompleteClipResponse response = converter.toResponse(leftConfirmedPart3());

        assertThat(response).isEqualTo(new CompleteClipResponse(Camera.LEFT, 3, ClipStatus.UPLOADED,
                new PartSummary(PartState.WAITING_RIGHT, false), new SessionSummary(SessionStatus.OPEN, 0, 40)));
    }

    @Test
    void should_sayComplete_when_convertingTheSecondClipOfAPart() {
        final CompleteClipResponse response = converter.toResponse(rightConfirmedPart3AfterLeft());

        assertThat(response.camera()).isEqualTo(Camera.RIGHT);
        assertThat(response.part()).isEqualTo(new PartSummary(PartState.COMPLETE, false));
    }
}
