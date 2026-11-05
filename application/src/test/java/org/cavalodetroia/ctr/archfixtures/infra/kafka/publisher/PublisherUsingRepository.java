package org.cavalodetroia.ctr.archfixtures.infra.kafka.publisher;

import org.cavalodetroia.ctr.archfixtures.infra.repository.ResidentRepository;

/** INFRA_ADAPTERS_ARE_INDEPENDENT: a Kafka publisher that uses the repository adapter instead of a domain port. */
public final class PublisherUsingRepository {

    private ResidentRepository repository;
}
