package org.cavalodetroia.ctr.api.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEventRequest(
        @NotBlank(message = "is mandatory")
        @Size(max = 200, message = "must have at most 200 characters")
        String name,

        @NotBlank(message = "is mandatory")
        @Size(max = 50, message = "must have at most 50 characters")
        String sport) {
}
