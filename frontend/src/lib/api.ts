import type { RootResponse, VerseResponse } from "./types";

// Spring Boot is the ONLY API - no Next.js API routes duplicate this.
// CORS on the backend already allows localhost:3000, see WebConfig.
const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

async function getJson<T>(url: string): Promise<T> {
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`Request failed (${res.status}): ${url}`);
  }
  return res.json();
}

/** A whole chapter (startVerse/endVerse default to the full chapter server-side). */
export function fetchChapter(book: string, chapter: number): Promise<VerseResponse[]> {
  return getJson(`${API_BASE}/verses/${book}/${chapter}`);
}

/** A verse range within a single chapter. */
export function fetchVerseRangeInChapter(
  book: string,
  chapter: number,
  startVerse: number,
  endVerse: number,
): Promise<VerseResponse[]> {
  return getJson(
    `${API_BASE}/verses/${book}/${chapter}?startVerse=${startVerse}&endVerse=${endVerse}`,
  );
}

/**
 * A multi-chapter span, at chapter granularity - the backend has no endpoint
 * for an arbitrary cross-chapter verse-precise range, so start/end verse
 * offsets within the boundary chapters are not trimmed here. Fine for
 * Phase 1; revisit if sub-chapter multi-chapter ranges matter later.
 */
export function fetchChapterRange(
  book: string,
  startChapter: number,
  endChapter: number,
): Promise<VerseResponse[]> {
  return getJson(
    `${API_BASE}/verses/${book}/range?startChapter=${startChapter}&endChapter=${endChapter}`,
  );
}

/**
 * The progressive reading range: from the book's own first verse through the
 * given chapter/verse (inclusive), colors computed over that whole span -
 * this is the default range model (always starts at Genesis 1:1, grows with
 * where you navigate to), not the fixed-range endpoints above.
 *
 * wordsIncluded (optional) gives word-level precision within the target
 * verse - only its first N words count toward the range, the rest still
 * renders but uncolored. Omitted, the whole verse counts (the backend's
 * default). 0 excludes the whole verse. This is what drives both the
 * "include selected verse" toggle and arrow-key word stepping. Returns
 * null on a 404 (verse doesn't exist), matching fetchRoot's convention.
 */
export async function fetchThrough(
  book: string,
  chapter: number,
  verse: number,
  wordsIncluded?: number,
): Promise<VerseResponse[] | null> {
  const wordsParam = wordsIncluded != null ? `&wordsIncluded=${wordsIncluded}` : "";
  const res = await fetch(
    `${API_BASE}/verses/${book}/through?chapter=${chapter}&verse=${verse}${wordsParam}`,
  );
  if (res.status === 404) return null;
  if (!res.ok) {
    throw new Error(`Request failed (${res.status}): /verses/${book}/through`);
  }
  return res.json();
}

/**
 * A single verse, colored using only its own word counts (not a real range -
 * see the endpoint's own backend comment). Used here just to peek a verse's
 * identity/word count for forward tracker crossing, not for its colors.
 */
export async function fetchVerse(
  book: string,
  chapter: number,
  verse: number,
): Promise<VerseResponse | null> {
  const res = await fetch(`${API_BASE}/verses/${book}/${chapter}/${verse}`);
  if (res.status === 404) return null;
  if (!res.ok) {
    throw new Error(`Request failed (${res.status}): /verses/${book}/${chapter}/${verse}`);
  }
  return res.json();
}

/** Rich lexicon detail for one resolved root - fetch on demand, not per-word. */
export async function fetchRoot(strongId: string): Promise<RootResponse | null> {
  const res = await fetch(`${API_BASE}/roots/${strongId}`);
  if (res.status === 404) return null;
  if (!res.ok) {
    throw new Error(`Request failed (${res.status}): /roots/${strongId}`);
  }
  return res.json();
}
