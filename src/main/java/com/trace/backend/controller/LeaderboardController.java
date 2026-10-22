package com.trace.backend.controller;
import com.trace.backend.model.TraceUser;
import com.trace.backend.service.GuestAuthService;
import org.springframework.web.bind.annotation.CookieValue;
import com.trace.backend.dto.LeaderboardResponse;
import com.trace.backend.model.TraceUser;
import com.trace.backend.service.GuestAuthService;
import com.trace.backend.service.LeaderboardService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardService service;
    private final GuestAuthService authService;

    public LeaderboardController(
            LeaderboardService service,
            GuestAuthService authService
    ) {
        this.service = service;
        this.authService = authService;
    }
    @GetMapping("/today")
    public ResponseEntity<LeaderboardResponse> today(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.today(page, size));
    }

    @GetMapping("/today/me")
    public ResponseEntity<LeaderboardResponse.MyRanking> me(
            @CookieValue(
                    name = "trace_guest_token",
                    required = false
            )
            String token
    ) {
        TraceUser user = authService.requireGuest(token);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.todayForUser(user.id()));
    }
}