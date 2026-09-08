import { notFound } from "next/navigation";
import { fetchChapterRange, fetchVerseRangeInChapter } from "@/lib/api";
import { VerseReader } from "@/components/VerseReader";
import { RangePicker } from "@/components/RangePicker";

// Always dynamic - arbitrary ranges are fetched client-... well, server-side
// per-request here, but never statically generated (see FRONTEND_PLAN.md's
// Routing section: "/read/[book]?start=&end= - always dynamic").
export const dynamic = "force-dynamic";

interface RangePoint {
  chapter: number;
  verse: number;
}

function parseRangePoint(raw: string | undefined): RangePoint | null {
  if (!raw) return null;
  const [chapterStr, verseStr] = raw.split(".");
  const chapter = Number(chapterStr);
  const verse = Number(verseStr);
  if (!Number.isInteger(chapter) || !Number.isInteger(verse)) return null;
  return { chapter, verse };
}

export default async function RangePage({
  params,
  searchParams,
}: {
  params: Promise<{ book: string }>;
  searchParams: Promise<{ start?: string; end?: string }>;
}) {
  const { book } = await params;
  const { start, end } = await searchParams;
  const startPoint = parseRangePoint(start);
  const endPoint = parseRangePoint(end);

  if (!startPoint || !endPoint) {
    return <RangePicker book={book} />;
  }

  const verses =
    startPoint.chapter === endPoint.chapter
      ? await fetchVerseRangeInChapter(book, startPoint.chapter, startPoint.verse, endPoint.verse)
      : await fetchChapterRange(book, startPoint.chapter, endPoint.chapter);

  if (verses.length === 0) notFound();

  return <VerseReader verses={verses} book={book} />;
}
