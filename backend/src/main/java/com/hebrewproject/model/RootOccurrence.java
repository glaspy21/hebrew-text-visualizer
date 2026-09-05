package com.hebrewproject.model;

import jakarta.persistence.*;

/**
 * The precomputed heatmap data for exactly one word: "as of this point in the
 * canon, this word's root has appeared N times, so its color is X."
 *
 * This is a separate table from Word (rather than just columns on Word) because
 * it's DERIVED data - the output of running the ingestion algorithm - versus
 * Word's fields, which are facts read directly from the source text. Keeping
 * them separate means we can recompute/wipe just this table (e.g. if we change
 * the color algorithm) without touching the source-of-truth word data at all.
 * This is the same instinct behind not mixing raw data and materialized views
 * in a production system.
 */
@Entity
@Table(name = "root_occurrences")
public class RootOccurrence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "word_id", nullable = false, unique = true)
    private Word word;

    // How many times this word's root has appeared, counting from the start of
    // ingestion up to and including this word (kept as raw data - useful for
    // future analysis - but no longer the basis for the displayed heatmap color).
    @Column(nullable = false)
    private Integer runningCount;

    // NOTE: no longer populated at ingestion time. Colors are now computed
    // live, per-request, by RangeColorCalculator - based on how many times a
    // root appears within whatever range the user is currently viewing, not
    // a fixed value baked in once at ingestion. This column is kept (nullable)
    // in case a future feature wants a cheap precomputed default; today it's
    // unused by the main API.
    @Column(length = 7)
    private String colorHex;

    protected RootOccurrence() {
    }

    public RootOccurrence(Word word, Integer runningCount) {
        this.word = word;
        this.runningCount = runningCount;
    }

    public Long getId() { return id; }
    public Word getWord() { return word; }
    public Integer getRunningCount() { return runningCount; }
    public String getColorHex() { return colorHex; }
}
