package org.cavalodetroia.ctr.api.domain.upload;

import org.cavalodetroia.ctr.domain.model.upload.Camera;

/**
 * One camera of the session and how far its upload is.
 *
 * @param camera        which camera
 * @param partsUploaded how many of its clips are in the bucket
 * @param partCount     how many clips it uploads
 */
public record CameraSummaryResponse(Camera camera, int partsUploaded, int partCount) {
}
