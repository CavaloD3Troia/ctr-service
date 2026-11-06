package org.cavalodetroia.ctr.domain.service;

import org.cavalodetroia.ctr.domain.exception.SessionNotFoundException;
import org.cavalodetroia.ctr.domain.model.upload.ClipCompletion;
import org.cavalodetroia.ctr.domain.model.upload.UploadCamera;
import org.cavalodetroia.ctr.domain.model.upload.UploadSession;
import org.cavalodetroia.ctr.domain.port.out.UploadSessionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sessions in memory, for the clip confirmation, counting the writes and recording the order of the calls that write
 * (with {@link FakeClipEventPublisher}'s, when it shares this log). Registration is tested against mocks.
 */
final class InMemoryUploadSessionRepository implements UploadSessionRepository {

    private final Map<UUID, UploadSession> sessions = new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private UploadSession staleRead;
    private int writes;

    void store(final UploadSession session) {
        sessions.put(session.id(), session);
    }

    /**
     * {@link #findById} returns {@code stale} from now on, as if another request changed the session after it was
     * read; {@link #update} still applies the change to what is stored.
     */
    void readStale(final UploadSession stale) {
        staleRead = stale;
    }

    UploadSession stored(final UUID sessionId) {
        return sessions.get(sessionId);
    }

    int writes() {
        return writes;
    }

    /**
     * @return {@code update} (once its change is stored, as a commit), {@code markPublished <partIndex>} and what
     *         others {@link #record recorded}, in call order
     */
    List<String> calls() {
        return List.copyOf(calls);
    }

    void record(final String call) {
        calls.add(call);
    }

    @Override
    public Optional<UploadSession> findById(final UUID sessionId) {
        if (staleRead != null) {
            return Optional.of(staleRead);
        }
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public synchronized ClipCompletion update(final UUID sessionId,
                                              final Function<UploadSession, ClipCompletion> completion) {
        final UploadSession current = Optional.ofNullable(sessions.get(sessionId))
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        final ClipCompletion result = completion.apply(current);
        if (!result.noop()) {
            sessions.put(sessionId, result.after());
            writes++;
        }
        calls.add("update");
        return result;
    }

    // Like the jOOQ adapter's UPDATE ... WHERE published_at IS NULL: a part published already keeps its time
    @Override
    public void markPublished(final UUID sessionId, final int partIndex, final Instant publishedAt) {
        calls.add("markPublished " + partIndex);
        final UploadSession current = sessions.get(sessionId);
        if (!current.part(partIndex).published()) {
            sessions.put(sessionId, current.markPublished(partIndex, publishedAt));
        }
    }

    @Override
    public Optional<UploadSession> findOpenByEvent(final UUID eventId) {
        throw new UnsupportedOperationException("not used by the clip confirmation");
    }

    @Override
    public void create(final UploadSession session) {
        throw new UnsupportedOperationException("not used by the clip confirmation");
    }

    @Override
    public void addCamera(final UploadSession session, final UploadCamera camera) {
        throw new UnsupportedOperationException("not used by the clip confirmation");
    }
}
