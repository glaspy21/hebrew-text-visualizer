import type { VerseResponse } from "../types";
import { HebrewWord } from "./HebrewWord";

export function VerseRow({ verse }: { verse: VerseResponse }) {
  return (
    <div className="verse-row">
      <span className="verse-number">{verse.verse}</span>
      <span className="verse-words">
        {verse.words.map((w, i) => (
          <HebrewWord key={i} word={w} />
        ))}
      </span>
    </div>
  );
}
