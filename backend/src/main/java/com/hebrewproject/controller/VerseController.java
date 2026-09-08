package com.hebrewproject.controller;

import com.hebrewproject.model.Verse;
import com.hebrewproject.model.Word;
import com.hebrewproject.repository.VerseRepository;
import com.hebrewproject.repository.WordRepository;
import com.hebrewproject.service.RangeColorCalculator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/verses")
public class VerseController {

    private final VerseRepository verseRepository;
    private final WordRepository wordRepository;
    private final RangeColorCalculator rangeColorCalculator;

    public VerseController(VerseRepository verseRepository, WordRepository wordRepository,
                            RangeColorCalculator rangeColorCalculator) {
        this.verseRepository = verseRepository;
        this.wordRepository = wordRepository;
        this.rangeColorCalculator = rangeColorCalculator;
    }

    // GET /api/verses/Gen/1/1 -> a single verse, colored using only that verse's
    // own word counts (fallback; most real usage should go through the range
    // endpoints below, since the whole point is comparing frequency ACROSS a span)
    @GetMapping("/{book}/{chapter}/{verse}")
    public ResponseEntity<VerseResponse> getVerse(
            @PathVariable String book, @PathVariable Integer chapter, @PathVariable Integer verse) {

        return verseRepository.findByBookAndChapterNumberAndVerseNumber(book, chapter, verse)
                .map(v -> {
                    List<Word> words = wordRepository.findByVerse_IdOrderByPositionInVerse(v.getId());
                    Map<Long, RangeColorCalculator.WordColorResult> colors = rangeColorCalculator.computeColors(words);
                    return ResponseEntity.ok(toResponse(v, words, colors));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/verses/Gen/1?startVerse=1&endVerse=10 -> a range within one chapter.
    // Colors are computed ONCE across every word in the whole range, so "occurs
    // twice in this range" means twice across everything returned, not twice-per-verse.
    @GetMapping("/{book}/{chapter}")
    public ResponseEntity<List<VerseResponse>> getVerseRange(
            @PathVariable String book, @PathVariable Integer chapter,
            @RequestParam(defaultValue = "1") Integer startVerse,
            @RequestParam(defaultValue = "999") Integer endVerse) {

        List<Verse> verses = verseRepository
                .findByBookAndChapterNumberAndVerseNumberBetweenOrderByVerseNumber(book, chapter, startVerse, endVerse);

        return ResponseEntity.ok(buildRangeResponse(verses));
    }

    // GET /api/verses/Gen/range?startChapter=1&endChapter=2 -> a clean multi-chapter span
    @GetMapping("/{book}/range")
    public ResponseEntity<List<VerseResponse>> getChapterRange(
            @PathVariable String book,
            @RequestParam Integer startChapter,
            @RequestParam Integer endChapter) {

        List<Verse> verses = verseRepository
                .findByBookAndChapterNumberBetweenOrderByCanonicalOrder(book, startChapter, endChapter);

        return ResponseEntity.ok(buildRangeResponse(verses));
    }

    // GET /api/verses/Gen/through?chapter=1&verse=30&includeVerse=true
    // -> the progressive reading range: every verse from the BOOK'S OWN FIRST
    // verse through the given chapter/verse (inclusive), colors computed over
    // that whole span. This is the "range always starts at Genesis 1:1"
    // default from FRONTEND_PLAN.md's navigation model - a custom range start
    // is a possible future addition, not built here.
    //
    // includeVerse=false excludes the target verse from the color computation
    // (but not from the response) - lets the frontend show an "about to be
    // read" verse with no highlights yet, tracker on its first word, while
    // includeVerse=true (default) counts it fully, tracker on its last word.
    @GetMapping("/{book}/through")
    public ResponseEntity<List<VerseResponse>> getThrough(
            @PathVariable String book,
            @RequestParam Integer chapter,
            @RequestParam Integer verse,
            @RequestParam(defaultValue = "true") Boolean includeVerse) {

        Verse target = verseRepository.findByBookAndChapterNumberAndVerseNumber(book, chapter, verse)
                .orElse(null);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }
        // canonicalOrder is canon-wide, not reset per book, so "start at 1"
        // only happens to work for Genesis because it's canonically first -
        // look up the book's own first verse instead of assuming that.
        Verse first = verseRepository.findFirstByBookOrderByCanonicalOrderAsc(book)
                .orElse(null);
        if (first == null) {
            return ResponseEntity.notFound().build();
        }

        List<Verse> verses = verseRepository.findByBookAndCanonicalOrderBetweenOrderByCanonicalOrder(
                book, first.getCanonicalOrder(), target.getCanonicalOrder());

        List<Verse> versesForColor = (includeVerse || verses.isEmpty())
                ? verses
                : verses.subList(0, verses.size() - 1);

        return ResponseEntity.ok(buildRangeResponse(verses, versesForColor));
    }

    private List<VerseResponse> buildRangeResponse(List<Verse> verses) {
        return buildRangeResponse(verses, verses);
    }

    // versesForColor lets a caller render more verses than it counts - the
    // "through" endpoint's includeVerse=false case: the target verse's text
    // is still returned, but its words are left out of the color computation
    // entirely, so nothing in it gets a highlight (not even white/no-signal -
    // it's simply not part of the analytical range yet).
    private List<VerseResponse> buildRangeResponse(List<Verse> verses, List<Verse> versesForColor) {
        // Fetch every word across every verse in the range FIRST, then compute
        // colors ONCE over the combined list - this is what makes cross-verse
        // patterns (like a word appearing once in Gen 1:5 and again in Gen 1:9)
        // visible at all. Computing colors verse-by-verse would never see that.
        List<Word> allWordsInRange = new ArrayList<>();
        for (Verse v : verses) {
            allWordsInRange.addAll(wordRepository.findByVerse_IdOrderByPositionInVerse(v.getId()));
        }

        List<Word> wordsForColor = allWordsInRange;
        if (versesForColor.size() != verses.size()) {
            Set<Long> colorScopeVerseIds = versesForColor.stream().map(Verse::getId).collect(Collectors.toSet());
            wordsForColor = allWordsInRange.stream()
                    .filter(w -> colorScopeVerseIds.contains(w.getVerse().getId()))
                    .collect(Collectors.toList());
        }
        Map<Long, RangeColorCalculator.WordColorResult> colors = rangeColorCalculator.computeColors(wordsForColor);

        List<VerseResponse> responses = new ArrayList<>();
        for (Verse v : verses) {
            List<Word> wordsInThisVerse = allWordsInRange.stream()
                    .filter(w -> w.getVerse().getId().equals(v.getId()))
                    .collect(Collectors.toList());
            responses.add(toResponse(v, wordsInThisVerse, colors));
        }
        return responses;
    }

    private VerseResponse toResponse(Verse verse, List<Word> words, Map<Long, RangeColorCalculator.WordColorResult> colors) {
        List<WordResponse> wordResponses = words.stream().map(w -> {
            RangeColorCalculator.WordColorResult c = colors.get(w.getId());
            return new WordResponse(
                    w.getId(),
                    w.getSurfaceForm(),
                    w.getRootStrongIdRaw(),
                    w.getRoot().getId(),
                    w.getRoot().getStrongId(),
                    w.getRoot().isDerivationUncertain(),
                    w.getPartOfSpeech(),
                    c != null ? c.countInRange : null,
                    // Null in both fields whenever countInRange <= 1 (or the
                    // word wasn't found in the range at all) - the frontend
                    // decides "no highlight" from countInRange, not from a
                    // sentinel color value. See PROJECT_NOTES.md's frontend
                    // color-contrast writeup for why there are two fields.
                    c != null ? c.colorHexDark : null,
                    c != null ? c.colorHexLight : null,
                    // Passive marker only - per PROJECT_NOTES.md's "Homograph
                    // detection is separate from root grouping", a flagged
                    // root is never merged with or hidden from its unrelated
                    // consonantal-skeleton twin (e.g. squeeze vs slaughter,
                    // both שחט bare) - it's still counted/colored on its own.
                    w.getRoot().isHomograph()
            );
        }).collect(Collectors.toList());
        return new VerseResponse(verse.getOsisId(), verse.getChapterNumber(), verse.getVerseNumber(), wordResponses);
    }

    // rootStrongIdRaw is the RAW per-word Strong's ID before derivation-chain
    // resolution (e.g. "4427 a" for מָלַךְ), kept for debugging/display -
    // resolvedRootId/resolvedRootStrongId are the actual grouping key
    // (Root.id / Root.strongId) frontend code should use to match occurrences
    // of the same true root. Previously these were conflated under one
    // ambiguously-named "rootId" field that actually only ever returned the
    // raw value - see PROJECT_NOTES.md.
    public record WordResponse(Long wordId, String surfaceForm, String rootStrongIdRaw,
                                Long resolvedRootId, String resolvedRootStrongId, boolean derivationUncertain,
                                String partOfSpeech, Integer countInRange,
                                String colorHexDark, String colorHexLight, boolean homograph) {}

    public record VerseResponse(String osisId, Integer chapter, Integer verse, List<WordResponse> words) {}
}
