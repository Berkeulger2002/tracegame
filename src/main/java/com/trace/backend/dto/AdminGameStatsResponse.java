package com.trace.backend.dto;

public record AdminGameStatsResponse(
        String gameId,
        String title,
        String publishDate,
        long startedPlayers,
        long wonPlayers,
        long lostPlayers,
        long unfinishedPlayers,
        double winRate
) {
}