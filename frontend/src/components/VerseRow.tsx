import type { VerseResponse } from "@/lib/types";
import { HebrewWord } from "./HebrewWord";

// Ported from the old Vite scaffold's VerseRow.tsx - the RTL layout carries
// over unchanged, only the word rendering underneath it changed.
export function VerseRow({ verse }: { verse: VerseResponse }) {
  return (
    <div className="flex items-baseline gap-3 py-1.5">
      <span className="w-8 shrink-0 text-right text-sm opacity-50 tabular-nums">
        {verse.verse}
      </span>
      <span dir="rtl" className="min-w-0 flex-1 font-hebrew text-2xl leading-loose">
        {verse.words.map((w, i) => (
          <span key={w.wordId ?? i}>
            <HebrewWord word={w} />{" "}
          </span>
        ))}
      </span>
    </div>
  );
}
