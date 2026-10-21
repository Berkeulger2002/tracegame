package com.trace.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "game_feedback")
public record GameFeedback(
        @Id String id,
        String userId,
        String gameId,
        String date,
        Rating rating,
        Instant createdAt,
        Instant updatedAt
) {
    public enum Rating {
        GOOD,
        BAD
    }
}