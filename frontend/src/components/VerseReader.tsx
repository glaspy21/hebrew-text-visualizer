import type { RangeTracker, VerseResponse } from "@/lib/types";
import { VerseRow } from "./VerseRow";

/**
 * Shared reader used by all routes - per FRONTEND_PLAN.md, the static
 * chapter route is a convenience shortcut onto the same reader, not a
 * different mode. `tracker` (optional - only the progressive-range route
 * sets it) marks the current end of the analytical range with an underline;
 * the three-verse-focus-window subduing is still Phase 3, not built here.
 */
export function VerseReader({
  verses,
  book,
  tracker,
}: {
  verses: VerseResponse[];
  book: string;
  tracker?: RangeTracker;
}) {
  if (verses.length === 0) {
    return <p className="opacity-60">No verses found for this range.</p>;
  }

  return (
    <div>
      {verses.map((v, i) => {
        const showChapterMarker = i === 0 || v.chapter !== verses[i - 1].chapter;
        const isTrackedVerse =
          tracker != null && v.chapter === tracker.chapter && v.verse === tracker.verse;
        return (
          <div key={v.osisId} data-chapter={v.chapter} data-verse={v.verse}>
            {showChapterMarker && (
              <h2
                className="mt-6 border-b pb-1 text-sm font-semibold uppercase tracking-wide opacity-60 first:mt-0"
                style={{ borderColor: "var(--color-muted-border)" }}
              >
                {book} {v.chapter}
              </h2>
            )}
            <VerseRow
              verse={v}
              trackedWordsIncluded={isTrackedVerse ? tracker.wordsIncluded : undefined}
            />
          </div>
        );
      })}
    </div>
  );
}
