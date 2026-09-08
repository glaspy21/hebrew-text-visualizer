package com.hebrewproject.service;

import com.hebrewproject.model.Root;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups Root rows by consonantalSkeleton to find homographs - different
 * primitive roots that happen to look identical once Masoretic vowel points
 * are stripped (e.g. שָׂחַט "squeeze" H7818 vs שָׁחַט "slaughter" H7819, both
 * שחט bare). See PROJECT_NOTES.md's "Homograph detection is separate from
 * root grouping" for the full decision writeup this implements.
 *
 * Deliberately restricted to CONFIRMED PRIMITIVE roots: a Root only
 * participates if its OWN Strong's ID has a lexicon entry whose
 * isPrimitiveRoot() is true. This is stricter than (and not the same set as)
 * "derivationUncertain == false" - a Root can resolve with
 * derivationUncertain=false (RootDerivationResolver stops because a chain is
 * empty, not because depth was exceeded) while its own entry still never
 * actually says "a primitive root" - H193 is the documented example. Filtering
 * on isPrimitiveRoot() directly, rather than trusting the derivationUncertain
 * flag, is what keeps that case out of the cluster set.
 */
@Component
public class HomographClusterer {

    /**
     * @param roots           candidate Root rows (e.g. everything currently
     *                        persisted, or a synthetic set for verification)
     * @param lexiconEntries  Strong's ID -&gt; lexicon entry, used only to
     *                        confirm each root's own primitive-root status
     * @return every cluster (consonantalSkeleton -&gt; member roots) with 2 or
     *         more confirmed-primitive members - singletons aren't homographs
     */
    public Map<String, List<Root>> findClusters(List<Root> roots, Map<String, StrongsLexiconEntry> lexiconEntries) {
        Map<String, List<Root>> bySkeleton = new HashMap<>();
        for (Root root : roots) {
            StrongsLexiconEntry entry = lexiconEntries.get(root.getStrongId());
            if (entry == null || !entry.isPrimitiveRoot()) continue;

            String skeleton = root.getConsonantalSkeleton();
            if (skeleton == null || skeleton.isEmpty()) continue;

            bySkeleton.computeIfAbsent(skeleton, k -> new ArrayList<>()).add(root);
        }

        Map<String, List<Root>> clusters = new HashMap<>();
        for (Map.Entry<String, List<Root>> entry : bySkeleton.entrySet()) {
            if (entry.getValue().size() >= 2) {
                clusters.put(entry.getKey(), entry.getValue());
            }
        }
        return clusters;
    }
}
