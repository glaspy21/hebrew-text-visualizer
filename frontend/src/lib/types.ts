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
 * underline on one word, per the range/tracker/navigation model. "end"
 * means the target verse is fully included (tracker on its LAST word);
 * "start" means it's excluded from coloring (tracker on its FIRST word,
 * about to be read but not counted yet) - mirrors the /through endpoint's
 * includeVerse flag.
 */
export interface RangeTracker {
  chapter: number;
  verse: number;
  position: "start" | "end";
}
