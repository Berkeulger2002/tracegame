package com.trace.backend.dto;

public record TimerResponse(
        String sessionId,
        long sequence,
        long activeDurationMillis,
        boolean running,
        boolean speedBadgeEligible,
        long heartbeatIntervalMillis,
        long leaseTimeoutMillis
) {
}