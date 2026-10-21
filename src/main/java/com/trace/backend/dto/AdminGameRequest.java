package com.trace.backend.dto;

import com.trace.backend.model.TraceGame;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AdminGameRequest(
        @NotBlank
        @Size(max = 160)
        String title,

        @NotBlank
        @Pattern(regexp = "[a-z]{2}")
        String langCode,

        @NotNull
        LocalDate publishDate,

        @NotNull
        TraceGame.Difficulty difficulty,

        @NotBlank
        @Size(max = 2000)
        String startEvent,

        @NotBlank
        @Size(max = 2000)
        String endEvent,

        @NotNull
        @Size(min = 1, max = 30)
        List<@NotNull @Valid StepInput> steps,

        Instant expectedUpdatedAt,

        @Size(max = 10000)
        String startExplanation,

        @Size(max = 10000)
        String endExplanation
) {
        public record StepInput(
                @NotNull
                @Size(min = 3, max = 3)
                List<@NotBlank @Size(max = 2000) String> options,

                @NotNull
                @Min(0)
                @Max(2)
                Integer correctOptionIndex,

                @Size(max = 10000)
                String explanation
        ) {
        }
}