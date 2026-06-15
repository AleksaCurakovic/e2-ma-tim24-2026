package com.example.myapplication.util;

public final class LeagueUtil {

    public static final int[] THRESHOLDS = {0, 100, 200, 400, 800, 1600};
    public static final String[] NAMES = {
            "Pocetnik",
            "Bronzana liga",
            "Srebrna liga",
            "Zlatna liga",
            "Dijamantska liga",
            "Legenda"
    };
    public static final String[] ICONS = {
            "league0",
            "league1",
            "league2",
            "league3",
            "league4",
            "league5"
    };

    private LeagueUtil() {}

    public static int levelForStars(long stars) {
        int level = 0;
        for (int i = 0; i < THRESHOLDS.length; i++) {
            if (stars >= THRESHOLDS[i]) {
                level = i;
            }
        }
        return level;
    }

    public static String nameForLevel(int level) {
        return NAMES[clamp(level)];
    }

    public static String iconForLevel(int level) {
        return ICONS[clamp(level)];
    }

    public static int dailyTokensForLevel(int level) {
        return 5 + Math.max(0, clamp(level));
    }

    public static int nextThresholdForLevel(int level) {
        int next = clamp(level) + 1;
        if (next >= THRESHOLDS.length) return -1;
        return THRESHOLDS[next];
    }

    public static int clamp(int level) {
        if (level < 0) return 0;
        if (level >= NAMES.length) return NAMES.length - 1;
        return level;
    }
}
