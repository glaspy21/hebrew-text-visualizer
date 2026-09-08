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
 * includeVerse=false excludes the target verse from the color computation
 * (its text still comes back, just with every word's countInRange/colorHex
 * null) - lets the frontend show an "about to be read" verse with the
 * tracker on its first word instead of its last. Returns null on a 404
 * (verse doesn't exist), matching fetchRoot's convention.
 */
export async function fetchThrough(
  book: string,
  chapter: number,
  verse: number,
  includeVerse = true,
): Promise<VerseResponse[] | null> {
  const res = await fetch(
    `${API_BASE}/verses/${book}/through?chapter=${chapter}&verse=${verse}&includeVerse=${includeVerse}`,
  );
  if (res.status === 404) return null;
  if (!res.ok) {
    throw new Error(`Request failed (${res.status}): /verses/${book}/through`);
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
