import { notFound } from "next/navigation";
import { fetchThrough } from "@/lib/api";
import { VerseReader } from "@/components/VerseReader";

// The progressive-range reading route: always starts at the book's own
// first verse (see the /through backend endpoint) and runs through this
// chapter/verse. Always dynamic - the range grows with navigation, so it
// can never be usefully precomputed at build time like the whole-chapter
// static route can.
export const dynamic = "force-dynamic";

export default async function ThroughVersePage({
  params,
  searchParams,
}: {
  params: Promise<{ book: string; chapter: string; verse: string }>;
  searchParams: Promise<{ includeVerse?: string }>;
}) {
  const { book, chapter, verse } = await params;
  const { includeVerse } = await searchParams;

  const chapterNum = Number(chapter);
  const verseNum = Number(verse);
  if (!Number.isInteger(chapterNum) || !Number.isInteger(verseNum)) notFound();

  const included = includeVerse !== "false";
  const verses = await fetchThrough(book, chapterNum, verseNum, included);
  if (!verses || verses.length === 0) notFound();

  return (
    <VerseReader
      verses={verses}
      book={book}
      tracker={{ chapter: chapterNum, verse: verseNum, position: included ? "end" : "start" }}
    />
  );
}
