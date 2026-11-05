package org.cavalodetroia.ctr.archfixtures.api;

import org.cavalodetroia.ctr.archfixtures.infra.InfraResident;

/** LAYERS: an api class that uses infra directly instead of a domain port. */
public final class ApiReachingIntoInfra {

    private InfraResident adapter;
}
