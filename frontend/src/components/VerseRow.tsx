import type { VerseResponse } from "@/lib/types";
import { HebrewWord } from "./HebrewWord";

// Ported from the old Vite scaffold's VerseRow.tsx - the RTL layout carries
// over unchanged, only the word rendering underneath it changed.
//
// The word span comes BEFORE the verse number in DOM order so the number
// lands on the right edge of the row - matching how printed Hebrew Bibles
// place verse numbers at the start of the (right-to-left) verse text.
export function VerseRow({
  verse,
  trackedWordsIncluded,
}: {
  verse: VerseResponse;
  /** How many of this verse's words are in range - see RangeTracker. Undefined = not the tracked verse. */
  trackedWordsIncluded?: number;
}) {
  const trackedIndex =
    trackedWordsIncluded == null
      ? -1
      : trackedWordsIncluded > 0
        ? Math.min(trackedWordsIncluded, verse.words.length) - 1
        : 0;

  return (
    <div className="flex items-baseline gap-3 py-1.5">
      <span dir="rtl" className="min-w-0 flex-1 font-hebrew text-2xl leading-loose">
        {verse.words.map((w, i) => {
          const tracked = i === trackedIndex;
          return (
            <span key={w.wordId ?? i}>
              <HebrewWord word={w} tracked={tracked} />{" "}
            </span>
          );
        })}
      </span>
      <span className="w-8 shrink-0 text-right text-sm opacity-50 tabular-nums">
        {verse.verse}
      </span>
    </div>
  );
}
