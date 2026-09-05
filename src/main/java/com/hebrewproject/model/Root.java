package com.hebrewproject.model;

import jakarta.persistence.*;

/**
 * A true Hebrew root (shoresh) - the thing multiple words of different parts
 * of speech (verb/noun/adjective) collapse into, per our derivation-chain logic.
 *
 * e.g. Strong's H4427 (מָלַךְ, "to reign") is the Root row that H4428 (מֶלֶךְ,
 * "king") and H4436 (מַלְכָּה, "queen") both point to.
 */
@Entity
@Table(name = "roots", indexes = {
        @Index(name = "idx_consonantal_skeleton", columnList = "consonantalSkeleton")
})
public class Root {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The Strong's number of the PRIMITIVE root entry itself, e.g. "4427".
    // Unique because each primitive root has exactly one canonical Strong's ID.
    @Column(nullable = false, unique = true, length = 10)
    private String strongId;

    // Pointed Hebrew spelling of the root as given in the lexicon, e.g. "מָלַךְ"
    @Column(nullable = false)
    private String hebrewPointed;

    // Bare consonants only, vowel points and sin/shin dots stripped, e.g. "מלך"
    // This is what we group on to detect homographs (see שחט case: squeeze vs slaughter).
    @Column(nullable = false, length = 10)
    private String consonantalSkeleton;

    // True if this root's derivation chain in Strong's went deeper than our
    // MAX_DEPTH cutoff (see root_finder_v2.py) - meaning the etymology is
    // uncertain/disputed and a human should review it rather than trusting
    // an auto-resolved chain. The Elohim case is the reference example.
    @Column(nullable = false)
    private boolean derivationUncertain = false;

    // English gloss for display, e.g. "to reign" - optional, nice for the UI tooltip
    @Column(length = 500)
    private String glossEnglish;

    protected Root() {
    }

    public Root(String strongId, String hebrewPointed, String consonantalSkeleton) {
        this.strongId = strongId;
        this.hebrewPointed = hebrewPointed;
        this.consonantalSkeleton = consonantalSkeleton;
    }

    public Long getId() { return id; }
    public String getStrongId() { return strongId; }
    public String getHebrewPointed() { return hebrewPointed; }
    public String getConsonantalSkeleton() { return consonantalSkeleton; }
    public boolean isDerivationUncertain() { return derivationUncertain; }
    public void setDerivationUncertain(boolean derivationUncertain) { this.derivationUncertain = derivationUncertain; }
    public String getGlossEnglish() { return glossEnglish; }
    public void setGlossEnglish(String glossEnglish) { this.glossEnglish = glossEnglish; }
}
