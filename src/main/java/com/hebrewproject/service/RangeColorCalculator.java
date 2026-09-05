package com.hebrewproject.service;

import com.hebrewproject.model.Word;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes colors for a RANGE of words (e.g. Gen 1:1 through Gen 2:10), where
 * every occurrence of a given root within that range shares ONE color, based
 * on how many times that root appears total within the range:
 *
 *   occurs once in range   -> white  (not yet a pattern)
 *   occurs twice in range  -> bright green, on BOTH occurrences (the literary
 *                              signal: the author is drawing a connection
 *                              between these two passages)
 *   occurs 3+ times        -> fades from green toward dark red as it approaches
 *                              whichever root is MOST frequent in this range -
 *                              common words recede into the background so they
 *                              don't compete visually with the green signal
 *
 * This deliberately replaces an earlier position-based design (where color
 * depended on a word's own running count vs. a "max so far" as you read
 * forward). That version made a root's color drift with WHERE you were in the
 * text; this version makes color a property of the ROOT'S BEHAVIOR WITHIN THE
 * WHOLE DISPLAYED RANGE, recomputed fresh each time the range changes. Simpler,
 * and it's what the literary pattern (rare second-occurrence = intentional
 * echo) actually calls for.
 */
@Component
public class RangeColorCalculator {

    private final ColorScaleCalculator colorScaleCalculator;

    public RangeColorCalculator(ColorScaleCalculator colorScaleCalculator) {
        this.colorScaleCalculator = colorScaleCalculator;
    }

    public static class WordColorResult {
        public final int countInRange;
        public final String colorHex;
        public WordColorResult(int countInRange, String colorHex) {
            this.countInRange = countInRange;
            this.colorHex = colorHex;
        }
    }

    /**
     * @param wordsInRange every Word in the range, in reading order - the caller
     *                     decides what the range's start and end are (e.g. via
     *                     the VerseRepository range queries)
     * @return a map from word.getId() to that word's computed color info
     */
    public Map<Long, WordColorResult> computeColors(List<Word> wordsInRange) {
        // Step 1: count how many times each root appears in this range
        Map<String, Integer> countPerRoot = new HashMap<>();
        for (Word w : wordsInRange) {
            countPerRoot.merge(w.getRootStrongIdRaw(), 1, Integer::sum);
        }

        // Step 2: find the range's own max (the most frequent root WITHIN this
        // range specifically - not a global corpus constant). Per your earlier
        // call, this includes ALL words, grammatical particles included.
        int rangeMax = countPerRoot.values().stream().mapToInt(Integer::intValue).max().orElse(1);

        // Step 3: compute one color per root, using the count-of-2-is-green,
        // count-of-rangeMax-is-darkest-red scale we already validated
        Map<String, String> colorPerRoot = new HashMap<>();
        for (Map.Entry<String, Integer> entry : countPerRoot.entrySet()) {
            colorPerRoot.put(entry.getKey(), colorScaleCalculator.occurrenceToColor(entry.getValue(), rangeMax));
        }

        // Step 4: apply each root's color to EVERY word instance sharing that root
        Map<Long, WordColorResult> result = new HashMap<>();
        for (Word w : wordsInRange) {
            int count = countPerRoot.get(w.getRootStrongIdRaw());
            String color = colorPerRoot.get(w.getRootStrongIdRaw());
            result.put(w.getId(), new WordColorResult(count, color));
        }
        return result;
    }
}
