package com.trace.backend.service;

import com.trace.backend.dto.TimerRequest;
import com.trace.backend.dto.TimerResponse;
import com.trace.backend.model.Attempt;
import com.trace.backend.model.GameTimer;
import com.trace.backend.model.TraceUser;
import com.trace.backend.repository.AttemptRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ActiveTimeService {
    private static final int MAX_RETRIES = 5;

    private final MongoTemplate mongoTemplate;
    private final AttemptRepository attemptRepository;
    private final GuestAuthService guestAuthService;
    private final Clock gameClock;

    public ActiveTimeService(
            MongoTemplate mongoTemplate,
            AttemptRepository attemptRepository,
            GuestAuthService guestAuthService,
            Clock gameClock
    ) {
        this.mongoTemplate = mongoTemplate;
        this.attemptRepository = attemptRepository;
        this.guestAuthService = guestAuthService;
        this.gameClock = gameClock;
    }

    public TimerResponse open(Attempt attempt) {
        if (attempt.status() != Attempt.Status.PLAYING) {
            return view(attempt);
        }

        requireToday(attempt);
        String sessionId = UUID.randomUUID().toString();

        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            Instant now = now();

            GameTimer previous = mongoTemplate.findById(
                    attempt.id(),
                    GameTimer.class
            );

            GameTimer updated = new GameTimer(
                    attempt.id(),
                    attempt.userId(),
                    sessionId,
                    0,
                    previous == null ? 0 : elapsed(previous, now),
                    true,
                    attempt.activeTimeTracked() && (
                            previous == null
                                    || previous.reliable()
                                    && !expired(previous, now)
                    ),
                    now,
                    previous == null ? null : previous.version()
            );

            try {
                GameTimer saved = previous == null
                        ? mongoTemplate.insert(updated)
                        : mongoTemplate.save(updated);

                return response(attempt, saved, now);
            } catch (DuplicateKeyException
                     | OptimisticLockingFailureException exception) {
                // Eş zamanlı güncellemede güncel belgeyi tekrar oku.
            }
        }

        throw conflict("Süre oturumu değişti. Tekrar başlatmayı dene.");
    }

    public TimerResponse resume(String token) {
        return open(loadPlaying(token));
    }

    public TimerResponse heartbeat(String token, TimerRequest request) {
        return update(loadPlaying(token), request, false);
    }

    public TimerResponse pause(String token, TimerRequest request) {
        return update(loadPlaying(token), request, true);
    }

    public TimerResponse requireActive(Attempt attempt) {
        TimerResponse timer = view(attempt);

        if (!timer.running()) {
            throw conflict(
                    "Süre duraklatılmış veya bağlantı kesilmiş. "
                            + "Önce oyuna devam et."
            );
        }

        return timer;
    }

    public TimerResponse view(Attempt attempt) {
        if (attempt.status() != Attempt.Status.PLAYING) {
            return new TimerResponse(
                    null,
                    0,
                    attempt.activeDurationMillis() == null
                            ? 0
                            : attempt.activeDurationMillis(),
                    false,
                    attempt.activeTimeTracked()
                            && attempt.activeDurationMillis() != null,
                    ActiveTimePolicy.HEARTBEAT_INTERVAL_MILLIS,
                    ActiveTimePolicy.LEASE_TIMEOUT_MILLIS
            );
        }

        GameTimer timer = mongoTemplate.findById(
                attempt.id(),
                GameTimer.class
        );

        if (timer == null) {
            return new TimerResponse(
                    null,
                    0,
                    0,
                    false,
                    false,
                    ActiveTimePolicy.HEARTBEAT_INTERVAL_MILLIS,
                    ActiveTimePolicy.LEASE_TIMEOUT_MILLIS
            );
        }

        return response(attempt, timer, now());
    }

    private TimerResponse update(
            Attempt attempt,
            TimerRequest request,
            boolean pause
    ) {
        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            requireToday(attempt);
            Instant now = now();

            GameTimer previous = mongoTemplate.findById(
                    attempt.id(),
                    GameTimer.class
            );

            if (previous == null
                    || !previous.sessionId().equals(request.sessionId())) {
                throw conflict(
                        "Süre oturumu geçersiz. "
                                + "Oyun başka bir sekmede açılmış olabilir."
                );
            }

            if (request.sequence() <= previous.sequence()
                    || !previous.running()) {
                return response(attempt, previous, now);
            }

            boolean expired = expired(previous, now);

            GameTimer updated = new GameTimer(
                    previous.id(),
                    previous.userId(),
                    previous.sessionId(),
                    request.sequence(),
                    elapsed(previous, now),
                    !pause && !expired,
                    previous.reliable() && !expired,
                    now,
                    previous.version()
            );

            try {
                GameTimer saved = mongoTemplate.save(updated);
                return response(attempt, saved, now);
            } catch (OptimisticLockingFailureException exception) {
                // Yeni sürümü okuyup aynı isteği tekrar değerlendir.
            }
        }

        throw conflict("Süre aynı anda güncellendi. İsteği tekrar dene.");
    }

    private Attempt loadPlaying(String token) {
        TraceUser user = guestAuthService.requireGuest(token);
        String id = user.id() + ":" + LocalDate.now(gameClock);

        Attempt attempt = attemptRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Önce bugünün oyununu başlatmalısın."
                ));

        if (attempt.status() != Attempt.Status.PLAYING) {
            throw conflict("Tamamlanmış oyunun süresi değiştirilemez.");
        }

        return attempt;
    }

    private TimerResponse response(
            Attempt attempt,
            GameTimer timer,
            Instant now
    ) {
        boolean expired = expired(timer, now);

        return new TimerResponse(
                timer.sessionId(),
                timer.sequence(),
                elapsed(timer, now),
                timer.running() && !expired,
                attempt.activeTimeTracked()
                        && timer.reliable()
                        && !expired,
                ActiveTimePolicy.HEARTBEAT_INTERVAL_MILLIS,
                ActiveTimePolicy.LEASE_TIMEOUT_MILLIS
        );
    }

    private long elapsed(GameTimer timer, Instant now) {
        return ActiveTimePolicy.elapsed(
                timer.elapsedMillis(),
                timer.lastSeenAt(),
                timer.running(),
                now
        );
    }

    private boolean expired(GameTimer timer, Instant now) {
        return ActiveTimePolicy.expired(
                timer.running(),
                timer.lastSeenAt(),
                now
        );
    }

    private void requireToday(Attempt attempt) {
        String today = LocalDate.now(gameClock).toString();

        if (!attempt.date().equals(today)) {
            throw conflict("Bu oyun artık bugüne ait değil.");
        }
    }

    private Instant now() {
        return gameClock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message
        );
    }
}