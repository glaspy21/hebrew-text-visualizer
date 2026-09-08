"use client";

import { useRouter } from "next/navigation";
import { useEffect, type FormEvent } from "react";
import type { VerseSummary } from "@/lib/types";

const HOLD_INITIAL_DELAY_MS = 350; // time before the first auto-repeat
const HOLD_MIN_INTERVAL_MS = 40; // fastest the ramp accelerates to
const HOLD_ACCELERATION = 0.85; // interval shrinks by this factor each tick

/**
 * The navigation model: jump to a book/chapter/verse (with an "include
 * selected verse" toggle - unchecked lands the tracker on the verse's
 * first word instead of its last, per the range/tracker discussion), plus
 * arrow-key word-by-word stepping through the target verse once you're
 * there. Both drive the same wordsIncluded query param the /through
 * endpoint understands - this component never computes colors itself.
 *
 * Holding a key auto-repeats with its own accelerating ramp (not the
 * browser's native, non-accelerating key repeat, which is ignored via
 * e.repeat) - starts at HOLD_INITIAL_DELAY_MS, speeds up toward
 * HOLD_MIN_INTERVAL_MS the longer it's held.
 *
 * Stepping crosses verse boundaries: retreating past a verse's first word
 * (wordsIncluded 0) lands on the PREVIOUS verse's last word (using data
 * already in versesSoFar - the whole progressive range is already fetched,
 * so backward crossing has no depth limit). Advancing past a verse's last
 * word lands on the NEXT verse's first word (wordsIncluded 0) using the
 * single-verse lookahead in nextVerse - only one boundary of forward
 * headroom is fetched, so a very long forward hold clamps at the end of
 * that next verse rather than fetching further ahead.
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
  versesSoFar,
  nextVerse,
}: {
  book: string;
  chapter: number;
  verse: number;
  wordsIncluded: number;
  verseWordCount: number;
  versesSoFar: VerseSummary[];
  nextVerse: VerseSummary | null;
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

  // Deliberately excludes `wordsIncluded` (and the other position-derived
  // props) from the dependency array: each step's navigation eventually
  // updates those props, and reacting to that would tear down and rebuild
  // this effect mid-hold, resetting the acceleration ramp on every single
  // step. `pos` below is the local, ahead-of-the-server position for the
  // life of one hold gesture; it's seeded fresh whenever a REAL navigation
  // (a different chapter/verse) actually happens.
  useEffect(() => {
    let pos = { chapter, verse, wordsIncluded };
    let heldDirection: 1 | -1 | null = null;
    let timer: ReturnType<typeof setTimeout> | null = null;
    let interval = HOLD_INITIAL_DELAY_MS;

    function wordCountFor(c: number, v: number): number {
      if (c === chapter && v === verse) return verseWordCount;
      if (nextVerse && c === nextVerse.chapter && v === nextVerse.verse) return nextVerse.wordCount;
      return versesSoFar.find((x) => x.chapter === c && x.verse === v)?.wordCount ?? 0;
    }

    function fireStep(direction: 1 | -1) {
      if (direction === 1) {
        if (pos.wordsIncluded < wordCountFor(pos.chapter, pos.verse)) {
          pos = { ...pos, wordsIncluded: pos.wordsIncluded + 1 };
        } else if (nextVerse && pos.chapter === chapter && pos.verse === verse) {
          pos = { chapter: nextVerse.chapter, verse: nextVerse.verse, wordsIncluded: 0 };
        } else {
          return; // no more forward data - clamp
        }
      } else {
        if (pos.wordsIncluded > 0) {
          pos = { ...pos, wordsIncluded: pos.wordsIncluded - 1 };
        } else {
          const idx = versesSoFar.findIndex((x) => x.chapter === pos.chapter && x.verse === pos.verse);
          if (idx > 0) {
            const prev = versesSoFar[idx - 1];
            pos = { chapter: prev.chapter, verse: prev.verse, wordsIncluded: prev.wordCount };
          } else {
            return; // at the book's own first verse - clamp
          }
        }
      }
      router.replace(`/read/${book}/${pos.chapter}/${pos.verse}?wordsIncluded=${pos.wordsIncluded}`);
    }

    function tick() {
      if (heldDirection == null) return;
      fireStep(heldDirection);
      interval = Math.max(HOLD_MIN_INTERVAL_MS, interval * HOLD_ACCELERATION);
      timer = setTimeout(tick, interval);
    }

    function startHold(direction: 1 | -1) {
      if (heldDirection === direction) return;
      stopHold();
      heldDirection = direction;
      interval = HOLD_INITIAL_DELAY_MS;
      fireStep(direction);
      timer = setTimeout(tick, interval);
    }

    function stopHold() {
      heldDirection = null;
      if (timer != null) clearTimeout(timer);
      timer = null;
    }

    // Confirmed direction: for RTL text (Hebrew), Left advances and Right
    // retreats, matching the visual reading direction rather than the
    // LTR-assuming default. This will need to flip once LTR content (the
    // English translation phase) can drive this same tracker. Down/Up are
    // direction-neutral aliases for advance/retreat either way.
    function isAdvanceKey(key: string) {
      return key === "ArrowLeft" || key === "ArrowDown";
    }
    function isRetreatKey(key: string) {
      return key === "ArrowRight" || key === "ArrowUp";
    }

    function onKeyDown(e: KeyboardEvent) {
      if (e.target instanceof HTMLInputElement) return; // don't steal typing
      if (!isAdvanceKey(e.key) && !isRetreatKey(e.key)) return;
      e.preventDefault();
      if (e.repeat) return; // our own ramp drives repeats, not the browser's
      startHold(isAdvanceKey(e.key) ? 1 : -1);
    }

    function onKeyUp(e: KeyboardEvent) {
      if (
        (isAdvanceKey(e.key) && heldDirection === 1) ||
        (isRetreatKey(e.key) && heldDirection === -1)
      ) {
        stopHold();
      }
    }

    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
      stopHold();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- see the comment above this effect
  }, [book, chapter, verse, verseWordCount, router]);

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
        ← / → steps word by word, hold to accelerate ({wordsIncluded}/{verseWordCount})
      </span>
    </form>
  );
}
