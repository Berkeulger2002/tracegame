package com.trace.backend.dto;

import java.time.Instant;

public record TomorrowGameResponse(
        boolean available,
        String publishDate,
        String title,
        String startEvent,
        String endEvent,
        Instant unlockAt,
        Instant serverTime,
        long remainingSeconds
) {
}