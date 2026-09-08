import { notFound } from "next/navigation";
import { fetchChapter } from "@/lib/api";
import { VerseReader } from "@/components/VerseReader";

// Only Genesis is ingested today (see README.md) - statically generate its
// 50 chapters as shareable entry points. dynamicParams stays at its default
// (true), so other books/chapters are still rendered on demand rather than
// 404ing outright; they just won't have a real verse to show yet.
const GENESIS_CHAPTER_COUNT = 50;

export function generateStaticParams() {
  return Array.from({ length: GENESIS_CHAPTER_COUNT }, (_, i) => ({
    book: "Gen",
    chapter: String(i + 1),
  }));
}

export default async function ChapterPage({
  params,
}: {
  params: Promise<{ book: string; chapter: string }>;
}) {
  const { book, chapter } = await params;
  const chapterNum = Number(chapter);
  if (!Number.isInteger(chapterNum) || chapterNum < 1) notFound();

  const verses = await fetchChapter(book, chapterNum);
  if (verses.length === 0) notFound();

  return <VerseReader verses={verses} book={book} />;
}
