package com.trace.backend.service;

import com.trace.backend.dto.PlayerStatsResponse;
import com.trace.backend.model.Attempt;
import com.trace.backend.model.TraceUser;
import com.trace.backend.repository.AttemptRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
public class PlayerStatsService {

    private final AttemptRepository attemptRepository;
    private final GuestAuthService guestAuthService;
    private final Clock gameClock;

    public PlayerStatsService(
            AttemptRepository attemptRepository,
            GuestAuthService guestAuthService,
            Clock gameClock
    ) {
        this.attemptRepository = attemptRepository;
        this.guestAuthService = guestAuthService;
        this.gameClock = gameClock;
    }

    public PlayerStatsResponse getStats(String token) {
        TraceUser user = guestAuthService.requireGuest(token);
        LocalDate today = LocalDate.now(gameClock);

        List<Attempt> wins = attemptRepository.findByUserIdAndStatus(
                user.id(),
                Attempt.Status.WON
        );

        List<Attempt> losses = attemptRepository.findByUserIdAndStatus(
                user.id(),
                Attempt.Status.LOST
        );

        TreeSet<LocalDate> winDates = datesThroughToday(wins, today);
        TreeSet<LocalDate> lossDates = datesThroughToday(losses, today);

        int gamesWon = winDates.size();
        int gamesLost = lossDates.size();
        int gamesPlayed = gamesWon + gamesLost;

        double winRate = gamesPlayed == 0
                ? 0.0
                : Math.round(gamesWon * 1000.0 / gamesPlayed) / 10.0;

        LocalDate weekStart = today.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );

        return new PlayerStatsResponse(
                gamesPlayed,
                gamesWon,
                gamesLost,
                winRate,
                calculateCurrentStreak(winDates, lossDates, today),
                calculateBestStreak(winDates),
                weekStart.toString(),
                buildWeekDays(user.id(), today, weekStart)
        );
    }

    private List<PlayerStatsResponse.WeekDayResponse> buildWeekDays(
            String userId,
            LocalDate today,
            LocalDate weekStart
    ) {
        List<String> attemptIds = new ArrayList<>();

        // AttemptService ile aynı kimlik yapısı: kullanıcıId:tarih.
        // Yalnızca bu haftanın bugün ve geçmiş günlerini sorgular.
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);

            if (!date.isAfter(today)) {
                attemptIds.add(userId + ":" + date);
            }
        }

        Map<String, Attempt.Status> statuses = new HashMap<>();

        for (Attempt attempt : attemptRepository.findAllById(attemptIds)) {
            if (userId.equals(attempt.userId())) {
                statuses.put(attempt.date(), attempt.status());
            }
        }

        List<PlayerStatsResponse.WeekDayResponse> days = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStart.plusDays(i);
            String status;

            if (date.isAfter(today)) {
                status = "FUTURE";
            } else {
                Attempt.Status savedStatus = statuses.get(date.toString());

                status = savedStatus == null
                        ? "NOT_PLAYED"
                        : savedStatus.name();
            }

            days.add(new PlayerStatsResponse.WeekDayResponse(
                    date.toString(),
                    date.getDayOfWeek().getValue(),
                    date.equals(today),
                    status
            ));
        }

        return List.copyOf(days);
    }

    private TreeSet<LocalDate> datesThroughToday(
            List<Attempt> attempts,
            LocalDate today
    ) {
        TreeSet<LocalDate> dates = new TreeSet<>();

        for (Attempt attempt : attempts) {
            LocalDate date = LocalDate.parse(attempt.date());

            if (!date.isAfter(today)) {
                dates.add(date);
            }
        }

        return dates;
    }

    private int calculateCurrentStreak(
            TreeSet<LocalDate> winDates,
            TreeSet<LocalDate> lossDates,
            LocalDate today
    ) {
        if (lossDates.contains(today)) {
            return 0;
        }

        LocalDate date = winDates.contains(today)
                ? today
                : today.minusDays(1);

        int streak = 0;

        while (winDates.contains(date)) {
            streak++;
            date = date.minusDays(1);
        }

        return streak;
    }

    private int calculateBestStreak(TreeSet<LocalDate> winDates) {
        int bestStreak = 0;
        int streak = 0;
        LocalDate previousDate = null;

        for (LocalDate date : winDates) {
            if (previousDate != null
                    && date.equals(previousDate.plusDays(1))) {
                streak++;
            } else {
                streak = 1;
            }

            bestStreak = Math.max(bestStreak, streak);
            previousDate = date;
        }

        return bestStreak;
    }
}