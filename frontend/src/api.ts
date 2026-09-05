import type { VerseResponse } from "./types";

const API_BASE = "http://localhost:8080/api";

export async function fetchVerseRange(
  book: string,
  chapter: number,
  startVerse: number,
  endVerse: number,
): Promise<VerseResponse[]> {
  const url = `${API_BASE}/verses/${book}/${chapter}?startVerse=${startVerse}&endVerse=${endVerse}`;
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`Request failed (${res.status}): ${url}`);
  }
  return res.json();
}
