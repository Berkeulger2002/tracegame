package com.trace.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "user")
public record TraceUser(
        @Id String id,
        String userName,
        boolean isAnonymous,
        boolean tutorialCompleted,
        String guestTokenHash,
        Instant guestExpiresAt,
        Instant createdAt,
        String email,
        String passwordHash,
        Role role
) {
    public TraceUser {
        // Eski kullanıcı belgelerinde role alanı bulunmuyor.
        role = role == null ? Role.USER : role;
    }

    // Eski, yedi parametreli constructor çağrılarıyla uyumluluk.
    public TraceUser(
            String id,
            String userName,
            boolean isAnonymous,
            boolean tutorialCompleted,
            String guestTokenHash,
            Instant guestExpiresAt,
            Instant createdAt
    ) {
        this(
                id,
                userName,
                isAnonymous,
                tutorialCompleted,
                guestTokenHash,
                guestExpiresAt,
                createdAt,
                null,
                null,
                Role.USER
        );
    }

    public enum Role {
        USER,
        ADMIN
    }
}