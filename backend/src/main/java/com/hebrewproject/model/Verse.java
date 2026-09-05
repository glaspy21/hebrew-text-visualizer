package com.hebrewproject.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * One verse of scripture, e.g. Genesis 1:1.
 *
 * We split book/chapter/verseNumber into separate columns (rather than storing
 * "Gen.1.1" as one string) specifically so range queries like "chapters 1-3"
 * or "verses 5 to 8" can use plain numeric comparisons (chapter BETWEEN 1 AND 3)
 * instead of parsing strings. This is what makes the future multi-verse-range
 * search feature cheap instead of painful.
 */
@Entity
@Table(name = "verses", indexes = {
        // Composite index: most queries will filter by book+chapter+verseNumber together,
        // so a compound index here is far more useful than three separate single-column ones.
        @Index(name = "idx_book_chapter_verse", columnList = "book, chapterNumber, verseNumber")
})
public class Verse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String book; // e.g. "Gen" - matches OSHB's book abbreviation

    @Column(nullable = false)
    private Integer chapterNumber;

    @Column(nullable = false)
    private Integer verseNumber;

    // The full running order of this verse across the whole canon.
    // e.g. Gen 1:1 = 1, Gen 1:2 = 2 ... this is what lets us do "reading order"
    // sweeps across book boundaries later, without recalculating position every time.
    @Column(nullable = false)
    private Long canonicalOrder;

    // One verse has many words. mappedBy = "verse" means the Word entity owns
    // the foreign key (word.verse_id) - Verse doesn't store anything extra for this,
    // it's just a convenient Java-side view of the relationship.
    @OneToMany(mappedBy = "verse", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Word> words = new ArrayList<>();

    protected Verse() {
        // JPA requires a no-arg constructor (it builds objects via reflection,
        // then sets fields - it doesn't call your custom constructors).
    }

    public Verse(String book, Integer chapterNumber, Integer verseNumber, Long canonicalOrder) {
        this.book = book;
        this.chapterNumber = chapterNumber;
        this.verseNumber = verseNumber;
        this.canonicalOrder = canonicalOrder;
    }

    public Long getId() { return id; }
    public String getBook() { return book; }
    public Integer getChapterNumber() { return chapterNumber; }
    public Integer getVerseNumber() { return verseNumber; }
    public Long getCanonicalOrder() { return canonicalOrder; }
    public List<Word> getWords() { return words; }

    public String getOsisId() {
        return book + "." + chapterNumber + "." + verseNumber;
    }
}
