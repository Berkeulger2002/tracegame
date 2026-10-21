package com.trace.backend.model;

public enum BadgeCode {
    FIRST_WIN("İlk zinciri tamamla", 1),
    PERFECT_FIVE("5 Trace'i hatasız bitir", 5),
    STREAK_SEVEN("7 günü üst üste tamamla", 7),
    STREAK_THIRTY("30 günlük seriyi koru", 30),
    FAST_WIN("60 sn altında bitir", 1),
    FAST_FIVE("5 Trace'i 60 sn altında bitir", 5);

    private final String title;
    private final int target;

    BadgeCode(String title, int target) {
        this.title = title;
        this.target = target;
    }

    public String title() {
        return title;
    }

    public int target() {
        return target;
    }
}