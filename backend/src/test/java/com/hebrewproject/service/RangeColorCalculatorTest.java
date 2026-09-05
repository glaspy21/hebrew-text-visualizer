package com.hebrewproject.service;

import com.hebrewproject.model.Word;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RangeColorCalculatorTest {

    // Real ColorScaleCalculator, not a mock: this suite is exercising the
    // combination ("range behavior") end to end, not RangeColorCalculator's
    // bookkeeping in isolation.
    private final RangeColorCalculator calculator = new RangeColorCalculator(new ColorScaleCalculator());

    private Word wordWithRoot(long id, String rootId) {
        Word word = mock(Word.class);
        when(word.getId()).thenReturn(id);
        when(word.getRootStrongIdRaw()).thenReturn(rootId);
        return word;
    }

    @Test
    void rootAppearingOnceInRangeIsWhite() {
        Word onlyWord = wordWithRoot(1L, "7225");

        Map<Long, RangeColorCalculator.WordColorResult> colors =
                calculator.computeColors(List.of(onlyWord));

        assertThat(colors.get(1L).countInRange).isEqualTo(1);
        assertThat(colors.get(1L).colorHex).isEqualTo("#FFFFFF");
    }

    @Test
    void rootAppearingTwiceInRangeIsGreenOnBothOccurrences() {
        // The literary signal this whole project is built around: a root seen
        // exactly twice across a range renders identically on both instances,
        // regardless of how far apart they are in the range.
        Word first = wordWithRoot(1L, "430");
        Word unrelated = wordWithRoot(2L, "8064");
        Word second = wordWithRoot(3L, "430");

        Map<Long, RangeColorCalculator.WordColorResult> colors =
                calculator.computeColors(List.of(first, unrelated, second));

        assertThat(colors.get(1L).countInRange).isEqualTo(2);
        assertThat(colors.get(3L).countInRange).isEqualTo(2);
        assertThat(colors.get(1L).colorHex).isEqualTo("#00C800");
        assertThat(colors.get(1L).colorHex).isEqualTo(colors.get(3L).colorHex);
    }

    @Test
    void mostFrequentRootInRangeIsDarkRed_relativeToThatRangeOnly() {
        // rootC is the range's own max (3x) - the scale's red endpoint is
        // defined relative to THIS range, not any fixed global count.
        Word a = wordWithRoot(1L, "A"); // occurs once
        Word b1 = wordWithRoot(2L, "B"); // occurs twice
        Word b2 = wordWithRoot(3L, "B");
        Word c1 = wordWithRoot(4L, "C"); // occurs three times - range max
        Word c2 = wordWithRoot(5L, "C");
        Word c3 = wordWithRoot(6L, "C");

        Map<Long, RangeColorCalculator.WordColorResult> colors =
                calculator.computeColors(List.of(a, b1, b2, c1, c2, c3));

        assertThat(colors.get(1L).colorHex).isEqualTo("#FFFFFF");
        assertThat(colors.get(2L).colorHex).isEqualTo("#00C800");
        assertThat(colors.get(3L).colorHex).isEqualTo("#00C800");
        assertThat(colors.get(4L).colorHex).isEqualTo("#280000");
        assertThat(colors.get(5L).colorHex).isEqualTo("#280000");
        assertThat(colors.get(6L).colorHex).isEqualTo("#280000");
    }

    @Test
    void sameRootCanDifferAcrossTwoRangeCalls_becauseRarityIsRelativeToTheRange() {
        // Same two words, once queried alone (range max = 2, so green) and once
        // queried alongside a root that's more frequent in that wider range
        // (pushing the pair's own frequency-of-2 further from the new max) -
        // colors.get on this range come out the same shape either way since the
        // pair is still a "seen twice" case, but this asserts the actual
        // mechanism: countInRange and color are recomputed from THIS call's
        // list only, never cached from a previous range.
        Word x1 = wordWithRoot(1L, "X");
        Word x2 = wordWithRoot(2L, "X");

        Map<Long, RangeColorCalculator.WordColorResult> narrowRange =
                calculator.computeColors(List.of(x1, x2));
        assertThat(narrowRange.get(1L).colorHex).isEqualTo("#00C800");

        Word y1 = wordWithRoot(3L, "Y");
        Word y2 = wordWithRoot(4L, "Y");
        Word y3 = wordWithRoot(5L, "Y");
        Map<Long, RangeColorCalculator.WordColorResult> widerRange =
                calculator.computeColors(List.of(x1, x2, y1, y2, y3));

        assertThat(widerRange.get(1L).countInRange).isEqualTo(2);
        assertThat(widerRange.get(1L).colorHex).isEqualTo("#00C800");
        assertThat(widerRange.get(3L).colorHex).isEqualTo("#280000"); // now the range's own max
    }
}
