package com.hebrewproject.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ColorScaleCalculatorTest {

    private final ColorScaleCalculator calculator = new ColorScaleCalculator();

    @Test
    void firstOccurrenceIsWhite() {
        assertThat(calculator.occurrenceToColor(1, 5)).isEqualTo("#FFFFFF");
    }

    @Test
    void zeroOrNegativeOccurrenceIsAlsoTreatedAsWhite() {
        // Defensive: the real pipeline never produces occurrence <= 0, but the
        // scale should still degrade sensibly rather than throwing/blending oddly.
        assertThat(calculator.occurrenceToColor(0, 5)).isEqualTo("#FFFFFF");
    }

    @Test
    void secondOccurrenceIsBrightGreen_whenMaxIsExactlyTwo() {
        assertThat(calculator.occurrenceToColor(2, 2)).isEqualTo("#00C800");
    }

    @Test
    void secondOccurrenceIsBrightGreen_whenMaxIsLarger() {
        // t = (2-2)/(max-2) = 0, so this must land exactly on the green endpoint,
        // not just "close to it" - the second occurrence IS the literary signal.
        assertThat(calculator.occurrenceToColor(2, 10)).isEqualTo("#00C800");
    }

    @Test
    void maxOccurrenceIsExactlyDarkRed() {
        assertThat(calculator.occurrenceToColor(7, 7)).isEqualTo("#280000");
    }

    @Test
    void midpointBlendsLinearlyBetweenGreenAndRed() {
        // occurrence=3, max=4 -> t=0.5 -> halfway between (0,200,0) and (40,0,0)
        assertThat(calculator.occurrenceToColor(3, 4)).isEqualTo("#146400");
    }

    @Test
    void occurrenceBeyondMaxIsClampedToDarkRed() {
        // Shouldn't happen from RangeColorCalculator's own bookkeeping, but the
        // scale itself must not produce out-of-palette colors if it ever does.
        assertThat(calculator.occurrenceToColor(99, 7)).isEqualTo("#280000");
    }

    @Test
    void onlyGreenAndRedShadesEverAppear_neverYellowOrOrange() {
        // Regression test for the bug this class's Javadoc calls out: an earlier
        // HSL hue-rotation implementation swept through yellow/orange on the way
        // from green to red. A direct RGB blend between fixed endpoints never
        // should, since blue channel stays 0 and red only rises as green falls.
        for (int occurrence = 2; occurrence <= 20; occurrence++) {
            String hex = calculator.occurrenceToColor(occurrence, 20);
            int r = Integer.parseInt(hex.substring(1, 3), 16);
            int g = Integer.parseInt(hex.substring(3, 5), 16);
            int b = Integer.parseInt(hex.substring(5, 7), 16);
            assertThat(b).isZero();
            assertThat(r).isBetween(0, 40);
            assertThat(g).isBetween(0, 200);
        }
    }
}
