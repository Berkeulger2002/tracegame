package com.trace.backend.service;

import com.trace.backend.dto.BadgeResponse;
import com.trace.backend.model.Attempt;
import com.trace.backend.model.BadgeCode;
import com.trace.backend.model.TraceUser;
import com.trace.backend.model.UserBadge;
import com.trace.backend.repository.AttemptRepository;
import com.trace.backend.repository.UserBadgeRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Service
public class BadgeService {
    private final AttemptRepository attemptRepository;
    private final UserBadgeRepository badgeRepository;
    private final GuestAuthService guestAuthService;
    private final Clock gameClock;

    public BadgeService(
            AttemptRepository attemptRepository,
            UserBadgeRepository badgeRepository,
            GuestAuthService guestAuthService,
            Clock gameClock
    ) {
        this.attemptRepository = attemptRepository;
        this.badgeRepository = badgeRepository;
        this.guestAuthService = guestAuthService;
        this.gameClock = gameClock;
    }

    public List<BadgeResponse> getBadges(String token) {
        TraceUser user = guestAuthService.requireGuest(token);
        return evaluate(user.id(), false);
    }

    public List<BadgeResponse> syncBadges(String token) {
        TraceUser user = guestAuthService.requireGuest(token);
        return evaluate(user.id(), true);
    }

    public void awardForUser(String userId) {
        evaluate(userId, true);
    }

    private List<BadgeResponse> evaluate(
            String userId,
            boolean saveNewBadges
    ) {
        List<Attempt> wins = attemptRepository.findByUserIdAndStatus(
                userId,
                Attempt.Status.WON
        );

        int winCount = wins.size();

        int perfectCount = (int) wins.stream()
                .filter(attempt -> attempt.mistakeCount() == 0)
                .count();

        int fastCount = (int) wins.stream()
                .filter(attempt -> ActiveTimePolicy.fastWin(
                        attempt.activeTimeTracked(),
                        attempt.activeDurationMillis()
                ))
                .count();

        int bestStreak = calculateBestStreak(wins);

        Map<BadgeCode, UserBadge> earnedBadges =
                new EnumMap<>(BadgeCode.class);

        for (UserBadge badge : badgeRepository.findByUserId(userId)) {
            earnedBadges.put(badge.badgeCode(), badge);
        }

        List<BadgeResponse> responses = new ArrayList<>();

        for (BadgeCode code : BadgeCode.values()) {
            int actualProgress = switch (code) {
                case FIRST_WIN -> winCount;
                case PERFECT_FIVE -> perfectCount;
                case FAST_WIN, FAST_FIVE -> fastCount;
                case STREAK_SEVEN, STREAK_THIRTY -> bestStreak;
            };

            UserBadge earnedBadge = earnedBadges.get(code);
            boolean qualifies = actualProgress >= code.target();

            if (saveNewBadges && earnedBadge == null && qualifies) {
                earnedBadge = awardOnce(userId, code);
                earnedBadges.put(code, earnedBadge);
            }

            boolean earned = earnedBadge != null;

            int progress = earned
                    ? code.target()
                    : Math.min(actualProgress, code.target());

            responses.add(new BadgeResponse(
                    code.name(),
                    code.title(),
                    progress,
                    code.target(),
                    earned,
                    earned ? earnedBadge.earnedAt() : null
            ));
        }

        return responses;
    }

    private UserBadge awardOnce(String userId, BadgeCode code) {
        String id = userId + ":" + code.name();

        UserBadge badge = new UserBadge(
                id,
                userId,
                code,
                gameClock.instant().truncatedTo(ChronoUnit.MILLIS)
        );

        try {
            return badgeRepository.insert(badge);
        } catch (DuplicateKeyException exception) {
            return badgeRepository.findById(id)
                    .orElseThrow(() -> exception);
        }
    }

    private int calculateBestStreak(List<Attempt> wins) {
        TreeSet<LocalDate> dates = new TreeSet<>();

        for (Attempt attempt : wins) {
            dates.add(LocalDate.parse(attempt.date()));
        }

        int bestStreak = 0;
        int currentStreak = 0;
        LocalDate previousDate = null;

        for (LocalDate date : dates) {
            if (previousDate != null
                    && date.equals(previousDate.plusDays(1))) {
                currentStreak++;
            } else {
                currentStreak = 1;
            }

            bestStreak = Math.max(bestStreak, currentStreak);
            previousDate = date;
        }

        return bestStreak;
    }
}