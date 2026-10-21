package com.trace.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AnswerRequest(
        @NotBlank String stepId,
        @NotBlank String optionId,
        @NotNull @PositiveOrZero Long version
) {
}