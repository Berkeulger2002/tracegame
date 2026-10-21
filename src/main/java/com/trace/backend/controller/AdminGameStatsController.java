package com.trace.backend.controller;

import com.trace.backend.dto.AdminGameStatsResponse;
import com.trace.backend.service.AdminGameStatsService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/games")
public class AdminGameStatsController {

    private final AdminGameStatsService service;

    public AdminGameStatsController(AdminGameStatsService service) {
        this.service = service;
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<AdminGameStatsResponse> getStats(
            @CookieValue(name = "trace_guest_token", required = false)
            String token,
            @PathVariable("id") String id
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.getStats(token, id));
    }
}