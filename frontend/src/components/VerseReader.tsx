import type { VerseResponse } from "@/lib/types";
import { VerseRow } from "./VerseRow";

/**
 * Shared reader used by both routes (static chapter page, dynamic range
 * page) - per FRONTEND_PLAN.md, the chapter route is a convenience shortcut
 * onto the same reader, not a different mode. This is Phase 1's minimal
 * rendering: a flat list of verses grouped by chapter boundary. The
 * continuous-load / three-verse-focus-window behavior is Phase 2/3.
 */
export function VerseReader({ verses, book }: { verses: VerseResponse[]; book: string }) {
  if (verses.length === 0) {
    return <p className="opacity-60">No verses found for this range.</p>;
  }

  return (
    <div>
      {verses.map((v, i) => {
        const showChapterMarker = i === 0 || v.chapter !== verses[i - 1].chapter;
        return (
          <div key={v.osisId}>
            {showChapterMarker && (
              <h2
                className="mt-6 border-b pb-1 text-sm font-semibold uppercase tracking-wide opacity-60 first:mt-0"
                style={{ borderColor: "var(--color-muted-border)" }}
              >
                {book} {v.chapter}
              </h2>
            )}
            <VerseRow verse={v} />
          </div>
        );
      })}
    </div>
  );
}
