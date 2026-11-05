package org.cavalodetroia.ctr.domain.model.storage;

import java.util.Objects;

/**
 * What the object storage says about an object without sending it.
 *
 * @param sizeBytes its size
 * @param etag      its etag, without the surrounding double quotes S3 puts around it
 */
public record ObjectMetadata(long sizeBytes, String etag) {

    public ObjectMetadata {
        etag = unquoted(Objects.requireNonNull(etag, "etag"));
    }

    /**
     * Etags are compared as strings once the surrounding double quotes are gone on both sides, so a multipart etag
     * such as {@code abc-3} is just another string.
     *
     * @param etag an etag, quoted or not
     * @return whether it is this object's etag
     */
    public boolean hasEtag(final String etag) {
        return this.etag.equals(unquoted(etag));
    }

    private static String unquoted(final String etag) {
        if (etag.length() >= 2 && etag.startsWith("\"") && etag.endsWith("\"")) {
            return etag.substring(1, etag.length() - 1);
        }
        return etag;
    }
}
