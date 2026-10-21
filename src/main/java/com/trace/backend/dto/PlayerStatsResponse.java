package com.trace.backend.dto;

import java.util.List;

public record PlayerStatsResponse(
        int gamesPlayed,
        int gamesWon,
        int gamesLost,
        double winRate,
        int currentStreak,
        int bestStreak,
        String weekStartDate,
        List<WeekDayResponse> weekDays
) {
    public record WeekDayResponse(
            String date,
            int dayOfWeek,
            boolean today,
            String status
    ) {
    }
}