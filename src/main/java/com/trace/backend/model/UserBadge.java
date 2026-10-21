package com.trace.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "user_badge")
public record UserBadge(
        @Id String id,
        String userId,
        BadgeCode badgeCode,
        Instant earnedAt
) {
}