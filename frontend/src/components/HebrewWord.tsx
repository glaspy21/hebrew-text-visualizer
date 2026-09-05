import type { WordResponse } from "../types";

export function HebrewWord({ word }: { word: WordResponse }) {
  const title = [
    `root ${word.rootId}`,
    word.partOfSpeech,
    word.countInRange != null ? `${word.countInRange}x in range` : null,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <span className="hebrew-word" style={{ color: word.colorHex }} title={title}>
      {word.surfaceForm}
    </span>
  );
}
