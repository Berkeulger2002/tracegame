package com.trace.backend.service;

import com.trace.backend.dto.AnswerRequest;
import com.trace.backend.dto.AnswerResponse;
import com.trace.backend.dto.AttemptResponse;
import com.trace.backend.dto.TimerResponse;
import com.trace.backend.model.Attempt;
import com.trace.backend.model.TraceGame;
import com.trace.backend.model.TraceUser;
import com.trace.backend.repository.AttemptRepository;
import com.trace.backend.repository.TraceGameRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class AttemptService {
    private final AttemptRepository attemptRepository;
    private final TraceGameRepository gameRepository;
    private final GuestAuthService guestAuthService;
    private final Clock gameClock;
    private final ActiveTimeService activeTimeService;
    private final BadgeService badgeService;

    public AttemptService(
            AttemptRepository attemptRepository,
            TraceGameRepository gameRepository,
            GuestAuthService guestAuthService,
            Clock gameClock,
            ActiveTimeService activeTimeService,
            BadgeService badgeService
    ) {
        this.attemptRepository = attemptRepository;
        this.gameRepository = gameRepository;
        this.guestAuthService = guestAuthService;
        this.gameClock = gameClock;
        this.activeTimeService = activeTimeService;
        this.badgeService = badgeService;
    }

    public AttemptResponse startOrResume(String token) {
        TraceUser user = guestAuthService.requireGuest(token);
        String today = today();
        String attemptId = user.id() + ":" + today;

        Attempt existing = attemptRepository.findById(attemptId)
                .orElse(null);

        if (existing != null) {
            if (existing.status() == Attempt.Status.PLAYING) {
                activeTimeService.open(existing);
            } else if (existing.status() == Attempt.Status.WON) {
                badgeService.awardForUser(existing.userId());
            }

            return toResponse(existing);
        }

        TraceGame game = gameRepository.findByPublishDateAndStatus(
                today,
                TraceGame.GameStatus.PUBLISHED
        ).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Bugün için yayınlanmış oyun bulunamadı."
        ));

        if (game.steps() == null || game.steps().isEmpty()) {
            throw conflict("Yayınlanan oyunda aşama bulunmuyor.");
        }

        Attempt newAttempt = new Attempt(
                attemptId,
                user.id(),
                game.id(),
                today,
                Attempt.Status.PLAYING,
                1,
                0,
                0,
                null,
                gameClock.instant(),
                List.of(),
                List.of(),
                null,
                null,
                true,
                game,
                null
        );

        Attempt saved;

        try {
            saved = attemptRepository.insert(newAttempt);
        } catch (DuplicateKeyException exception) {
            saved = attemptRepository.findById(attemptId)
                    .orElseThrow(() -> exception);
        }

        if (saved.status() == Attempt.Status.PLAYING) {
            activeTimeService.open(saved);
        }

        return toResponse(saved);
    }

    public AnswerResponse answer(String token, AnswerRequest request) {
        TraceUser user = guestAuthService.requireGuest(token);
        String attemptId = user.id() + ":" + today();

        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Önce bugünün oyununu başlatmalısın."
                ));

        if (attempt.status() != Attempt.Status.PLAYING) {
            throw conflict("Bu oyun zaten tamamlandı.");
        }

        if (!Objects.equals(attempt.version(), request.version())) {
            throw conflict(
                    "Oyun durumu değişmiş. Güncel durumu tekrar yüklemelisin."
            );
        }

        TraceGame.Step step = currentStep(attempt);

        if (!step.id().equals(request.stepId())) {
            throw conflict("Yalnızca bulunduğun aşamaya cevap verebilirsin.");
        }

        boolean optionExists = step.options().stream()
                .anyMatch(option -> option.id().equals(request.optionId()));

        if (!optionExists) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Seçenek bu aşamaya ait değil."
            );
        }

        boolean alreadyAnswered = attempt.answers().stream()
                .anyMatch(answer ->
                        answer.stepPosition() == step.position()
                                && answer.optionId().equals(request.optionId())
                );

        if (alreadyAnswered) {
            throw conflict("Bu seçeneği daha önce denedin.");
        }

        boolean available = availableOptions(attempt, step).stream()
                .anyMatch(option -> option.id().equals(request.optionId()));

        if (!available) {
            throw conflict("Bu seçenek elenmiş; tekrar seçilemez.");
        }

        TimerResponse timer = activeTimeService.requireActive(attempt);
        boolean correct = step.correctOptionId().equals(request.optionId());
        Instant now = gameClock.instant();

        List<Attempt.Answer> answers = new ArrayList<>(attempt.answers());

        answers.add(new Attempt.Answer(
                step.position(),
                request.optionId(),
                correct,
                now
        ));

        int nextPosition = attempt.currentStepPosition();
        int totalMistakes = attempt.mistakeCount();
        int stepMistakes = attempt.currentStepMistakeCount();
        Integer firstMistakePosition = attempt.firstMistakeStepPosition();

        Attempt.Status status = Attempt.Status.PLAYING;
        Instant finishedAt = null;

        if (correct) {
            boolean lastStep =
                    step.position() == attempt.gameSnapshot().steps().size();

            if (lastStep) {
                status = Attempt.Status.WON;
                finishedAt = now;
            } else {
                nextPosition++;
                stepMistakes = 0;
            }
        } else {
            totalMistakes++;
            stepMistakes++;

            if (firstMistakePosition == null) {
                firstMistakePosition = step.position();
            }

            // Hangi aşamada olursa olsun toplam üçüncü yanlışta elenir.
            if (totalMistakes >= 3) {
                status = Attempt.Status.LOST;
                finishedAt = now;
            }
        }

        Attempt updated = new Attempt(
                attempt.id(),
                attempt.userId(),
                attempt.gameId(),
                attempt.date(),
                status,
                nextPosition,
                totalMistakes,
                stepMistakes,
                firstMistakePosition,
                attempt.startedAt(),
                answers,
                attempt.hints(),
                finishedAt,
                status == Attempt.Status.PLAYING
                        ? null
                        : timer.activeDurationMillis(),
                attempt.activeTimeTracked() && timer.speedBadgeEligible(),
                attempt.gameSnapshot(),
                attempt.version()
        );

        Attempt saved;

        try {
            saved = attemptRepository.save(updated);
        } catch (OptimisticLockingFailureException exception) {
            throw conflict(
                    "Başka bir istek oyunu güncelledi. "
                            + "Güncel durumu yüklemelisin."
            );
        }

        if (saved.status() == Attempt.Status.WON) {
            badgeService.awardForUser(saved.userId());
        }

        return new AnswerResponse(correct, toResponse(saved));
    }

    private TraceGame.Step currentStep(Attempt attempt) {
        return attempt.gameSnapshot().steps().stream()
                .filter(step ->
                        step.position() == attempt.currentStepPosition()
                )
                .findFirst()
                .orElseThrow(() ->
                        conflict("Oyuncunun bulunduğu aşama bulunamadı.")
                );
    }

    List<TraceGame.Option> availableOptions(
            Attempt attempt,
            TraceGame.Step step
    ) {
        List<String> wrongOptionIds = attempt.answers().stream()
                .filter(answer ->
                        answer.stepPosition() == step.position()
                                && !answer.correct()
                )
                .map(Attempt.Answer::optionId)
                .toList();

        List<String> eliminatedOptionIds = attempt.hints().stream()
                .filter(hint ->
                        hint.stepPosition() == step.position()
                                && hint.type() == Attempt.HintType.ELIMINATE
                )
                .map(Attempt.Hint::eliminatedOptionId)
                .toList();

        return step.options().stream()
                .filter(option -> !wrongOptionIds.contains(option.id()))
                .filter(option -> !eliminatedOptionIds.contains(option.id()))
                .toList();
    }

    private Map<String, Integer> audiencePercentages(
            Attempt attempt,
            TraceGame.Step step,
            List<TraceGame.Option> options
    ) {
        Attempt.Hint audienceHint = attempt.hints().stream()
                .filter(hint ->
                        hint.stepPosition() == step.position()
                                && hint.type() == Attempt.HintType.AUDIENCE
                )
                .findFirst()
                .orElse(null);

        if (audienceHint == null) {
            return Map.of();
        }

        Map<String, Integer> percentages = new LinkedHashMap<>();

        int wrongCount = options.size() - 1;
        int correctPercentage = wrongCount == 0
                ? 100
                : audienceHint.audienceCorrectPercentage();

        int remainingPercentage = 100 - correctPercentage;
        int wrongIndex = 0;

        for (TraceGame.Option option : options) {
            if (option.id().equals(step.correctOptionId())) {
                percentages.put(option.id(), correctPercentage);
            } else {
                int percentage = remainingPercentage / wrongCount;

                if (wrongIndex < remainingPercentage % wrongCount) {
                    percentage++;
                }

                percentages.put(option.id(), percentage);
                wrongIndex++;
            }
        }

        return percentages;
    }

    private List<AttemptResponse.CompletedStepResponse> completedSteps(
            Attempt attempt
    ) {
        return attempt.gameSnapshot().steps().stream()
                .filter(step ->
                        attempt.answers().stream().anyMatch(answer ->
                                answer.correct()
                                        && answer.stepPosition() == step.position()
                                        && Objects.equals(
                                        answer.optionId(),
                                        step.correctOptionId()
                                )
                        )
                )
                .sorted(Comparator.comparingInt(TraceGame.Step::position))
                .map(step -> {
                    String text = step.options().stream()
                            .filter(option -> Objects.equals(
                                    option.id(),
                                    step.correctOptionId()
                            ))
                            .map(TraceGame.Option::text)
                            .findFirst()
                            .orElseThrow(() ->
                                    conflict(
                                            "Tamamlanan adımın metni bulunamadı."
                                    )
                            );

                    int mistakeCount = (int) attempt.answers().stream()
                            .filter(answer ->
                                    answer.stepPosition() == step.position()
                                            && !answer.correct()
                            )
                            .count();

                    return new AttemptResponse.CompletedStepResponse(
                            step.id(),
                            step.position(),
                            text,
                            step.explanation(),
                            mistakeCount
                    );
                })
                .toList();
    }

    AttemptResponse toResponse(Attempt attempt) {
        TraceGame game = attempt.gameSnapshot();

        long eliminateCount = attempt.hints().stream()
                .filter(hint -> hint.type() == Attempt.HintType.ELIMINATE)
                .count();

        long audienceCount = attempt.hints().stream()
                .filter(hint -> hint.type() == Attempt.HintType.AUDIENCE)
                .count();

        AttemptResponse.StepResponse stepResponse = null;

        if (attempt.status() == Attempt.Status.PLAYING) {
            TraceGame.Step step = currentStep(attempt);
            List<TraceGame.Option> options = availableOptions(attempt, step);

            boolean eliminateUsed = attempt.hints().stream()
                    .anyMatch(hint ->
                            hint.stepPosition() == step.position()
                                    && hint.type() == Attempt.HintType.ELIMINATE
                    );

            boolean audienceUsed = attempt.hints().stream()
                    .anyMatch(hint ->
                            hint.stepPosition() == step.position()
                                    && hint.type() == Attempt.HintType.AUDIENCE
                    );

            stepResponse = new AttemptResponse.StepResponse(
                    step.id(),
                    step.position(),
                    options,
                    eliminateUsed,
                    audienceUsed,
                    audiencePercentages(attempt, step, options)
            );
        }

        return new AttemptResponse(
                attempt.id(),
                attempt.gameId(),
                attempt.date(),
                attempt.status().name(),
                game.title(),
                game.startEvent(),
                game.endEvent(),
                attempt.currentStepPosition(),
                game.steps().size(),
                attempt.mistakeCount(),
                attempt.currentStepMistakeCount(),
                attempt.firstMistakeStepPosition(),
                attempt.version(),
                Math.max(0, 2 - (int) eliminateCount),
                Math.max(0, 1 - (int) audienceCount),
                activeTimeService.view(attempt),
                stepResponse,
                game.startExplanation(),
                game.endExplanation(),
                completedSteps(attempt),
                attempt.score(),
                attempt.status() != Attempt.Status.PLAYING,
                Math.max(0, 3 - attempt.mistakeCount())
        );
    }

    private String today() {
        return LocalDate.now(gameClock).toString();
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
