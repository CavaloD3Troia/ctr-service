package org.cavalodetroia.ctr.archfixtures.shared;

import org.cavalodetroia.ctr.archfixtures.application.ApplicationResident;

/**
 * NO_CYCLES: a package outside the four layers (so LAYERS ignores it) that reaches back into the composition root,
 * closing the cycle {@code application -> shared -> application}.
 */
public final class SharedReachingIntoApplication {

    private ApplicationResident wiring;
}
