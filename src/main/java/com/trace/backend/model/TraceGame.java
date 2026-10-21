package com.trace.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "trace_game")
public record TraceGame(
        @Id String id,
        String title,
        String langCode,
        String publishDate,
        Difficulty difficulty,
        GameStatus status,
        String startEvent,
        String endEvent,
        List<Step> steps,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String updatedBy,
        String startExplanation,
        String endExplanation
) {
    @PersistenceCreator
    public TraceGame {
        startExplanation = startExplanation == null
                ? ""
                : startExplanation.strip();

        endExplanation = endExplanation == null
                ? ""
                : endExplanation.strip();
    }

    public TraceGame(
            String id,
            String title,
            String langCode,
            String publishDate,
            Difficulty difficulty,
            GameStatus status,
            String startEvent,
            String endEvent,
            List<Step> steps,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            String updatedBy
    ) {
        this(
                id,
                title,
                langCode,
                publishDate,
                difficulty,
                status,
                startEvent,
                endEvent,
                steps,
                createdAt,
                updatedAt,
                createdBy,
                updatedBy,
                "",
                ""
        );
    }

    public enum Difficulty {
        EASY, MEDIUM, HARD
    }

    public enum GameStatus {
        DRAFT, PUBLISHED
    }

    public record Step(
            String id,
            int position,
            List<Option> options,
            String correctOptionId,
            String explanation
    ) {
        @PersistenceCreator
        public Step {
            explanation = explanation == null
                    ? ""
                    : explanation.strip();
        }

        public Step(
                String id,
                int position,
                List<Option> options,
                String correctOptionId
        ) {
            this(id, position, options, correctOptionId, "");
        }
    }

    public record Option(
            String id,
            String text
    ) {
    }
}