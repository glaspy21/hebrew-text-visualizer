package com.hebrewproject.service;

import java.text.Normalizer;
import java.util.List;

/**
 * One entry parsed from HebrewStrong.xml: the Strong's ID (leading "H"
 * stripped, e.g. "4427"), whether its own &lt;source&gt; line marks it a
 * primitive root, and the ordered list of other Strong's IDs (also "H"
 * stripped) it derives from via &lt;source&gt;'s nested
 * &lt;w src="H####"&gt; references - empty if none.
 */
public class StrongsLexiconEntry {

    private static final char HEB_START = 'א'; // alef
    private static final char HEB_END = 'ת';    // tav

    private final String id;
    private final boolean primitiveRoot;
    private final List<String> derivationRefs;
    private final String hebrewPointed;
    private final String consonantalSkeleton;
    private final String transliteration;
    private final String meaning;

    public StrongsLexiconEntry(String id, boolean primitiveRoot, List<String> derivationRefs, String hebrewPointed,
                                String transliteration, String meaning) {
        this.id = id;
        this.primitiveRoot = primitiveRoot;
        this.derivationRefs = derivationRefs;
        this.hebrewPointed = hebrewPointed;
        this.consonantalSkeleton = toConsonantalSkeleton(hebrewPointed);
        this.transliteration = transliteration;
        this.meaning = meaning;
    }

    public String getId() { return id; }
    public boolean isPrimitiveRoot() { return primitiveRoot; }
    public List<String> getDerivationRefs() { return derivationRefs; }
    public String getHebrewPointed() { return hebrewPointed; }
    public String getConsonantalSkeleton() { return consonantalSkeleton; }
    // Romanized reading, e.g. "mâlak" for מָלַךְ - from the headword's own
    // xlit attribute, present on all 8,674 vendored entries.
    public String getTransliteration() { return transliteration; }
    // English gloss, e.g. "to reign; inceptively, to ascend the throne..." -
    // from <meaning>, concatenating its nested <def> text nodes the same way
    // getHebrewPointed() already does for the headword. Null for the ~239
    // entries (of 8,674) with no <meaning> tag at all.
    public String getMeaning() { return meaning; }

    /**
     * Strip vowel points/cantillation marks, keeping only bare Hebrew
     * consonants - e.g. מֶלֶךְ -> מלך. Direct port of root_finder_v2.py's
     * strip_to_consonants(). Used to satisfy Root.consonantalSkeleton (not
     * homograph clustering itself, which is a separate, not-yet-built feature -
     * see PROJECT_NOTES.md's "Homograph detection is separate from root
     * grouping").
     */
    static String toConsonantalSkeleton(String hebrewPointed) {
        if (hebrewPointed == null) return null;
        String decomposed = Normalizer.normalize(hebrewPointed, Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            if (c >= HEB_START && c <= HEB_END) sb.append(c);
        }
        return sb.toString();
    }
}
