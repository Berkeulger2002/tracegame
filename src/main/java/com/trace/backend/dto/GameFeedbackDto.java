package com.trace.backend.dto;

import com.trace.backend.model.GameFeedback;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public final class GameFeedbackDto {

    private GameFeedbackDto() {
    }

    public record Request(
            @NotNull GameFeedback.Rating rating
    ) {
    }

    public record Response(
            String gameId,
            String date,
            GameFeedback.Rating rating,
            Instant updatedAt
    ) {
    }

    public record Stats(
            String gameId,
            long total,
            long good,
            long bad
    ) {
    }
}