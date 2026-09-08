package com.hebrewproject.service;

import org.springframework.stereotype.Component;

/**
 * Direct two-color linear blend per theme, matching the validated Python
 * prototype's original shape (color_scale_v2.py):
 *   1st occurrence in range -> no highlight (null/null - the frontend
 *                              decides this from countInRange, not a color)
 *   2nd occurrence in range -> the theme's green endpoint (exact)
 *   maxOccurrence in range  -> the theme's red endpoint (exact)
 *   everything between 2 and max -> straight-line RGB blend between those
 *   two endpoints, positioned by (occurrence - 2) / (maxOccurrence - 2)
 *
 * Deliberately NOT using HSL hue rotation here (an earlier version did, and
 * it swept through yellow/orange on the way from green to red - a visual
 * side effect of how hue angles work, not something anyone asked for). This
 * blends the R/G/B channels of two fixed endpoint colors directly, so the
 * only colors that ever appear are shades of green and shades of red.
 *
 * Two independently-tuned palettes, not simple inversions of each other -
 * see PROJECT_NOTES.md's frontend color-contrast writeup for the full WCAG
 * reasoning. Word colors render as a highlight BACKGROUND behind Hebrew
 * text, not as the text color itself, so each palette has to satisfy two
 * separate constraints: enough contrast against that theme's text color to
 * stay legible, AND enough contrast against that theme's page background to
 * read as a highlight at all (a badge whose luminance matches the page it
 * sits on is invisible even if it technically passes a text-contrast check).
 *   - Dark theme: light text (~#F2F0EB) on a near-black page (~#121212), so
 *     badges need to be darker than the text but lighter than the page -
 *     deep jewel tones. Green ~5.4:1 vs text, red ~6.0:1 vs text.
 *   - Light theme: dark text (~#1A1A1A) on a warm off-white page (~#FAF7F0),
 *     so badges need to be lighter than the text but distinct from an
 *     already-near-white page - soft highlighter pastels. Green ~6.1:1 vs
 *     text, red ~5.5:1 vs text.
 * Same hue identity in both (green = the twice-occurrence signal, red =
 * common/receding), independently tuned luminance per theme.
 */
@Component
public class ColorScaleCalculator {

    private static final int[] DARK_GREEN = {10, 122, 64};    // #0A7A40
    private static final int[] DARK_RED = {190, 40, 40};      // #BE2828
    private static final int[] LIGHT_GREEN = {90, 170, 120};  // #5AAA78
    private static final int[] LIGHT_RED = {210, 120, 110};   // #D2786E

    public record ThemedColor(String dark, String light) {}

    public ThemedColor occurrenceToColor(int occurrence, int maxOccurrence) {
        if (occurrence <= 1) {
            return new ThemedColor(null, null);
        }
        if (maxOccurrence <= 2) {
            // edge case: no meaningful range yet - everything at the green
            // endpoint exactly, not a blend toward a red that doesn't apply.
            return new ThemedColor(toHex(DARK_GREEN), toHex(LIGHT_GREEN));
        }

        double t = (double) (occurrence - 2) / (double) (maxOccurrence - 2);
        t = Math.max(0.0, Math.min(t, 1.0)); // clamp

        return new ThemedColor(
                toHex(blend(DARK_GREEN, DARK_RED, t)),
                toHex(blend(LIGHT_GREEN, LIGHT_RED, t))
        );
    }

    private int[] blend(int[] from, int[] to, double t) {
        int r = (int) Math.round(from[0] + (to[0] - from[0]) * t);
        int g = (int) Math.round(from[1] + (to[1] - from[1]) * t);
        int b = (int) Math.round(from[2] + (to[2] - from[2]) * t);
        return new int[]{r, g, b};
    }

    private String toHex(int[] rgb) {
        return String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
    }
}
