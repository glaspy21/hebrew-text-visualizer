// Mirrors VerseController.WordResponse / VerseResponse on the backend.
export interface WordResponse {
  surfaceForm: string;
  rootId: string;
  partOfSpeech: string | null;
  countInRange: number | null;
  colorHex: string;
}

export interface VerseResponse {
  osisId: string;
  chapter: number;
  verse: number;
  words: WordResponse[];
}
