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

    private List<VerseResponse> buildRangeResponse(List<Verse> verses) {
        // Fetch every word across every verse in the range FIRST, then compute
        // colors ONCE over the combined list - this is what makes cross-verse
        // patterns (like a word appearing once in Gen 1:5 and again in Gen 1:9)
        // visible at all. Computing colors verse-by-verse would never see that.
        List<Word> allWordsInRange = new ArrayList<>();
        for (Verse v : verses) {
            allWordsInRange.addAll(wordRepository.findByVerse_IdOrderByPositionInVerse(v.getId()));
        }
        Map<Long, RangeColorCalculator.WordColorResult> colors = rangeColorCalculator.computeColors(allWordsInRange);

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
                    w.getSurfaceForm(),
                    w.getRootStrongIdRaw(),
                    w.getPartOfSpeech(),
                    c != null ? c.countInRange : null,
                    c != null ? c.colorHex : "#FFFFFF"
            );
        }).collect(Collectors.toList());
        return new VerseResponse(verse.getOsisId(), verse.getChapterNumber(), verse.getVerseNumber(), wordResponses);
    }

    public record WordResponse(String surfaceForm, String rootId, String partOfSpeech,
                                Integer countInRange, String colorHex) {}

    public record VerseResponse(String osisId, Integer chapter, Integer verse, List<WordResponse> words) {}
}
