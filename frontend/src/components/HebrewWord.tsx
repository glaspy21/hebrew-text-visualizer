"use client";

import type { WordResponse } from "@/lib/types";
import { useTheme } from "./ThemeProvider";

// Ported from the old Vite scaffold's HebrewWord.tsx, updated for the new
// WordResponse shape and the background-badge color system (see
// FRONTEND_PLAN.md's Color system) - color is a highlight BEHIND the word,
// never the text color itself.
export function HebrewWord({
  word,
  tracked = false,
}: {
  word: WordResponse;
  /** Marks this word as the current end of the analytical range - see RangeTracker. */
  tracked?: boolean;
}) {
  const { theme } = useTheme();
  const colorHex = theme === "dark" ? word.colorHexDark : word.colorHexLight;

  const title = [
    `root ${word.resolvedRootStrongId}`,
    word.partOfSpeech,
    word.countInRange != null ? `${word.countInRange}x in range` : null,
    word.derivationUncertain ? "derivation uncertain" : null,
    word.homograph ? "homograph: shares consonants with an unrelated root" : null,
    tracked ? "current range position" : null,
  ]
    .filter(Boolean)
    .join(" · ");

  // surfaceForm carries OSHB's "/" morpheme-boundary marker between a
  // grammatical prefix and the word it attaches to (e.g. "בְּ/רֵאשִׁית") -
  // that's source-data punctuation, not part of the Hebrew text itself, so
  // it's stripped for display only; rootStrongIdRaw/resolvedRootId etc. are
  // untouched.
  const displayText = word.surfaceForm.replaceAll("/", "");

  return (
    <span
      className="rounded px-0.5 py-px"
      style={{
        backgroundColor: colorHex ?? undefined,
        boxShadow: tracked ? "inset 0 -3px 0 0 var(--color-tracker)" : undefined,
      }}
      title={title}
    >
      {displayText}
    </span>
  );
}
