package org.cavalodetroia.ctr.domain.model.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC-4: etags compare as strings once the surrounding double quotes are stripped on both sides.
 */
class ObjectMetadataTest {

    @Test
    void should_dropTheSurroundingQuotes_when_givenTheEtagAsS3SendsIt() {
        assertThat(new ObjectMetadata(1, "\"9b2cf5\"").etag()).isEqualTo("9b2cf5");
    }

    @ParameterizedTest(name = "stored {0}, confirmed {1}: {2}")
    @CsvSource(delimiter = '|', value = {
            "\"9b2cf5\"   | 9b2cf5     | true",
            "9b2cf5       | \"9b2cf5\" | true",
            "\"abc-3\"    | abc-3      | true",
            "abc-3        | abc-2      | false",
            "abc-3        | abc        | false",
            "9b2cf5       | 9B2CF5     | false",
            "\"           | \"         | true"})
    void should_compareTheEtagsWithoutTheirQuotes_when_askedWhetherItHasAnEtag(final String stored,
                                                                              final String confirmed,
                                                                              final boolean same) {
        assertThat(new ObjectMetadata(1, stored).hasEtag(confirmed)).isEqualTo(same);
    }
}
