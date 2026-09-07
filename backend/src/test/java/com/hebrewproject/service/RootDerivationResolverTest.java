package com.hebrewproject.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Re-validates the five cases from PROJECT_NOTES.md's "Validated
 * root-derivation test cases" table against the REAL vendored
 * HebrewStrong.xml (not a mock/fixture) - the Java-side regression suite
 * that table promised once this logic was ported from root_finder_v2.py.
 */
class RootDerivationResolverTest {

    private static Map<String, StrongsLexiconEntry> entries;

    private final RootDerivationResolver resolver = new RootDerivationResolver();

    @BeforeAll
    static void loadLexicon() throws Exception {
        entries = new StrongsLexiconParser().parseClasspathResource("data/HebrewStrong.xml");
    }

    @Test
    void kingResolvesToReign() {
        // מֶלֶךְ (king, H4428) -> מָלַךְ (reign, H4427)
        RootResolution result = resolver.resolve("4428", entries);
        assertThat(result.getRootStrongId()).isEqualTo("4427");
        assertThat(result.isFlagged()).isFalse();
    }

    @Test
    void ribResolvesToLimping() {
        // צֵלָע (rib, Gen 2:22, H6763) -> צָלַע (limping, Gen 32:32, H6760)
        RootResolution result = resolver.resolve("6763", entries);
        assertThat(result.getRootStrongId()).isEqualTo("6760");
        assertThat(result.isFlagged()).isFalse();
    }

    @Test
    void righteousAdjectiveResolvesToTheVerb() {
        // צַדִּיק (righteous, H6662) -> צָדַק (be righteous, H6663)
        RootResolution result = resolver.resolve("6662", entries);
        assertThat(result.getRootStrongId()).isEqualTo("6663");
        assertThat(result.isFlagged()).isFalse();
    }

    @Test
    void alreadyPrimitiveResolvesToItself() {
        // בָּרָא (create, H1254) - already "a primitive root;"
        RootResolution result = resolver.resolve("1254", entries);
        assertThat(result.getRootStrongId()).isEqualTo("1254");
        assertThat(result.isFlagged()).isFalse();
    }

    @Test
    void elohimIsFlaggedNotSilentlyChainedToADisputedEtymology() {
        // אֱלֹהִים (Elohim, H430) -> H433 -> H410 -> H352 -> ... eventually H193
        // ("twist/strength", via "ram") - BDB calls this chain "intricate...
        // conclusions dubious" and offers a competing theory. The depth-1 cap
        // stops one hop in (at H433) and flags it, rather than picking a side.
        RootResolution result = resolver.resolve("430", entries);
        assertThat(result.isFlagged()).isTrue();
    }
}
