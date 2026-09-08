package com.hebrewproject.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ColorScaleCalculatorTest {

    private final ColorScaleCalculator calculator = new ColorScaleCalculator();

    @Test
    void firstOccurrenceHasNoHighlightInEitherTheme() {
        ColorScaleCalculator.ThemedColor color = calculator.occurrenceToColor(1, 5);
        assertThat(color.dark()).isNull();
        assertThat(color.light()).isNull();
    }

    @Test
    void zeroOrNegativeOccurrenceIsAlsoTreatedAsNoHighlight() {
        // Defensive: the real pipeline never produces occurrence <= 0, but the
        // scale should still degrade sensibly rather than throwing/blending oddly.
        ColorScaleCalculator.ThemedColor color = calculator.occurrenceToColor(0, 5);
        assertThat(color.dark()).isNull();
        assertThat(color.light()).isNull();
    }

    @Test
    void secondOccurrenceIsTheGreenEndpoint_whenMaxIsExactlyTwo() {
        ColorScaleCalculator.ThemedColor color = calculator.occurrenceToColor(2, 2);
        assertThat(color.dark()).isEqualTo("#0A7A40");
        assertThat(color.light()).isEqualTo("#5AAA78");
    }

    @Test
    void secondOccurrenceIsTheGreenEndpoint_whenMaxIsLarger() {
        // t = (2-2)/(max-2) = 0, so this must land exactly on the green endpoint,
        // not just "close to it" - the second occurrence IS the literary signal.
        ColorScaleCalculator.ThemedColor color = calculator.occurrenceToColor(2, 10);
        assertThat(color.dark()).isEqualTo("#0A7A40");
        assertThat(color.light()).isEqualTo("#5AAA78");
    }

    @Test
    void maxOccurrenceIsExactlyTheRedEndpoint() {
        ColorScaleCalculator.ThemedColor color = calculator.occurrenceToColor(7, 7);
        assertThat(color.dark()).isEqualTo("#BE2828");
        assertThat(color.light()).isEqualTo("#D2786E");
    }

    @Test
    void midpointBlendsLinearlyBetweenGreenAndRed_inBothThemes() {
        // occurrence=3, max=4 -> t=0.5 -> halfway between each theme's own
        // green and red endpoints.
        ColorScaleCalculator.ThemedColor color = calculator.occurrenceToColor(3, 4);
        assertThat(color.dark()).isEqualTo("#645134");
        assertThat(color.light()).isEqualTo("#969173");
    }

    @Test
    void occurrenceBeyondMaxIsClampedToTheRedEndpoint() {
        // Shouldn't happen from RangeColorCalculator's own bookkeeping, but the
        // scale itself must not produce out-of-palette colors if it ever does.
        ColorScaleCalculator.ThemedColor color = calculator.occurrenceToColor(99, 7);
        assertThat(color.dark()).isEqualTo("#BE2828");
        assertThat(color.light()).isEqualTo("#D2786E");
    }

    @Test
    void darkThemeNeverDriftsTowardYellowOrOrange() {
        assertNoOrangeDrift(10, 122, 64, 190, 40, 40, false);
    }

    @Test
    void lightThemeNeverDriftsTowardYellowOrOrange() {
        assertNoOrangeDrift(90, 170, 120, 210, 120, 110, true);
    }

    /**
     * Regression check for the bug this class's Javadoc calls out: an earlier
     * HSL hue-rotation implementation swept through yellow/orange on the way
     * from green to red. A direct RGB blend between two fixed endpoints can't,
     * as long as the red channel rises monotonically while the green channel
     * falls monotonically across the whole blend - the two channels never both
     * sit at a simultaneously-high "yellow" combination. Verified here by
     * checking that monotonicity directly, rather than re-deriving hue.
     */
    private void assertNoOrangeDrift(int gR, int gG, int gB, int rR, int rG, int rB, boolean light) {
        int previousR = Integer.MIN_VALUE;
        int previousG = Integer.MAX_VALUE;
        for (int occurrence = 2; occurrence <= 20; occurrence++) {
            ColorScaleCalculator.ThemedColor color = calculator.occurrenceToColor(occurrence, 20);
            String hex = light ? color.light() : color.dark();
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);

            assertThat(r).isBetween(Math.min(gR, rR), Math.max(gR, rR));
            assertThat(g).isBetween(Math.min(gG, rG), Math.max(gG, rG));
            assertThat(b).isBetween(Math.min(gB, rB), Math.max(gB, rB));
            assertThat(r).isGreaterThanOrEqualTo(previousR);
            assertThat(g).isLessThanOrEqualTo(previousG);
            previousR = r;
            previousG = g;
        }
    }
}
