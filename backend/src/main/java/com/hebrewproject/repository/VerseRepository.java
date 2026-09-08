package com.hebrewproject.repository;

import com.hebrewproject.model.Verse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Extending JpaRepository<Verse, Long> gives us save(), findById(), findAll(),
 * delete(), etc. for free - no implementation needed, Spring generates it at
 * runtime via a dynamic proxy. This is the "convention over configuration"
 * magic that's worth understanding rather than just accepting: Spring looks
 * at the method NAME below and parses it into a query. "findByBookAndChapterNumber"
 * becomes "WHERE book = ?1 AND chapter_number = ?2" automatically.
 */
public interface VerseRepository extends JpaRepository<Verse, Long> {

    Optional<Verse> findByBookAndChapterNumberAndVerseNumber(String book, Integer chapterNumber, Integer verseNumber);

    List<Verse> findByBookAndChapterNumberBetweenOrderByCanonicalOrder(String book, Integer startChapter, Integer endChapter);

    // Powers the "3 to 5 verse range" search from your Phase 2 spec.
    List<Verse> findByBookAndChapterNumberAndVerseNumberBetweenOrderByVerseNumber(
            String book, Integer chapterNumber, Integer startVerse, Integer endVerse);

    // The book's own first verse - the progressive reading range always
    // starts here by default (canonicalOrder is canon-wide, not per-book, so
    // "1" isn't a safe assumption once more than one book is ingested).
    Optional<Verse> findFirstByBookOrderByCanonicalOrderAsc(String book);

    // Powers the progressive range: "from the start of the book through
    // wherever the reader currently is", crossing chapter boundaries at
    // verse-level precision (canonicalOrder is monotonic across the whole
    // book, so a single BETWEEN covers this without stitching chapters).
    List<Verse> findByBookAndCanonicalOrderBetweenOrderByCanonicalOrder(
            String book, Long startOrder, Long endOrder);
}
