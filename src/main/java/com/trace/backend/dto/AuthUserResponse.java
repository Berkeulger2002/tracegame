package com.trace.backend.dto;

import com.trace.backend.model.TraceUser;

public record AuthUserResponse(
        String id,
        String userName,
        boolean isAnonymous,
        boolean tutorialCompleted,
        String email,
        String role
) {
    public static AuthUserResponse from(TraceUser user) {
        return new AuthUserResponse(
                user.id(),
                user.userName(),
                user.isAnonymous(),
                user.tutorialCompleted(),
                user.email(),
                user.role().name()
        );
    }
}