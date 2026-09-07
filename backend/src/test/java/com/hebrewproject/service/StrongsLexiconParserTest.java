package com.hebrewproject.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StrongsLexiconParserTest {

    private static Map<String, StrongsLexiconEntry> entries;

    @BeforeAll
    static void loadLexicon() throws Exception {
        entries = new StrongsLexiconParser().parseClasspathResource("data/HebrewStrong.xml");
    }

    @Test
    void parsesThousandsOfEntriesFromTheRealVendoredFile() {
        // DATA_SOURCES.md records 8,674 <entry> elements in the vendored file.
        assertThat(entries).hasSize(8674);
    }

    @Test
    void primitiveRootEntryHasNoDerivationRefs() {
        // H4427 (מָלַךְ, "to reign") - "a primitive root;"
        StrongsLexiconEntry malak = entries.get("4427");
        assertThat(malak.isPrimitiveRoot()).isTrue();
        assertThat(malak.getDerivationRefs()).isEmpty();
        assertThat(malak.getHebrewPointed()).isEqualTo("מָלַךְ");
    }

    @Test
    void derivedEntryPointsToItsSource() {
        // H4428 (מֶלֶךְ, "king") - "from <w src=\"H4427\">4427</w>;"
        StrongsLexiconEntry melek = entries.get("4428");
        assertThat(melek.isPrimitiveRoot()).isFalse();
        assertThat(melek.getDerivationRefs()).containsExactly("4427");
    }

    @Test
    void consonantalSkeletonStripsVowelPoints() {
        StrongsLexiconEntry melek = entries.get("4428"); // מֶלֶךְ
        assertThat(melek.getConsonantalSkeleton()).isEqualTo("מלך");
    }
}
