package org.cavalodetroia.ctr.domain.service;

import org.cavalodetroia.ctr.domain.exception.CameraSetConflictException;
import org.cavalodetroia.ctr.domain.exception.EventNotFoundException;
import org.cavalodetroia.ctr.domain.exception.OpenSessionAlreadyExistsException;
import org.cavalodetroia.ctr.domain.exception.SessionNotFoundException;
import org.cavalodetroia.ctr.domain.exception.UploadSessionChangedException;
import org.cavalodetroia.ctr.domain.model.upload.Camera;
import org.cavalodetroia.ctr.domain.model.upload.RegistrationResult;
import org.cavalodetroia.ctr.domain.model.upload.UploadCamera;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.port.out.ClipEventPublisher;
import org.cavalodetroia.ctr.domain.port.out.ClipStorage;
import org.cavalodetroia.ctr.domain.port.out.EventRepository;
import org.cavalodetroia.ctr.domain.port.out.UploadSessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.BUCKET;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.EVENT_ID;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.JOINED_AT;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.SESSION_ID;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.first;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.joining;
import static org.cavalodetroia.ctr.domain.model.upload.CameraRegistrations.openedBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AC-1, AC-2, AC-6, AC-11: the use case opens or joins the event's session through the ports, and registers again
 * against what is stored when a concurrent registration won.
 */
@ExtendWith(MockitoExtension.class)
class UploadSessionServiceTest {

    private static final Clock CLOCK = Clock.fixed(JOINED_AT, ZoneOffset.UTC);

    @Mock
    private EventRepository events;

    @Mock
    private UploadSessionRepository sessions;

    @Mock
    private ClipStorage storage;

    @Mock
    private ClipEventPublisher publisher;

    private UploadSessionService service() {
        return new UploadSessionService(events, sessions, storage, publisher, CLOCK, BUCKET);
    }

    @Test
    void should_throwEventNotFound_when_theEventIsUnknown() {
        when(events.exists(EVENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service().register(EVENT_ID, first(Camera.LEFT)))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining(EVENT_ID.toString());
        verify(sessions, never()).create(any());
    }

    @Test
    void should_createASessionWithAUuid7Id_when_theEventHasNoOpenSession() {
        when(events.exists(EVENT_ID)).thenReturn(true);
        when(sessions.findOpenByEvent(EVENT_ID)).thenReturn(Optional.empty());

        final RegistrationResult result = service().register(EVENT_ID, first(Camera.LEFT));

        final ArgumentCaptor<UploadSession> created = ArgumentCaptor.forClass(UploadSession.class);
        verify(sessions).create(created.capture());
        assertThat(created.getValue()).isEqualTo(result.session());
        assertThat(result.session().id().version()).isEqualTo(7);
        assertThat(result.session().id().getMostSignificantBits() >>> 16).isEqualTo(JOINED_AT.toEpochMilli());
        assertThat(result.session().createdAt()).isEqualTo(JOINED_AT);
        assertThat(result.camera().camera()).isEqualTo(Camera.LEFT);
    }

    @Test
    void should_addTheCameraToTheOpenSession_when_theEventHasOne() {
        final UploadSession left = openedBy(Camera.LEFT);
        when(events.exists(EVENT_ID)).thenReturn(true);
        when(sessions.findOpenByEvent(EVENT_ID)).thenReturn(Optional.of(left));

        final RegistrationResult result = service().register(EVENT_ID, joining(Camera.RIGHT));

        assertThat(result.session().id()).isEqualTo(left.id());
        assertThat(result.camera().camera()).isEqualTo(Camera.RIGHT);
        verify(sessions).addCamera(result.session(), result.camera());
        verify(sessions, never()).create(any());
    }

    @Test
    void should_joinTheSessionThatWon_when_anotherFirstCameraOpenedItInTheMeantime() {
        final UploadSession winner = openedBy(Camera.LEFT);
        when(events.exists(EVENT_ID)).thenReturn(true);
        when(sessions.findOpenByEvent(EVENT_ID)).thenReturn(Optional.empty(), Optional.of(winner));
        doThrow(new OpenSessionAlreadyExistsException(EVENT_ID, null)).when(sessions).create(any());

        final RegistrationResult result = service().register(EVENT_ID, first(Camera.RIGHT));

        assertThat(result.session().id()).isEqualTo(winner.id());
        assertThat(result.session().cameras()).extracting(UploadCamera::camera)
                .containsExactly(Camera.LEFT, Camera.RIGHT);
        verify(sessions).addCamera(result.session(), result.camera());
    }

    @Test
    void should_readTheSessionAgainAndJoinWhatIsThere_when_anotherCameraJoinedInTheMeantime() {
        final UploadSession left = openedBy(Camera.LEFT);
        final UploadSession leftAndRight = left.join(joining(Camera.RIGHT), BUCKET, JOINED_AT).session();
        when(events.exists(EVENT_ID)).thenReturn(true);
        when(sessions.findOpenByEvent(EVENT_ID)).thenReturn(Optional.of(left), Optional.of(leftAndRight));
        doThrow(new UploadSessionChangedException(left.id())).when(sessions).addCamera(any(), any());

        // A second RIGHT read the session with LEFT only; read again, it finds RIGHT already there
        assertThatThrownBy(() -> service().register(EVENT_ID, joining(Camera.RIGHT)))
                .isInstanceOf(CameraSetConflictException.class);
        verify(sessions, times(2)).findOpenByEvent(EVENT_ID);
        verify(sessions, times(1)).addCamera(any(), any());
    }

    @Test
    void should_joinAfterReadingAgain_when_theSessionChangedOnceInTheMeantime() {
        final UploadSession left = openedBy(Camera.LEFT);
        when(events.exists(EVENT_ID)).thenReturn(true);
        when(sessions.findOpenByEvent(EVENT_ID)).thenReturn(Optional.of(left));
        doThrow(new UploadSessionChangedException(left.id())).doNothing().when(sessions).addCamera(any(), any());

        final RegistrationResult result = service().register(EVENT_ID, joining(Camera.RIGHT));

        assertThat(result.session().cameras()).extracting(UploadCamera::camera)
                .containsExactly(Camera.LEFT, Camera.RIGHT);
        verify(sessions, times(2)).addCamera(any(), any());
    }

    @Test
    void should_propagate_when_everyAttemptRaces() {
        when(events.exists(EVENT_ID)).thenReturn(true);
        when(sessions.findOpenByEvent(EVENT_ID)).thenReturn(Optional.empty());
        doThrow(new OpenSessionAlreadyExistsException(EVENT_ID, null)).when(sessions).create(any());

        assertThatThrownBy(() -> service().register(EVENT_ID, first(Camera.LEFT)))
                .isInstanceOf(OpenSessionAlreadyExistsException.class);
        verify(sessions, times(3)).create(any());
    }

    // TH-143 AC-1: the session as stored, and nothing written
    @Test
    void should_giveTheStoredSession_when_readingAKnownSession() {
        final UploadSession stored = openedBy(Camera.LEFT);
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.of(stored));

        assertThat(service().get(SESSION_ID)).isEqualTo(stored);
        verify(sessions, never()).create(any());
        verify(sessions, never()).addCamera(any(), any());
    }

    // TH-143 AC-4
    @Test
    void should_throwSessionNotFound_when_readingAnUnknownSession() {
        when(sessions.findById(SESSION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(SESSION_ID))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining(SESSION_ID.toString());
    }
}
