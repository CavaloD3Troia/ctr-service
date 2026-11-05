package org.cavalodetroia.ctr.archfixtures.infra.kafka;

import org.cavalodetroia.ctr.archfixtures.infra.persistence.PersistenceAdapter;

/** AC8: a Kafka adapter that uses the persistence adapter instead of a domain port. */
public final class KafkaAdapterUsingPersistence {

    private PersistenceAdapter repository;
}
