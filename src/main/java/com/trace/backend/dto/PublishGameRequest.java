package com.trace.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record PublishGameRequest(
        @NotNull Instant expectedUpdatedAt
) {
}
