package com.trace.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

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
        if (score == null) {
            score = calculateScore(
                    status,
                    answers,
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
            int mistakeCount,
            Long activeDurationMillis,
            boolean activeTimeTracked
    ) {
        long correctStepCount = answers.stream()
                .filter(Answer::correct)
                .map(Answer::stepPosition)
                .distinct()
                .count();

        long correctPoints = correctStepCount * 100L;
        long mistakePenalty = Math.max(0, mistakeCount) * 25L;

        long completionBonus = 0;
        long speedBonus = 0;

        if (status == Status.WON) {
            completionBonus = 200;

            if (activeTimeTracked
                    && activeDurationMillis != null
                    && activeDurationMillis >= 0) {
                long activeSeconds = activeDurationMillis / 1000L;
                speedBonus = Math.max(0L, 100L - activeSeconds);
            }
        }

        long total = correctPoints
                - mistakePenalty
                + completionBonus
                + speedBonus;

        return (int) Math.min(
                Integer.MAX_VALUE,
                Math.max(0L, total)
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