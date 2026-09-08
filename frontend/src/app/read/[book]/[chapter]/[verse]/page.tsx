import { notFound } from "next/navigation";
import { fetchThrough, fetchVerse } from "@/lib/api";
import { VerseReader } from "@/components/VerseReader";
import { RangeNavigator } from "@/components/RangeNavigator";
import { FocusWindow } from "@/components/FocusWindow";

// The progressive-range reading route: always starts at the book's own
// first verse (see the /through backend endpoint) and runs through this
// chapter/verse. Always dynamic - the range grows with navigation, so it
// can never be usefully precomputed at build time like the whole-chapter
// static route can.
export const dynamic = "force-dynamic";

/** Peeks the immediate next verse (same chapter first, then chapter+1 verse 1) for forward tracker crossing. */
async function peekNextVerse(book: string, chapter: number, verse: number) {
  const sameChapterNext = await fetchVerse(book, chapter, verse + 1);
  if (sameChapterNext) {
    return { chapter: sameChapterNext.chapter, verse: sameChapterNext.verse, wordCount: sameChapterNext.words.length };
  }
  const nextChapterFirst = await fetchVerse(book, chapter + 1, 1);
  if (nextChapterFirst) {
    return { chapter: nextChapterFirst.chapter, verse: nextChapterFirst.verse, wordCount: nextChapterFirst.words.length };
  }
  return null;
}

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

  const [verses, nextVerse] = await Promise.all([
    fetchThrough(book, chapterNum, verseNum, wordsIncludedParam),
    peekNextVerse(book, chapterNum, verseNum),
  ]);
  if (!verses || verses.length === 0) notFound();

  const targetVerse = verses.find((v) => v.chapter === chapterNum && v.verse === verseNum);
  const verseWordCount = targetVerse?.words.length ?? 0;
  // wordsIncludedParam omitted means "the whole verse" (the backend's own
  // default) - resolve that to a concrete count from what actually came
  // back, so the tracker lands on the true last word rather than guessing.
  const resolvedWordsIncluded = wordsIncludedParam ?? verseWordCount;

  // The entire progressive range is already in memory (it always spans the
  // book's start), so backward tracker crossing needs no extra fetch - just
  // a compact projection of what we already have.
  const versesSoFar = verses.map((v) => ({
    chapter: v.chapter,
    verse: v.verse,
    wordCount: v.words.length,
  }));

  return (
    <div className="flex min-h-[75vh] flex-col">
      <RangeNavigator
        book={book}
        chapter={chapterNum}
        verse={verseNum}
        wordsIncluded={resolvedWordsIncluded}
        verseWordCount={verseWordCount}
        versesSoFar={versesSoFar}
        nextVerse={nextVerse}
      />
      <div className="flex flex-1 items-center">
        <FocusWindow>
          <VerseReader
            verses={verses}
            book={book}
            tracker={{ chapter: chapterNum, verse: verseNum, wordsIncluded: resolvedWordsIncluded }}
          />
        </FocusWindow>
      </div>
    </div>
  );
}
