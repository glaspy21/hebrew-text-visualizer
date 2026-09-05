package com.hebrewproject.service;

import org.springframework.stereotype.Component;

/**
 * Direct two-color linear blend, matching the validated Python prototype
 * (color_scale_v2.py):
 *   1st occurrence in range -> white (exact)
 *   2nd occurrence in range -> bright green (exact)
 *   maxOccurrence in range  -> dark red, almost black (exact)
 *   everything between 2 and max -> straight-line RGB blend between those
 *   two endpoints, positioned by (occurrence - 2) / (maxOccurrence - 2)
 *
 * Deliberately NOT using HSL hue rotation here (an earlier version did, and
 * it swept through yellow/orange on the way from green to red - a visual
 * side effect of how hue angles work, not something anyone asked for). This
 * version blends the R/G/B channels of two fixed endpoint colors directly,
 * so the only colors that ever appear are shades of green and shades of red.
 */
@Component
public class ColorScaleCalculator {

    private static final int[] WHITE = {255, 255, 255};
    private static final int[] BRIGHT_GREEN = {0, 200, 0};
    private static final int[] DARK_RED = {40, 0, 0}; // "almost black" red

    public String occurrenceToColor(int occurrence, int maxOccurrence) {
        if (occurrence <= 1) {
            return toHex(WHITE);
        }
        if (maxOccurrence <= 2) {
            return toHex(BRIGHT_GREEN); // edge case: no meaningful range yet
        }

        double t = (double) (occurrence - 2) / (double) (maxOccurrence - 2);
        t = Math.max(0.0, Math.min(t, 1.0)); // clamp

        int r = (int) Math.round(BRIGHT_GREEN[0] + (DARK_RED[0] - BRIGHT_GREEN[0]) * t);
        int g = (int) Math.round(BRIGHT_GREEN[1] + (DARK_RED[1] - BRIGHT_GREEN[1]) * t);
        int b = (int) Math.round(BRIGHT_GREEN[2] + (DARK_RED[2] - BRIGHT_GREEN[2]) * t);

        return toHex(new int[]{r, g, b});
    }

    private String toHex(int[] rgb) {
        return String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
    }
}
