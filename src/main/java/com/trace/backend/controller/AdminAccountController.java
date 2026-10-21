package com.trace.backend.controller;

import com.trace.backend.dto.AuthUserResponse;
import com.trace.backend.service.GuestAuthService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminAccountController {
    private final GuestAuthService authService;

    public AdminAccountController(GuestAuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me(
            @CookieValue(name = "trace_guest_token", required = false)
            String token
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        AuthUserResponse.from(
                                authService.requireAdmin(token)
                        )
                );
    }
}