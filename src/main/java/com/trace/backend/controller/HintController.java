package com.trace.backend.controller;

import com.trace.backend.dto.AttemptResponse;
import com.trace.backend.dto.HintRequest;
import com.trace.backend.service.HintService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attempts")
public class HintController {

    private final HintService service;

    public HintController(HintService service) {
        this.service = service;
    }

    @PostMapping("/today/hint")
    public ResponseEntity<AttemptResponse> useHint(
            @CookieValue(
                    name = "trace_guest_token",
                    required = false
            ) String token,
            @Valid @RequestBody HintRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.useHint(token, request));
    }
}