package com.trace.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Document(collection = "attempt")
public record Attempt(
        @Id String id,
        String userId,
        String gameId,
        String date,
        Status status,
        int currentStepPosition,
        int mistakeCount,
        int currentStepMistakeCount,
        Integer firstMistakeStepPosition,
        Instant startedAt,
        List<Answer> answers,
        List<Hint> hints,
        Instant finishedAt,
        Long activeDurationMillis,
        Boolean activeTimeTracked,
        TraceGame gameSnapshot,
        @Version Long version,
        Integer score
) {
    @PersistenceCreator
    public Attempt {
        activeTimeTracked = Boolean.TRUE.equals(activeTimeTracked);
        answers = answers == null ? List.of() : List.copyOf(answers);
        hints = hints == null ? List.of() : List.copyOf(hints);

        // Eski kayıtlarda score bulunmayabilir.
        // Yeni Attempt oluşturulurken de puanı sunucu hesaplar.
        if (status == Status.LOST) {
            score = 0;
        } else if (score == null) {
            score = calculateScore(
                    status,
                    answers,
                    hints,
                    mistakeCount,
                    activeDurationMillis,
                    activeTimeTracked
            );
        }
    }

    // Mevcut servislerdeki constructor çağrılarıyla uyumluluk.
    public Attempt(
            String id,
            String userId,
            String gameId,
            String date,
            Status status,
            int currentStepPosition,
            int mistakeCount,
            int currentStepMistakeCount,
            Integer firstMistakeStepPosition,
            Instant startedAt,
            List<Answer> answers,
            List<Hint> hints,
            Instant finishedAt,
            Long activeDurationMillis,
            Boolean activeTimeTracked,
            TraceGame gameSnapshot,
            Long version
    ) {
        this(
                id,
                userId,
                gameId,
                date,
                status,
                currentStepPosition,
                mistakeCount,
                currentStepMistakeCount,
                firstMistakeStepPosition,
                startedAt,
                answers,
                hints,
                finishedAt,
                activeDurationMillis,
                activeTimeTracked,
                gameSnapshot,
                version,
                null
        );
    }

    private static int calculateScore(
            Status status,
            List<Answer> answers,
            List<Hint> hints,
            int mistakeCount,
            Long activeDurationMillis,
            boolean activeTimeTracked
    ) {
        Map<Integer, Integer> mistakesByStep = new HashMap<>();
        Set<Integer> completedSteps = new HashSet<>();
        long stepPoints = 0;

        // Cevaplar servis tarafından gönderilme sırasıyla eklenir.
        // Yalnızca doğru tamamlanan adım puan kazandırır.
        for (Answer answer : answers) {
            int position = answer.stepPosition();
            if (completedSteps.contains(position)) {
                continue;
            }
            if (!answer.correct()) {
                mistakesByStep.merge(position, 1, Integer::sum);
                continue;
            }

            int mistakes = mistakesByStep.getOrDefault(position, 0);
            stepPoints += switch (mistakes) {
                case 0 -> 20;
                case 1 -> 14;
                case 2 -> 8;
                default -> 0;
            };
            completedSteps.add(position);
        }

        long perfectBonus = 0;
        long noHintBonus = 0;
        long speedBonus = 0;

        // Elenen veya devam eden oyuncu yalnızca adım puanlarını korur.
        if (status == Status.WON) {
            boolean noWrongAnswers = answers.stream().allMatch(Answer::correct);
            if (mistakeCount == 0 && noWrongAnswers) {
                perfectBonus = 50;
            }
            if (hints.isEmpty()) {
                noHintBonus = 20;
            }

            // Güvenilir aktif süre yoksa zaman bonusu verilmez.
            if (activeTimeTracked
                    && activeDurationMillis != null
                    && activeDurationMillis >= 0) {
                long elapsedAfterGrace = Math.max(0L, activeDurationMillis - 15_000L);
                // İlk kesinti 19. saniyede; sonra her 4 saniyede bir.
                speedBonus = Math.max(0L, 30L - elapsedAfterGrace / 4_000L);
            }
        }

        return (int) Math.min(
                Integer.MAX_VALUE,
                stepPoints + perfectBonus + noHintBonus + speedBonus
        );
    }

    public enum Status {
        PLAYING, WON, LOST
    }

    public enum HintType {
        ELIMINATE, AUDIENCE
    }

    public record Answer(
            int stepPosition,
            String optionId,
            boolean correct,
            Instant answeredAt
    ) {
    }

    public record Hint(
            int stepPosition,
            HintType type,
            String eliminatedOptionId,
            Integer audienceCorrectPercentage,
            Instant usedAt
    ) {
    }
}