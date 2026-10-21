package com.trace.backend.service;

import com.trace.backend.dto.TomorrowGameResponse;
import com.trace.backend.model.TraceGame;
import com.trace.backend.repository.TraceGameRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class GamePreviewService {

    private final TraceGameRepository gameRepository;
    private final Clock gameClock;

    public GamePreviewService(
            TraceGameRepository gameRepository,
            Clock gameClock
    ) {
        this.gameRepository = gameRepository;
        this.gameClock = gameClock;
    }

    public TomorrowGameResponse getTomorrow() {
        Instant now = gameClock.instant();
        ZoneId zone = gameClock.getZone();

        LocalDate tomorrow = LocalDate.ofInstant(now, zone)
                .plusDays(1);

        Instant unlockAt = tomorrow.atStartOfDay(zone).toInstant();

        long remainingSeconds = Math.max(
                0,
                Duration.between(now, unlockAt).getSeconds()
        );

        TraceGame game = gameRepository.findByPublishDateAndStatus(
                tomorrow.toString(),
                TraceGame.GameStatus.PUBLISHED
        ).orElse(null);

        return new TomorrowGameResponse(
                game != null,
                tomorrow.toString(),
                game == null ? null : game.title(),
                game == null ? null : game.startEvent(),
                game == null ? null : game.endEvent(),
                unlockAt,
                now,
                remainingSeconds
        );
    }
}