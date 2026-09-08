"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, type FormEvent } from "react";
import type { VerseSummary } from "@/lib/types";

const HOLD_INITIAL_DELAY_MS = 350; // time before the first auto-repeat
const HOLD_MIN_INTERVAL_MS = 40; // fastest the ramp accelerates to
const HOLD_ACCELERATION = 0.85; // interval shrinks by this factor each tick
// If a resume (see below) would happen more than this long after the last
// real step, treat the hold as stale (the key was probably released while
// the component was unmounted for some other reason) rather than resuming it.
const RESUME_STALE_AFTER_MS = 1000;

interface Position {
  chapter: number;
  verse: number;
  wordsIncluded: number;
}

// Module-level, NOT component state - crossing a verse boundary changes the
// chapter/verse PATH segments, which makes this a different route match, so
// React fully unmounts and remounts RangeNavigator rather than just handing
// it new props. Every ref and piece of local state is discarded on that
// remount, same as a fresh mount would be. Holding a key needs to survive
// that so the ramp doesn't go silent the instant it crosses into a new
// verse: the browser only sends one real keydown per physical press, so if
// nothing here remembers "a key is still down" across the remount, the
// fresh instance has no way to know it should keep ticking.
let heldDirection: 1 | -1 | null = null;
let heldInterval = HOLD_INITIAL_DELAY_MS;
let lastTickAt = 0;

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
 * HOLD_MIN_INTERVAL_MS the longer it's held, and keeps running (ramp speed
 * included) across verse-boundary crossings until keyup - see the
 * module-level state above for why that needs to live outside React.
 *
 * Stepping crosses verse boundaries: retreating past a verse's first word
 * (wordsIncluded 0) lands on the PREVIOUS verse's last word (using data
 * already in versesSoFar - the whole progressive range is already fetched,
 * so backward crossing has no depth limit). Advancing past a verse's last
 * word lands on the NEXT verse's first word (wordsIncluded 0) using the
 * single-verse lookahead in nextVerse - only one boundary of forward
 * headroom is fetched, so a very fast hold that reaches a second forward
 * boundary before that first crossing's navigation has resolved clamps
 * until the fresh data arrives, rather than fetching further ahead itself.
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

  // Always the latest render's data, read fresh at tick time from inside
  // the listener effect below. Synced via an effect, not written during
  // render - React refs must only be read/written outside of render.
  const dataRef = useRef({ chapter, verse, verseWordCount, versesSoFar, nextVerse });
  useEffect(() => {
    dataRef.current = { chapter, verse, verseWordCount, versesSoFar, nextVerse };
  }, [chapter, verse, verseWordCount, versesSoFar, nextVerse]);

  // The ahead-of-the-server position for the life of one hold gesture -
  // fine to re-seed fresh on every mount (including a boundary-crossing
  // remount): the props at that point already reflect wherever the last
  // step landed, which is exactly the right starting point.
  const posRef = useRef<Position>({ chapter, verse, wordsIncluded });
  useEffect(() => {
    if (heldDirection == null) {
      posRef.current = { chapter, verse, wordsIncluded };
    }
  }, [chapter, verse, wordsIncluded]);

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
    let timer: ReturnType<typeof setTimeout> | null = null;

    function wordCountFor(c: number, v: number): number {
      const { chapter, verse, verseWordCount, nextVerse, versesSoFar } = dataRef.current;
      if (c === chapter && v === verse) return verseWordCount;
      if (nextVerse && c === nextVerse.chapter && v === nextVerse.verse) return nextVerse.wordCount;
      return versesSoFar.find((x) => x.chapter === c && x.verse === v)?.wordCount ?? 0;
    }

    function fireStep(direction: 1 | -1) {
      const pos = posRef.current;
      const { chapter, verse, nextVerse, versesSoFar } = dataRef.current;
      let next: Position;

      if (direction === 1) {
        if (pos.wordsIncluded < wordCountFor(pos.chapter, pos.verse)) {
          next = { ...pos, wordsIncluded: pos.wordsIncluded + 1 };
        } else if (nextVerse && pos.chapter === chapter && pos.verse === verse) {
          next = { chapter: nextVerse.chapter, verse: nextVerse.verse, wordsIncluded: 0 };
        } else {
          return; // no more forward data yet - clamp
        }
      } else {
        if (pos.wordsIncluded > 0) {
          next = { ...pos, wordsIncluded: pos.wordsIncluded - 1 };
        } else {
          const idx = versesSoFar.findIndex((x) => x.chapter === pos.chapter && x.verse === pos.verse);
          if (idx > 0) {
            const prev = versesSoFar[idx - 1];
            next = { chapter: prev.chapter, verse: prev.verse, wordsIncluded: prev.wordCount };
          } else {
            return; // at the book's own first verse - clamp
          }
        }
      }

      posRef.current = next;
      lastTickAt = Date.now();
      router.replace(`/read/${book}/${next.chapter}/${next.verse}?wordsIncluded=${next.wordsIncluded}`);
    }

    function tick() {
      if (heldDirection == null) return;
      fireStep(heldDirection);
      heldInterval = Math.max(HOLD_MIN_INTERVAL_MS, heldInterval * HOLD_ACCELERATION);
      timer = setTimeout(tick, heldInterval);
    }

    function startHold(direction: 1 | -1) {
      if (heldDirection === direction) return;
      if (timer != null) clearTimeout(timer);
      heldDirection = direction;
      heldInterval = HOLD_INITIAL_DELAY_MS;
      fireStep(direction);
      timer = setTimeout(tick, heldInterval);
    }

    function stopHold() {
      heldDirection = null;
      heldInterval = HOLD_INITIAL_DELAY_MS;
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

    // Resume a hold that survived a boundary-crossing remount - the key may
    // still be physically down with no new keydown event to tell us that.
    // The staleness check guards against navigating away mid-hold some
    // other way (a Link, browser back) and back again much later, which
    // should NOT resume a phantom hold from a stale module variable.
    if (heldDirection != null && Date.now() - lastTickAt < RESUME_STALE_AFTER_MS) {
      timer = setTimeout(tick, heldInterval);
    } else {
      heldDirection = null;
    }

    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
      // Only this instance's timer - NOT the module-level held state, which
      // must survive a boundary-crossing remount rather than being cancelled.
      if (timer != null) clearTimeout(timer);
      timer = null;
    };
  }, [book, router]);

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
