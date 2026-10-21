package com.trace.backend.dto;

import java.time.Instant;

public record BadgeResponse(
        String code,
        String title,
        int progress,
        int target,
        boolean earned,
        Instant earnedAt
) {
}