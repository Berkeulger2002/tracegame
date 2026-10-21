package com.trace.backend.controller;

import com.trace.backend.dto.AuthUserResponse;
import com.trace.backend.dto.LoginRequest;
import com.trace.backend.dto.RegisterRequest;
import com.trace.backend.service.GuestAuthService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class GuestAuthController {
    private final GuestAuthService service;
    private final Clock gameClock;
    private final boolean secureCookie;

    public GuestAuthController(
            GuestAuthService service,
            Clock gameClock,
            @Value("${app.auth.cookie-secure:false}") boolean secureCookie
    ) {
        this.service = service;
        this.gameClock = gameClock;
        this.secureCookie = secureCookie;
    }

    @PostMapping("/guest")
    public ResponseEntity<AuthUserResponse> guest(
            @CookieValue(name = "trace_guest_token", required = false)
            String token
    ) {
        return sessionResponse(service.getOrCreateGuest(token));
    }

    @PostMapping(
            value = "/register",
            consumes = "application/json"
    )
    public ResponseEntity<AuthUserResponse> register(
            @CookieValue(name = "trace_guest_token", required = false)
            String token,
            @Valid @RequestBody RegisterRequest request
    ) {
        return sessionResponse(service.register(token, request));
    }

    @PostMapping(
            value = "/login",
            consumes = "application/json"
    )
    public ResponseEntity<AuthUserResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        return sessionResponse(service.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthUserResponse> me(
            @CookieValue(name = "trace_guest_token", required = false)
            String token
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(AuthUserResponse.from(service.requireUser(token)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "trace_guest_token", required = false)
            String token
    ) {
        service.logout(token);

        return ResponseEntity.noContent()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookie("", 0).toString()
                )
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    private ResponseEntity<AuthUserResponse> sessionResponse(
            GuestAuthService.GuestSession session
    ) {
        long remainingSeconds = Math.max(
                0,
                Duration.between(
                        gameClock.instant(),
                        session.user().guestExpiresAt()
                ).getSeconds()
        );

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookie(session.token(), remainingSeconds).toString()
                )
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(AuthUserResponse.from(session.user()));
    }

    private ResponseCookie cookie(String token, long maxAge) {
        return ResponseCookie
                .from("trace_guest_token", token)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/api")
                .maxAge(maxAge)
                .build();
    }
}