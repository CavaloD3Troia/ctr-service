package org.cavalodetroia.ctr.infra.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;

/**
 * Drops records with a null value before they reach a listener that has no use for deletions.
 */
public class TombstoneFilter<K, V> implements RecordFilterStrategy<K, V> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TombstoneFilter.class);

    @Override
    public boolean filter(final ConsumerRecord<K, V> record) {
        if (record.value() == null) {
            LOGGER.debug("operation=filter, message=Tombstone discarded topic='{}' key='{}'", record.topic(), record.key());
            return true;
        }
        return false;
    }
}
