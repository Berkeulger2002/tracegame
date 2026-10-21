package com.trace.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "attempt_timer")
public record GameTimer(
        @Id String id,
        String userId,
        String sessionId,
        long sequence,
        long elapsedMillis,
        boolean running,
        boolean reliable,
        Instant lastSeenAt,
        @Version Long version
) {
}