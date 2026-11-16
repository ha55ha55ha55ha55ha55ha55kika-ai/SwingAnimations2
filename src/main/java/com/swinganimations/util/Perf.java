package com.swinganimations.util;

/**
 * Общий уровень качества визуалов мода: 0 = полное, 1 = облегчённое, 2 = минимальное.
 * Выставляется модулем Performance (адаптивное качество по FPS), читается остальными модулями.
 */
public final class Perf {
    private static int quality;

    private Perf() {}

    public static int quality() { return quality; }

    public static void setQuality(int q) { quality = Math.max(0, Math.min(2, q)); }
}
