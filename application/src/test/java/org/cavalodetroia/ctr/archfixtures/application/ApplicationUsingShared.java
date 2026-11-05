package org.cavalodetroia.ctr.archfixtures.application;

import org.cavalodetroia.ctr.archfixtures.shared.SharedReachingIntoApplication;

/** NO_CYCLES: the composition root using a package outside the four layers, which uses it back. */
public final class ApplicationUsingShared {

    private SharedReachingIntoApplication shared;
}
