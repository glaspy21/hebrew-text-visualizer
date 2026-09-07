package com.hebrewproject.service;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Walks a Strong's ID's derivation chain back toward its true root, capped at
 * depth 1: a word derived directly from a primitive root resolves cleanly,
 * but anything deeper gets flagged rather than auto-resolved, since deeper
 * chains in Strong's often reflect speculative 19th-century etymology rather
 * than settled scholarship (see the Elohim case in PROJECT_NOTES.md - BDB
 * itself calls its full chain "intricate...conclusions dubious"). Direct port
 * of root_finder_v2.py's find_root(), validated there against the five cases
 * this Java version's tests re-check (RootDerivationResolverTest).
 */
@Component
public class RootDerivationResolver {

    private static final int MAX_DEPTH = 1;

    public RootResolution resolve(String strongId, Map<String, StrongsLexiconEntry> entries) {
        String current = strongId;
        int depth = 0;
        while (true) {
            StrongsLexiconEntry entry = entries.get(current);
            if (entry == null) {
                // Unknown ID (not in the lexicon, or a non-lexical placeholder
                // like "UNKNOWN") - nothing further to walk, not itself an
                // uncertain derivation.
                return new RootResolution(current, false);
            }
            if (entry.isPrimitiveRoot() || entry.getDerivationRefs().isEmpty()) {
                return new RootResolution(current, false);
            }
            if (depth >= MAX_DEPTH) {
                return new RootResolution(current, true);
            }
            current = entry.getDerivationRefs().get(0);
            depth++;
        }
    }
}
