// Mirrors VerseController.WordResponse / VerseResponse and RootController's
// response on the backend - see FRONTEND_PLAN.md's "Backend API contract".

export interface WordResponse {
  wordId: number;
  surfaceForm: string;
  rootStrongIdRaw: string;
  resolvedRootId: number;
  resolvedRootStrongId: string;
  derivationUncertain: boolean;
  partOfSpeech: string | null;
  countInRange: number | null;
  // Null whenever countInRange <= 1 - render NO highlight, not white.
  colorHexDark: string | null;
  colorHexLight: string | null;
  homograph: boolean;
}

export interface VerseResponse {
  osisId: string;
  chapter: number;
  verse: number;
  words: WordResponse[];
}

export interface RootResponse {
  strongId: string;
  hebrewPointed: string;
  consonantalSkeleton: string;
  transliteration: string;
  // Null for ~239 of 8,674 lexicon entries with no <meaning> tag.
  glossEnglish: string | null;
  derivationUncertain: boolean;
  homograph: boolean;
}

export type Theme = "dark" | "light";

/**
 * Marks the current end of the progressive analytical range - a visual
 * underline on one word, per the range/tracker/navigation model.
 * wordsIncluded is how many of the target verse's words are counted (mirrors
 * the /through endpoint's wordsIncluded param): the tracker sits on word
 * (wordsIncluded - 1) if wordsIncluded > 0 (the last COUNTED word), or word 0
 * if wordsIncluded is 0 (the verse's first word, not yet counted - about to
 * be read). This subsumes the coarser "include the whole verse or not" case:
 * wordsIncluded === the verse's own word count means fully included.
 */
export interface RangeTracker {
  chapter: number;
  verse: number;
  wordsIncluded: number;
}

/** Just enough to let the tracker cross a verse boundary without re-fetching colors for it. */
export interface VerseSummary {
  chapter: number;
  verse: number;
  wordCount: number;
}
