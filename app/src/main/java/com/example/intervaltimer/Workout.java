package com.example.intervaltimer;

import java.util.Locale;

public final class Workout {
    public final String name;
    public final int preparationSeconds;
    public final int workSeconds;
    public final int restSeconds;
    public final int repeats;

    public Workout(String name, int preparationSeconds, int workSeconds, int restSeconds, int repeats) {
        if (preparationSeconds < 0 || workSeconds < 1 || restSeconds < 1 || repeats < 1) {
            throw new IllegalArgumentException("Workout durations and repeats must be positive");
        }
        this.name = name == null ? "" : name.trim();
        this.preparationSeconds = preparationSeconds;
        this.workSeconds = workSeconds;
        this.restSeconds = restSeconds;
        this.repeats = repeats;
    }

    public String displayName() {
        if (!name.isEmpty()) {
            return name;
        }
        return String.format(Locale.getDefault(), "%d x %s work, %s rest",
                repeats, formatDuration(workSeconds), formatDuration(restSeconds));
    }

    public static String formatDuration(int seconds) {
        return String.format(Locale.getDefault(), "%d:%02d", seconds / 60, seconds % 60);
    }
}
