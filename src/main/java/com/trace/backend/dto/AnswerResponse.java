package com.trace.backend.dto;

public record AnswerResponse(
        boolean correct,
        AttemptResponse attempt
) {
}