package com.trace.backend.controller;

import com.trace.backend.dto.AnswerRequest;
import com.trace.backend.dto.AnswerResponse;
import com.trace.backend.dto.AttemptResponse;
import com.trace.backend.service.AttemptService;
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
public class AttemptController {

    private final AttemptService service;

    public AttemptController(AttemptService service) {
        this.service = service;
    }

    @PostMapping("/today/start")
    public ResponseEntity<AttemptResponse> startOrResume(
            @CookieValue(
                    name = "trace_guest_token",
                    required = false
            ) String token
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.startOrResume(token));
    }

    @PostMapping("/today/answer")
    public ResponseEntity<AnswerResponse> answer(
            @CookieValue(
                    name = "trace_guest_token",
                    required = false
            ) String token,
            @Valid @RequestBody AnswerRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.answer(token, request));
    }
}