package com.trace.backend.dto;

import com.trace.backend.model.TraceGame;

import java.util.List;
import java.util.Map;

public record AttemptResponse(
        String attemptId,
        String gameId,
        String date,
        String status,
        String title,
        String startEvent,
        String endEvent,
        int currentStepPosition,
        int totalSteps,
        int mistakeCount,
        int currentStepMistakeCount,
        Integer firstMistakeStepPosition,
        Long version,
        int remainingEliminateUses,
        int remainingAudienceUses,
        TimerResponse timer,
        StepResponse currentStep,
        String startExplanation,
        String endExplanation,
        List<CompletedStepResponse> completedSteps,
        int score,
        boolean scoreFinal,
        int remainingLives
) {
    public record StepResponse(
            String id,
            int position,
            List<TraceGame.Option> options,
            boolean eliminateUsed,
            boolean audienceUsed,
            Map<String, Integer> audiencePercentages
    ) {
    }

    public record CompletedStepResponse(
            String id,
            int position,
            String text,
            String explanation,
            int mistakeCount
    ) {
    }
}