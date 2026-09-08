package com.hebrewproject.service;

import com.hebrewproject.model.Word;
import com.hebrewproject.repository.RootRepository;
import com.hebrewproject.repository.WordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack check that root derivation is actually wired into ingestion, not
 * just correct in isolation (RootDerivationResolverTest already covers the
 * pure walk against the real lexicon). Boots the real Spring context - which
 * runs the real GenesisIngestionRunner exactly as it runs in production -
 * against a disposable in-memory database, then inspects the resulting DB
 * state directly for the cases in PROJECT_NOTES.md's "Validated
 * root-derivation test cases" table.
 *
 * @Transactional here is purely a test convenience (keeps one Hibernate
 * session open per test method so Word.root's lazy proxy can actually
 * resolve) - ingestion itself already committed in its own transaction
 * during context startup, before any test method runs.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:ingestion-runner-test;DB_CLOSE_DELAY=-1"
})
@Transactional
class GenesisIngestionRunnerIntegrationTest {

    @Autowired
    private WordRepository wordRepository;
    @Autowired
    private RootRepository rootRepository;

    @Test
    void kingAndReignCollapseToTheSameRoot() {
        Word king = firstWordWithRawId("4428"); // מֶלֶךְ
        // The verb only occurs in Genesis at 36:31, tagged "4427 a" - OSHB's
        // homonym-sense suffix, stripped only for lexicon lookup (see
        // GenesisIngestionRunner.toLexiconLookupId), never from rootStrongIdRaw
        // itself - so the raw value on the actual ingested Word is still "4427 a".
        Word reign = firstWordWithRawId("4427 a"); // מָלַךְ, already primitive

        assertThat(king.getRoot().getStrongId()).isEqualTo("4427");
        assertThat(king.getRoot().getId()).isEqualTo(reign.getRoot().getId());
        assertThat(king.getRoot().isDerivationUncertain()).isFalse();
    }

    @Test
    void ribAndLimpingCollapseToTheSameRoot() {
        Word rib = firstWordWithRawId("6763");     // צֵלָע, Gen 2:22
        Word limping = firstWordWithRawId("6760"); // צֹלֵעַ, Gen 32:32

        assertThat(rib.getRoot().getStrongId()).isEqualTo("6760");
        assertThat(rib.getRoot().getId()).isEqualTo(limping.getRoot().getId());
        assertThat(rib.getRoot().isDerivationUncertain()).isFalse();
    }

    @Test
    void righteousAdjectiveCollapsesToTheVerbItsBasedOn() {
        Word righteous = firstWordWithRawId("6662"); // צַדִּיק, Gen 6:9

        assertThat(righteous.getRoot().getStrongId()).isEqualTo("6663");
    }

    @Test
    void elohimResolvesOneHopButIsFlaggedRatherThanFullyChained() {
        // H430 -> H433 -> H410 -> ... eventually a disputed 19th-century
        // etymology (H193, "twist/strength"). Depth-1 cap stops at H433 and
        // flags it instead of silently resolving the whole chain - see the
        // Elohim case study in PROJECT_NOTES.md.
        Word elohim = firstWordWithRawId("430");

        assertThat(elohim.getRoot().getStrongId()).isEqualTo("433");
        assertThat(elohim.getRoot().isDerivationUncertain()).isTrue();
    }

    @Test
    void squeezeAndSlaughterAreFlaggedHomographsButRemainSeparateRoots() {
        // שָׂחַט "squeeze" (H7818, Gen 40:11) vs שָׁחַט "slaughter" (H7819, Gen
        // 22:10, tagged "7819 a" per OSHB's homonym-sense suffix) - two
        // different primitive roots that only collide once vowel points are
        // stripped. Both must be flagged, and NEITHER merged nor hidden - see
        // PROJECT_NOTES.md's "Homograph detection is separate from root
        // grouping".
        Word squeeze = firstWordWithRawId("7818");
        Word slaughter = firstWordWithRawId("7819 a");

        assertThat(squeeze.getRoot().getStrongId()).isEqualTo("7818");
        assertThat(slaughter.getRoot().getStrongId()).isEqualTo("7819");
        assertThat(squeeze.getRoot().getId()).isNotEqualTo(slaughter.getRoot().getId());
        assertThat(squeeze.getRoot().isHomograph()).isTrue();
        assertThat(slaughter.getRoot().isHomograph()).isTrue();
    }

    @Test
    void ordinaryRootIsNotFlaggedAHomograph() {
        // מָלַךְ "reign" (H4427) has no consonantal-skeleton collision among
        // Genesis's confirmed-primitive roots - a plain, unambiguous root
        // should never get the homograph marker.
        Word reign = firstWordWithRawId("4427 a");

        assertThat(reign.getRoot().isHomograph()).isFalse();
    }

    @Test
    void elohimIsNotFlaggedAHomographDespiteBeingUncertain() {
        // H433 is a depth-1 stopping point, not a confirmed primitive root
        // (derivationUncertain=true) - it must not participate in homograph
        // clustering at all, per the "confirmed primitive roots only" decision.
        Word elohim = firstWordWithRawId("430");

        assertThat(elohim.getRoot().isDerivationUncertain()).isTrue();
        assertThat(elohim.getRoot().isHomograph()).isFalse();
    }

    @Test
    void resolvedRootsAreFewerThanRawStrongsIds() {
        // Sanity check that grouping actually changes something: strictly
        // fewer Root rows than distinct raw Strong's IDs, since e.g. king and
        // reign now collapse onto one shared Root instead of two separate
        // raw-ID group keys.
        long distinctRawIds = wordRepository.findAll().stream()
                .map(Word::getRootStrongIdRaw).distinct().count();

        assertThat(rootRepository.count()).isLessThan(distinctRawIds);
    }

    private Word firstWordWithRawId(String rawStrongId) {
        List<Word> words = wordRepository.findAllByRootStrongIdInReadingOrder(rawStrongId);
        assertThat(words).as("no ingested word found with raw Strong's ID %s", rawStrongId).isNotEmpty();
        return words.get(0);
    }
}
