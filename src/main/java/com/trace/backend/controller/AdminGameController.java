package com.trace.backend.controller;

import com.trace.backend.dto.AdminGameRequest;
import com.trace.backend.dto.PublishGameRequest;
import com.trace.backend.model.TraceGame;
import com.trace.backend.service.AdminGameService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/admin/games")
public class AdminGameController {
    private final AdminGameService service;

    public AdminGameController(AdminGameService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<TraceGame>> list(
            @CookieValue(name = "trace_guest_token", required = false)
            String token,

            @RequestParam(required = false)
            TraceGame.GameStatus status,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.list(token, status, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TraceGame> get(
            @CookieValue(name = "trace_guest_token", required = false)
            String token,
            @PathVariable String id
    ) {
        return result(service.get(token, id));
    }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<TraceGame> create(
            @CookieValue(name = "trace_guest_token", required = false)
            String token,
            @Valid @RequestBody AdminGameRequest request
    ) {
        TraceGame game = service.create(token, request);

        return ResponseEntity.created(
                        URI.create("/api/admin/games/" + game.id())
                )
                .cacheControl(CacheControl.noStore())
                .body(game);
    }

    @PutMapping(
            value = "/{id}",
            consumes = "application/json"
    )
    public ResponseEntity<TraceGame> update(
            @CookieValue(name = "trace_guest_token", required = false)
            String token,
            @PathVariable String id,
            @Valid @RequestBody AdminGameRequest request
    ) {
        return result(service.update(token, id, request));
    }

    @PostMapping(
            value = "/{id}/publish",
            consumes = "application/json"
    )
    public ResponseEntity<TraceGame> publish(
            @CookieValue(name = "trace_guest_token", required = false)
            String token,
            @PathVariable String id,
            @Valid @RequestBody PublishGameRequest request
    ) {
        return result(service.publish(token, id, request));
    }

    private ResponseEntity<TraceGame> result(TraceGame game) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(game);
    }
}