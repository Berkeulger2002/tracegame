package com.trace.backend.service;

import com.trace.backend.dto.AttemptResponse;
import com.trace.backend.dto.HintRequest;
import com.trace.backend.model.Attempt;
import com.trace.backend.model.TraceGame;
import com.trace.backend.model.TraceUser;
import com.trace.backend.repository.AttemptRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class HintService {
    private final AttemptRepository repository;
    private final GuestAuthService guestAuthService;
    private final AttemptService attemptService;
    private final Clock gameClock;
    private final ActiveTimeService activeTimeService;
    private final SecureRandom random = new SecureRandom();

    public HintService(
            AttemptRepository repository,
            GuestAuthService guestAuthService,
            AttemptService attemptService,
            Clock gameClock,
            ActiveTimeService activeTimeService
    ) {
        this.repository = repository;
        this.guestAuthService = guestAuthService;
        this.attemptService = attemptService;
        this.gameClock = gameClock;
        this.activeTimeService = activeTimeService;
    }

    public AttemptResponse useHint(String token, HintRequest request) {
        TraceUser user = guestAuthService.requireGuest(token);
        String today = LocalDate.now(gameClock).toString();
        String attemptId = user.id() + ":" + today;

        Attempt attempt = repository.findById(attemptId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Önce bugünün oyununu başlatmalısın."
                ));

        if (attempt.status() != Attempt.Status.PLAYING) {
            throw conflict("Tamamlanmış oyunda joker kullanılamaz.");
        }

        if (!Objects.equals(attempt.version(), request.version())) {
            throw conflict("Oyun durumu değişmiş. Güncel durumu yüklemelisin.");
        }

        TraceGame.Step step = attempt.gameSnapshot().steps().stream()
                .filter(item ->
                        item.position() == attempt.currentStepPosition()
                )
                .findFirst()
                .orElseThrow(() -> conflict("Mevcut aşama bulunamadı."));

        if (!step.id().equals(request.stepId())) {
            throw conflict("Yalnızca bulunduğun aşamada joker kullanabilirsin.");
        }

        long usageCount = attempt.hints().stream()
                .filter(hint -> hint.type() == request.type())
                .count();

        int limit = request.type() == Attempt.HintType.ELIMINATE ? 2 : 1;

        if (usageCount >= limit) {
            throw conflict("Bu joker için hakkın kalmadı.");
        }

        boolean usedOnThisStep = attempt.hints().stream()
                .anyMatch(hint -> hint.stepPosition() == step.position());

        if (usedOnThisStep) {
            throw conflict(
                    "Bu aşamada zaten bir joker kullandın. "
                            + "Aynı aşamada ikinci joker kullanamazsın."
            );
        }

        List<TraceGame.Option> options =
                attemptService.availableOptions(attempt, step);

        if (options.size() < 2) {
            throw conflict("Tek seçenek kaldığı için joker kullanılamaz.");
        }

        activeTimeService.requireActive(attempt);

        String eliminatedOptionId = null;
        Integer audienceCorrectPercentage = null;

        if (request.type() == Attempt.HintType.ELIMINATE) {
            List<TraceGame.Option> wrongOptions = options.stream()
                    .filter(option ->
                            !option.id().equals(step.correctOptionId())
                    )
                    .toList();

            if (wrongOptions.isEmpty()) {
                throw conflict("Elenebilecek yanlış seçenek bulunamadı.");
            }

            eliminatedOptionId = wrongOptions
                    .get(random.nextInt(wrongOptions.size()))
                    .id();
        } else {
            audienceCorrectPercentage = 51 + random.nextInt(9);
        }

        List<Attempt.Hint> hints = new ArrayList<>(attempt.hints());

        hints.add(new Attempt.Hint(
                step.position(),
                request.type(),
                eliminatedOptionId,
                audienceCorrectPercentage,
                gameClock.instant()
        ));

        Attempt updated = new Attempt(
                attempt.id(),
                attempt.userId(),
                attempt.gameId(),
                attempt.date(),
                attempt.status(),
                attempt.currentStepPosition(),
                attempt.mistakeCount(),
                attempt.currentStepMistakeCount(),
                attempt.firstMistakeStepPosition(),
                attempt.startedAt(),
                attempt.answers(),
                hints,
                attempt.finishedAt(),
                attempt.activeDurationMillis(),
                attempt.activeTimeTracked(),
                attempt.gameSnapshot(),
                attempt.version()
        );

        try {
            Attempt saved = repository.save(updated);
            return attemptService.toResponse(saved);
        } catch (OptimisticLockingFailureException exception) {
            throw conflict(
                    "Başka bir istek oyunu güncelledi. "
                            + "Güncel durumu yüklemelisin."
            );
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}