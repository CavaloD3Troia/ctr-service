package org.cavalodetroia.ctr.api.error;

/**
 * One entry of the {@code errors} list of a {@code VALIDATION_FAILED} problem.
 *
 * @param field   the path of the value in the request, for example {@code venue.lengthM}
 * @param message what is wrong with it
 */
public record InvalidField(String field, String message) {
}
