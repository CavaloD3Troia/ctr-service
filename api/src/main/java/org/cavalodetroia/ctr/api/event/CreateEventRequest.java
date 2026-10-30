package org.cavalodetroia.ctr.api.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateEventRequest(@NotBlank @Size(max = 200) String name) {
}
