package com.hebrewproject.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Records that a specific source file, at a specific content checksum, was
 * FULLY ingested. This row is only ever written after ingestion completes
 * successfully - never before - so its mere presence (for a matching checksum)
 * is proof the data is complete, not just non-empty.
 *
 * This replaces the earlier "verseRepository.count() > 0" skip check, which
 * couldn't tell a complete ingestion apart from a partial one left behind by
 * a crash mid-run (see PROJECT_NOTES.md - this exact failure mode happened
 * once already with a schema-width bug). Keying on checksum also means
 * swapping in a newer OSHB release triggers a clean re-ingest automatically,
 * instead of silently keeping stale data forever.
 */
@Entity
@Table(name = "ingestion_metadata")
public class IngestionMetadata {

    @Id
    @Column(length = 100)
    private String sourceFile; // e.g. "data/Gen.xml"

    @Column(nullable = false, length = 64)
    private String sha256Checksum;

    @Column(nullable = false)
    private int verseCount;

    @Column(nullable = false)
    private int wordCount;

    @Column(nullable = false)
    private Instant completedAt;

    protected IngestionMetadata() {
    }

    public IngestionMetadata(String sourceFile, String sha256Checksum, int verseCount, int wordCount, Instant completedAt) {
        this.sourceFile = sourceFile;
        this.sha256Checksum = sha256Checksum;
        this.verseCount = verseCount;
        this.wordCount = wordCount;
        this.completedAt = completedAt;
    }

    public String getSourceFile() { return sourceFile; }
    public String getSha256Checksum() { return sha256Checksum; }
    public int getVerseCount() { return verseCount; }
    public int getWordCount() { return wordCount; }
    public Instant getCompletedAt() { return completedAt; }
}
