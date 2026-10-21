package com.trace.backend.controller;

import com.trace.backend.dto.PlayerStatsResponse;
import com.trace.backend.service.PlayerStatsService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class PlayerStatsController {

    private final PlayerStatsService service;

    public PlayerStatsController(PlayerStatsService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public ResponseEntity<PlayerStatsResponse> me(
            @CookieValue(
                    name = "trace_guest_token",
                    required = false
            )
            String token
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.getStats(token));
    }
}