package com.trace.backend.dto;

import com.trace.backend.model.Attempt;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record HintRequest(
        @NotBlank String stepId,
        @NotNull Attempt.HintType type,
        @NotNull @PositiveOrZero Long version
) {
}