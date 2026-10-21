package com.trace.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record TimerRequest(
        @NotBlank String sessionId,
        @Positive long sequence
) {
}