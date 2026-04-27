package com.fyp.floodmonitoring.util;

import java.util.Map;

/**
 * Calibration constants that translate integer flood-level codes (0–3) to
 * human-readable values.  Centralised here so any future recalibration only
 * needs to happen in one place.
 *
 * <pre>
 *  Level 0 → Dry     →  0.0 m
 *  Level 1 → Normal  →  1.0 m
 *  Level 2 → Warning →  2.5 m
 *  Level 3 → Critical → 4.0 m
 * </pre>
 */
public final class WaterLevel {

    private WaterLevel() {}

    private static final Map<Integer, Double> TO_METERS =
            Map.of(0, 0.0, 1, 1.0, 2, 2.5, 3, 4.0);

    private static final Map<Integer, String> TO_LABEL =
            Map.of(0, "0.0m", 1, "1.0m", 2, "2.5m", 3, "4.0m");

    /**
     * Returns the water depth in metres for the given level code.
     * Falls back to 0.0 for unknown codes.
     */
    public static double toMeters(int level) {
        return TO_METERS.getOrDefault(level, 0.0);
    }

    /**
     * Returns the display label (e.g. "2.5m") for the given level code.
     * Falls back to "0.0m" for unknown codes.
     */
    public static String toLabel(int level) {
        return TO_LABEL.getOrDefault(level, "0.0m");
    }
}
