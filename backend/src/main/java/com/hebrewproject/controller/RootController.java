package com.hebrewproject.controller;

import com.hebrewproject.model.Root;
import com.hebrewproject.repository.RootRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rich lexicon detail for one resolved root, fetched on demand (e.g. when a
 * word is clicked) rather than embedded on every word in every verse
 * response - VerseController.WordResponse stays cheap to fetch for large
 * ranges, and this data (gloss, transliteration, etc.) is identical for
 * every occurrence of the same root anyway. See PROJECT_NOTES.md.
 */
@RestController
@RequestMapping("/api/roots")
public class RootController {

    private final RootRepository rootRepository;

    public RootController(RootRepository rootRepository) {
        this.rootRepository = rootRepository;
    }

    // GET /api/roots/4427 -> detail for the resolved root H4427 (מָלַךְ, "to reign")
    @GetMapping("/{strongId}")
    public ResponseEntity<RootDetailResponse> getRoot(@PathVariable String strongId) {
        return rootRepository.findByStrongId(strongId)
                .map(r -> ResponseEntity.ok(toResponse(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    private RootDetailResponse toResponse(Root root) {
        return new RootDetailResponse(
                root.getStrongId(),
                root.getHebrewPointed(),
                root.getConsonantalSkeleton(),
                root.getTransliteration(),
                root.getGlossEnglish(),
                root.isDerivationUncertain(),
                root.isHomograph()
        );
    }

    public record RootDetailResponse(String strongId, String hebrewPointed, String consonantalSkeleton,
                                      String transliteration, String glossEnglish,
                                      boolean derivationUncertain, boolean homograph) {}
}
