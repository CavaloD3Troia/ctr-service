package org.cavalodetroia.ctr.archfixtures.infra;

import org.springframework.web.bind.annotation.PostMapping;

/** INFRA_HAS_NO_WEB_TYPES: an infra class with a Spring MVC mapping. */
public final class InfraUsingWebTypes {

    @PostMapping("/api/v1/upload-sessions")
    void create() {
    }
}
