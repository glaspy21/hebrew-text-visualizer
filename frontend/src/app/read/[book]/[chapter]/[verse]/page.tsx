import { notFound } from "next/navigation";
import { fetchThrough } from "@/lib/api";
import { VerseReader } from "@/components/VerseReader";
import { RangeNavigator } from "@/components/RangeNavigator";

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
  searchParams: Promise<{ wordsIncluded?: string }>;
}) {
  const { book, chapter, verse } = await params;
  const { wordsIncluded } = await searchParams;

  const chapterNum = Number(chapter);
  const verseNum = Number(verse);
  if (!Number.isInteger(chapterNum) || !Number.isInteger(verseNum)) notFound();

  const wordsIncludedParam = wordsIncluded != null ? Number(wordsIncluded) : undefined;
  if (wordsIncludedParam != null && !Number.isInteger(wordsIncludedParam)) notFound();

  const verses = await fetchThrough(book, chapterNum, verseNum, wordsIncludedParam);
  if (!verses || verses.length === 0) notFound();

  const targetVerse = verses.find((v) => v.chapter === chapterNum && v.verse === verseNum);
  const verseWordCount = targetVerse?.words.length ?? 0;
  // wordsIncludedParam omitted means "the whole verse" (the backend's own
  // default) - resolve that to a concrete count from what actually came
  // back, so the tracker lands on the true last word rather than guessing.
  const resolvedWordsIncluded = wordsIncludedParam ?? verseWordCount;

  return (
    <>
      <RangeNavigator
        book={book}
        chapter={chapterNum}
        verse={verseNum}
        wordsIncluded={resolvedWordsIncluded}
        verseWordCount={verseWordCount}
      />
      <VerseReader
        verses={verses}
        book={book}
        tracker={{ chapter: chapterNum, verse: verseNum, wordsIncluded: resolvedWordsIncluded }}
      />
    </>
  );
}
