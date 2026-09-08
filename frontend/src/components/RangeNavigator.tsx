"use client";

import { useRouter } from "next/navigation";
import { useEffect, type FormEvent } from "react";

/**
 * The navigation model: jump to a book/chapter/verse (with an "include
 * selected verse" toggle - unchecked lands the tracker on the verse's
 * first word instead of its last, per the range/tracker discussion), plus
 * arrow-key word-by-word stepping through the target verse once you're
 * there. Both drive the same wordsIncluded query param the /through
 * endpoint understands - this component never computes colors itself.
 *
 * Stepping is scoped to the current verse only for now (clamped at 0 and
 * the verse's own word count) - crossing into the next/previous verse on a
 * repeated key press isn't built yet.
 *
 * Inputs are uncontrolled (defaultValue, not value/onChange) - the form is
 * keyed on the current position, so React remounts (and re-defaults) it
 * whenever navigation happens some other way (arrow keys, browser
 * back/forward) instead of syncing local state to props via an effect.
 */
export function RangeNavigator({
  book,
  chapter,
  verse,
  wordsIncluded,
  verseWordCount,
}: {
  book: string;
  chapter: number;
  verse: number;
  wordsIncluded: number;
  verseWordCount: number;
}) {
  const router = useRouter();

  function goTo(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const data = new FormData(e.currentTarget);
    const c = Number(data.get("chapter"));
    const v = Number(data.get("verse"));
    if (!Number.isInteger(c) || !Number.isInteger(v)) return;
    const query = data.get("includeVerse") === "on" ? "" : "?wordsIncluded=0";
    router.push(`/read/${book}/${c}/${v}${query}`);
  }

  useEffect(() => {
    function step(delta: number) {
      const next = Math.max(0, Math.min(wordsIncluded + delta, verseWordCount));
      if (next !== wordsIncluded) {
        router.replace(`/read/${book}/${chapter}/${verse}?wordsIncluded=${next}`);
      }
    }

    function onKeyDown(e: KeyboardEvent) {
      if (e.repeat) return; // ignore key-repeat floods - one step per press
      if (e.target instanceof HTMLInputElement) return; // don't steal typing

      // Confirmed direction: for RTL text (Hebrew), Left advances and Right
      // retreats, matching the visual reading direction rather than the
      // LTR-assuming default. This will need to flip once LTR content (the
      // English translation phase) can drive this same tracker. Down/Up are
      // direction-neutral aliases for advance/retreat either way.
      if (e.key === "ArrowLeft" || e.key === "ArrowDown") {
        e.preventDefault();
        step(1);
      } else if (e.key === "ArrowRight" || e.key === "ArrowUp") {
        e.preventDefault();
        step(-1);
      }
    }

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [book, chapter, verse, wordsIncluded, verseWordCount, router]);

  return (
    <form
      key={`${chapter}-${verse}-${wordsIncluded}`}
      onSubmit={goTo}
      className="mb-6 flex flex-wrap items-end gap-3 text-sm"
    >
      <label className="flex flex-col">
        Chapter
        <input
          name="chapter"
          defaultValue={chapter}
          className="w-16 rounded border px-2 py-1"
          style={{ borderColor: "var(--color-muted-border)" }}
        />
      </label>
      <label className="flex flex-col">
        Verse
        <input
          name="verse"
          defaultValue={verse}
          className="w-16 rounded border px-2 py-1"
          style={{ borderColor: "var(--color-muted-border)" }}
        />
      </label>
      <label className="flex items-center gap-2 pb-1.5">
        <input name="includeVerse" type="checkbox" defaultChecked={wordsIncluded > 0} />
        Include selected verse
      </label>
      <button
        type="submit"
        className="rounded border px-3 py-1"
        style={{ borderColor: "var(--color-muted-border)" }}
      >
        Go
      </button>
      <span className="pb-1.5 opacity-50">
        ← / → steps word by word ({wordsIncluded}/{verseWordCount})
      </span>
    </form>
  );
}
