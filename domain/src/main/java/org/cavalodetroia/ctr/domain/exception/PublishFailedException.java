package org.cavalodetroia.ctr.domain.exception;

/**
 * A part's message could not be published: the broker was unreachable, too slow, or refused it. Checked on purpose:
 * the caller has already committed the part, so it must decide what a failed publish means instead of letting it
 * reach the client as an error.
 */
public class PublishFailedException extends Exception {

    private final String topic;

    /**
     * @param topic where the message was going
     * @param key   the message's key
     * @param cause what the producer reported
     */
    public PublishFailedException(final String topic, final String key, final Throwable cause) {
        super("Could not publish '" + key + "' to '" + topic + "'", cause);
        this.topic = topic;
    }

    public String topic() {
        return topic;
    }
}
