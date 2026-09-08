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

/** Rich lexicon detail for one resolved root - fetch on demand, not per-word. */
export async function fetchRoot(strongId: string): Promise<RootResponse | null> {
  const res = await fetch(`${API_BASE}/roots/${strongId}`);
  if (res.status === 404) return null;
  if (!res.ok) {
    throw new Error(`Request failed (${res.status}): /roots/${strongId}`);
  }
  return res.json();
}
