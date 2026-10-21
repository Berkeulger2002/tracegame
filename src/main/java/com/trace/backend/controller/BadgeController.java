package com.trace.backend.controller;

import com.trace.backend.dto.BadgeResponse;
import com.trace.backend.service.BadgeService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/badges")
public class BadgeController {

    private final BadgeService service;

    public BadgeController(BadgeService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<BadgeResponse>> getBadges(
            @CookieValue(
                    name = "trace_guest_token",
                    required = false
            ) String token
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.getBadges(token));
    }

    @PostMapping("/sync")
    public ResponseEntity<List<BadgeResponse>> syncBadges(
            @CookieValue(
                    name = "trace_guest_token",
                    required = false
            ) String token
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.syncBadges(token));
    }
}