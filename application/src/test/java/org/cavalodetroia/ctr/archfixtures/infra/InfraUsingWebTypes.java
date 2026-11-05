package org.cavalodetroia.ctr.archfixtures.infra;

import org.springframework.web.bind.annotation.PostMapping;

/** AC5: an infra class with a Spring MVC mapping. */
public final class InfraUsingWebTypes {

    @PostMapping("/api/v1/events")
    void create() {
    }
}
