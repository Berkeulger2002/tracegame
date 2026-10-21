package com.trace.backend.dto;

public record TodayGameResponse(
        String id,
        String title,
        String langCode,
        String publishDate,
        String difficulty,
        String startEvent,
        String endEvent,
        int stepCount
) {
}