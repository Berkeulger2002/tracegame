package com.trace.backend.controller;

import com.trace.backend.dto.TodayGameResponse;
import com.trace.backend.dto.TomorrowGameResponse;
import com.trace.backend.service.GamePreviewService;
import com.trace.backend.service.TraceGameService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
public class TraceGameController {

    private final TraceGameService service;
    private final GamePreviewService previewService;

    public TraceGameController(
            TraceGameService service,
            GamePreviewService previewService
    ) {
        this.service = service;
        this.previewService = previewService;
    }

    @GetMapping("/today")
    public TodayGameResponse getTodayGame() {
        return service.getTodayGame();
    }

    @GetMapping("/tomorrow")
    public ResponseEntity<TomorrowGameResponse> getTomorrowGame() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(previewService.getTomorrow());
    }
}