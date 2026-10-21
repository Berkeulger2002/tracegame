package com.trace.backend.service;

import java.time.Duration;
import java.time.Instant;

public final class ActiveTimePolicy {
    public static final long HEARTBEAT_INTERVAL_MILLIS = 5_000;
    public static final long LEASE_TIMEOUT_MILLIS = 10_000;

    private ActiveTimePolicy() {
    }

    public static long elapsed(
            long savedMillis,
            Instant lastSeen,
            boolean running,
            Instant now
    ) {
        if (!running || lastSeen == null) {
            return savedMillis;
        }

        long delta = Math.max(
                0,
                Duration.between(lastSeen, now).toMillis()
        );

        return Math.addExact(
                savedMillis,
                Math.min(delta, LEASE_TIMEOUT_MILLIS)
        );
    }

    public static boolean expired(
            boolean running,
            Instant lastSeen,
            Instant now
    ) {
        return running && (
                lastSeen == null
                        || Duration.between(lastSeen, now).toMillis()
                        > LEASE_TIMEOUT_MILLIS
        );
    }

    public static boolean fastWin(boolean tracked, Long durationMillis) {
        return tracked
                && durationMillis != null
                && durationMillis >= 0
                && durationMillis < 60_000;
    }
}