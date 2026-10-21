package com.trace.backend.controller;

import com.trace.backend.dto.GameFeedbackDto;
import com.trace.backend.service.GameFeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class GameFeedbackController {

    private final GameFeedbackService service;

    public GameFeedbackController(GameFeedbackService service) {
        this.service = service;
    }

    @GetMapping("/api/attempts/today/feedback")
    public ResponseEntity<GameFeedbackDto.Response> getToday(
            @CookieValue(name = "trace_guest_token", required = false)
            String token
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.getToday(token));
    }

    @PutMapping(
            value = "/api/attempts/today/feedback",
            consumes = "application/json"
    )
    public ResponseEntity<GameFeedbackDto.Response> saveToday(
            @CookieValue(name = "trace_guest_token", required = false)
            String token,
            @Valid @RequestBody GameFeedbackDto.Request request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.saveToday(token, request));
    }

    @GetMapping("/api/admin/games/{id}/feedback/stats")
    public ResponseEntity<GameFeedbackDto.Stats> getStats(
            @CookieValue(name = "trace_guest_token", required = false)
            String token,
            @PathVariable("id") String id
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.getStats(token, id));
    }
}