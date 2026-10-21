package com.trace.backend.service;

import com.trace.backend.dto.TodayGameResponse;
import com.trace.backend.model.TraceGame;
import com.trace.backend.repository.TraceGameRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class TraceGameService {

    private final TraceGameRepository repository;
    private final Clock gameClock;

    public TraceGameService(
            TraceGameRepository repository,
            Clock gameClock
    ) {
        this.repository = repository;
        this.gameClock = gameClock;
    }

    public TodayGameResponse getTodayGame() {
        String today = LocalDate.now(gameClock).toString();

        TraceGame game = repository.findByPublishDateAndStatus(
                today,
                TraceGame.GameStatus.PUBLISHED
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Bugün için yayınlanmış oyun bulunamadı."
        ));

        return new TodayGameResponse(
                game.id(),
                game.title(),
                game.langCode(),
                game.publishDate(),
                game.difficulty().name(),
                game.startEvent(),
                game.endEvent(),
                game.steps().size()
        );
    }
}