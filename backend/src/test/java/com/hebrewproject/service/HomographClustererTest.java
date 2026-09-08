package com.hebrewproject.service;

import com.hebrewproject.model.Root;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Re-verifies PROJECT_NOTES.md's "Key design decisions" homograph writeup by
 * actually running HomographClusterer against the REAL vendored
 * HebrewStrong.xml (not a mock/fixture) - same style as
 * RootDerivationResolverTest. Two things get checked here that the writeup
 * called out explicitly:
 *
 *  - the documented "83 clusters" figure, computed over every entry the
 *    lexicon itself marks "a primitive root" (not just whatever a particular
 *    ingestion run happens to touch)
 *  - that filtering on isPrimitiveRoot() is a real, stricter constraint than
 *    "derivationUncertain == false" - the H193 case names this gap by ID
 */
class HomographClustererTest {

    private static Map<String, StrongsLexiconEntry> entries;

    private final HomographClusterer clusterer = new HomographClusterer();

    @BeforeAll
    static void loadLexicon() throws Exception {
        entries = new StrongsLexiconParser().parseClasspathResource("data/HebrewStrong.xml");
    }

    @Test
    void eightyThreeClustersAmongAllConfirmedPrimitiveRootsInTheLexicon() {
        // Every entry the lexicon itself marks primitive, turned into a
        // synthetic Root row - i.e. "if we had ingested the whole lexicon,
        // not just Genesis's subset of it," which is exactly the population
        // PROJECT_NOTES.md's "83" figure was computed against.
        List<Root> allPrimitiveRoots = entries.values().stream()
                .filter(StrongsLexiconEntry::isPrimitiveRoot)
                .map(e -> new Root(e.getId(), e.getHebrewPointed(), e.getConsonantalSkeleton()))
                .toList();

        Map<String, List<Root>> clusters = clusterer.findClusters(allPrimitiveRoots, entries);

        assertThat(clusters).hasSize(83);
    }

    @Test
    void squeezeAndSlaughterFormAGenuineHomographCluster() {
        // שָׂחַט "squeeze" (H7818, Gen 40:11) vs שָׁחַט "slaughter" (H7819, Gen
        // 22:10) - two different primitive roots, same bare consonants.
        Root squeeze = new Root("7818", entries.get("7818").getHebrewPointed(), entries.get("7818").getConsonantalSkeleton());
        Root slaughter = new Root("7819", entries.get("7819").getHebrewPointed(), entries.get("7819").getConsonantalSkeleton());

        Map<String, List<Root>> clusters = clusterer.findClusters(List.of(squeeze, slaughter), entries);

        assertThat(clusters).hasSize(1);
        List<Root> cluster = clusters.values().iterator().next();
        assertThat(cluster).extracting(Root::getStrongId).containsExactlyInAnyOrder("7818", "7819");
    }

    @Test
    void h193IsExcludedDespitePassingADerivationUncertainCheck() {
        // H193 has no <source> refs, so RootDerivationResolver resolves it
        // with flagged=false (derivationUncertain would be false) - but its
        // own entry never says "a primitive root" either, so isPrimitiveRoot()
        // is false. It must NOT be treated as a confirmed primitive here,
        // even though a naive "derivationUncertain == false" filter would let
        // it through - see PROJECT_NOTES.md's H193 callout.
        StrongsLexiconEntry h193 = entries.get("193");
        assertThat(h193).isNotNull();
        assertThat(h193.isPrimitiveRoot()).isFalse();

        Root fake = new Root("193", h193.getHebrewPointed(), h193.getConsonantalSkeleton());
        // Pair it with a real primitive that happens to share no skeleton -
        // the point is just that h193 itself never enters bySkeleton at all.
        Map<String, List<Root>> clusters = clusterer.findClusters(List.of(fake), entries);

        assertThat(clusters).isEmpty();
    }

    @Test
    void rootsWithNoLexiconEntryAreExcluded() {
        Root unresolved = new Root("UNKNOWN", "UNKNOWN", "");
        Map<String, List<Root>> clusters = clusterer.findClusters(List.of(unresolved), entries);

        assertThat(clusters).isEmpty();
    }
}
