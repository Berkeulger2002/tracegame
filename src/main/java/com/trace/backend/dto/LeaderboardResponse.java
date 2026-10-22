package com.trace.backend.dto;

import java.util.List;

public record LeaderboardResponse(
        String date,
        int page,
        int size,
        long totalPlayers,
        List<Entry> entries
) {
    public record Entry(
            long rank,
            String userName,
            int score,
            String status
    ) {
    }
    public record MyRanking(
            String date,
            String status,
            boolean ranked,
            Long rank,
            int score
    ) {
    }
}