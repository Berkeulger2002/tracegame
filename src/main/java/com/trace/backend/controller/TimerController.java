package com.trace.backend.controller;

import com.trace.backend.dto.TimerRequest;
import com.trace.backend.dto.TimerResponse;
import com.trace.backend.service.ActiveTimeService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attempts/today/timer")
public class TimerController {
    private final ActiveTimeService service;

    public TimerController(ActiveTimeService service) {
        this.service = service;
    }

    @PostMapping("/resume")
    public ResponseEntity<TimerResponse> resume(
            @CookieValue(name = "trace_guest_token", required = false)
            String token
    ) {
        return result(service.resume(token));
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<TimerResponse> heartbeat(
            @CookieValue(name = "trace_guest_token", required = false)
            String token,
            @Valid @RequestBody TimerRequest request
    ) {
        return result(service.heartbeat(token, request));
    }

    @PostMapping("/pause")
    public ResponseEntity<TimerResponse> pause(
            @CookieValue(name = "trace_guest_token", required = false)
            String token,
            @Valid @RequestBody TimerRequest request
    ) {
        return result(service.pause(token, request));
    }

    private ResponseEntity<TimerResponse> result(TimerResponse timer) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(timer);
    }
}