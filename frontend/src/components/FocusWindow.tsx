"use client";

import { motion } from "motion/react";
import { useRouter } from "next/navigation";
import { useEffect, useLayoutEffect, useRef, useState } from "react";

// The "magnifying glass": a bordered rectangle marking the sharp, in-focus
// zone. Fixed size, not a fixed verse count - however many verses fit in
// this height is what shows, varying with how long each verse's text is.
const FRAME_HEIGHT = "clamp(320px, 45vh, 420px)";
// Blurred margins above/below the frame, inside the same outer container -
// real text (not empty space), per the "I should still see full text on
// the screen... it should be there and blurred" request.
const FRAME_MARGIN = "clamp(110px, 18vh, 180px)";
const FRAME_COLOR = "#4A3728"; // dark brown
// Horizontal breathing room so verse-number labels and word badges never
// crowd the frame's own border.
const FRAME_PADDING_X = "1.25rem";

// Module-level, NOT component state - crossing a verse boundary changes the
// chapter/verse PATH segments, which unmounts and remounts this whole
// component (same root cause as RangeNavigator's hold-across-remount fix).
// Without this, offset would reset to 0 on every remount, and Motion would
// visibly tween from 0 to the correct new position instead of continuing
// smoothly from wherever it already was - a jarring "snap to top, then
// slide back down" glitch on every verse crossing.
let lastOffset = 0;

// How long to wait after the last wheel event before treating a scroll
// gesture as "settled" and snapping the tracker to whatever verse is
// centered - long enough that trackpad momentum's trailing small deltas
// don't each restart the timer forever, short enough that it still reads
// as "right after you stop", not a laggy delay.
const SCROLL_SETTLE_MS = 180;

/** Normalizes wheel delta across input devices - deltaMode 1 is "lines" (a real mouse wheel), 2 is "pages"; only trackpads report 0 ("pixels") natively. */
function normalizedWheelDelta(e: WheelEvent): number {
  if (e.deltaMode === 1) return e.deltaY * 16;
  if (e.deltaMode === 2) return e.deltaY * 320;
  return e.deltaY;
}

/**
 * The "magnifying view" from the range/tracker/navigation discussion: a
 * bordered frame, vertically centered on screen, marking the sharp
 * in-focus zone - text scrolls THROUGH it (a Motion transform, not native
 * page scroll), "rolodex" style, with the tracked word pinned at the
 * frame's vertical CENTER as you navigate (not its bottom edge - revised
 * after live feedback: the tracker should keep the reader's eyes centered
 * in the frame, not anchored low in it). Above and below the frame, inside
 * the same outer container, real text stays visible but FULLY blurred
 * (a flat backdrop-filter, no graduated fade - the frame is the only
 * magnified text, so the transition at its border is a hard cut, not a
 * soft one) - past verses above (their real colors), upcoming
 * not-yet-counted verses below (always uncolored, since they aren't part
 * of the analytical range yet - see page.tsx's peekUpcomingVerses).
 *
 * Position is computed from the delta between the tracked word's and the
 * content container's current getBoundingClientRect() - both live inside
 * the same transformed element, so a uniform translateY shifts them by
 * the same amount and cancels out of the difference, leaving the tracked
 * word's true position regardless of whatever transform is currently
 * applied. (offsetTop/offsetParent looked layout-native and
 * transform-proof in theory, but offsetParent walks through the nearest
 * POSITIONED ancestor and skips right over an unpositioned content
 * wrapper - produced garbage numbers in practice; this is simpler and
 * actually correct.)
 *
 * `seed` freezes this instance's starting offset at first render, and
 * that exact value is passed to Motion's `initial` prop explicitly -
 * relying on Motion's implicit "no initial prop means don't animate on
 * mount" default turned out NOT to reliably hold across a remount that
 * changes `animate` again within the same mount (confirmed by tracing the
 * actual applied transform frame-by-frame: it visibly started from an
 * unrelated intermediate value, not from lastOffset, when initial was
 * left unset). Being explicit removes the ambiguity.
 *
 * Scroll-to-navigate: wheeling with the cursor over the frame moves the
 * content 1:1 with the gesture (no tween - `isScrolling` drops the
 * transition duration to 0 so it doesn't lag behind the wheel), same
 * "rolodex" content but scroll-driven instead of key-driven. Once the
 * wheel goes quiet for `SCROLL_SETTLE_MS`, whichever verse is closest to
 * the frame's vertical center becomes the new tracked verse, landed on
 * its first word (`wordsIncluded=0`) via a route navigation - this
 * reuses the exact same remount -> `lastOffset`/`seed` continuity ->
 * re-center path already verified for arrow-key boundary crossings, so
 * the snap into place after scrolling gets the same jank-free behavior
 * for free rather than needing its own logic.
 */
export function FocusWindow({ children, book }: { children: React.ReactNode; book: string }) {
  const router = useRouter();
  const outerRef = useRef<HTMLDivElement>(null);
  const frameRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  // Lazy initializers (not a ref read during render, which the lint
  // correctly forbids) - `seed` is frozen once for this instance's whole
  // lifetime, used as Motion's explicit `initial` value.
  const [seed] = useState(() => lastOffset);
  const [offset, setOffset] = useState(seed);
  const [isScrolling, setIsScrolling] = useState(false);

  // Centering bug (previously open, see PROJECT_NOTES.md): re-verified live
  // via instrumented tracing (every effect run's computed nextOffset logged
  // alongside the transform Motion actually applied, both immediately and
  // ~500ms after settling) across a backward single-verse crossing, a rapid
  // multi-verse hold-repeat burst, and a chapter-boundary crossing. In all
  // cases the applied transform matched the computed nextOffset exactly - no
  // discrepancy reproduced. The earlier ~250px mismatch is gone, apparently
  // fixed as a side effect of the explicit `initial={{ y: seed }}` fix above
  // (both bugs shared the same underlying cause: an ordering race between
  // this effect and Motion's own effects on a boundary-crossing remount).
  useLayoutEffect(() => {
    const outerEl = outerRef.current;
    const frameEl = frameRef.current;
    const contentEl = contentRef.current;
    if (!outerEl || !frameEl || !contentEl) return;
    const tracked = contentEl.querySelector<HTMLElement>('[data-tracked="true"]');
    if (!tracked) return;

    const outerTop = outerEl.getBoundingClientRect().top;
    const frameRect = frameEl.getBoundingClientRect();
    const desiredCenter = (frameRect.top + frameRect.bottom) / 2 - outerTop;

    const trackedRect = tracked.getBoundingClientRect();
    const contentTop = contentEl.getBoundingClientRect().top;
    const trackedCenterRelativeToContent = (trackedRect.top + trackedRect.bottom) / 2 - contentTop;

    const nextOffset = desiredCenter - trackedCenterRelativeToContent;
    lastOffset = nextOffset;
    setOffset(nextOffset);
    // Re-measure whenever the rendered content itself changes (a new verse
    // list / tracker position from navigation) - not on every unrelated
    // re-render (e.g. a theme toggle), which the empty-array suggestion
    // the linter defaults to would miss entirely.
  }, [children]);

  // Native listener (not React's onWheel) so preventDefault reliably stops
  // the page itself from scrolling - React attaches its own passive
  // listener at the root for wheel events, which silently ignores
  // preventDefault() called from a plain onWheel prop.
  useEffect(() => {
    const outerEl = outerRef.current;
    if (!outerEl) return;

    let settleTimer: ReturnType<typeof setTimeout> | null = null;

    function landOnCenteredVerse() {
      const frameEl = frameRef.current;
      const contentEl = contentRef.current;
      if (!frameEl || !contentEl) return;
      const frameRect = frameEl.getBoundingClientRect();
      const centerY = (frameRect.top + frameRect.bottom) / 2;

      const verseEls = contentEl.querySelectorAll<HTMLElement>("[data-chapter][data-verse]");
      let closest: { chapter: number; verse: number; distance: number } | null = null;
      verseEls.forEach((el) => {
        const rect = el.getBoundingClientRect();
        const elCenter = (rect.top + rect.bottom) / 2;
        const distance = Math.abs(elCenter - centerY);
        if (!closest || distance < closest.distance) {
          closest = { chapter: Number(el.dataset.chapter), verse: Number(el.dataset.verse), distance };
        }
      });
      setIsScrolling(false);
      if (!closest) return;
      router.replace(`/read/${book}/${closest.chapter}/${closest.verse}?wordsIncluded=0`);
    }

    function onWheel(e: WheelEvent) {
      e.preventDefault();
      setIsScrolling(true);
      const delta = normalizedWheelDelta(e);
      // Scrolling down (positive deltaY) reveals later content, same
      // direction as the existing offset convention above (more negative
      // offset = later content shifted into the frame).
      setOffset((o) => {
        const next = o - delta;
        lastOffset = next;
        return next;
      });
      if (settleTimer != null) clearTimeout(settleTimer);
      settleTimer = setTimeout(landOnCenteredVerse, SCROLL_SETTLE_MS);
    }

    outerEl.addEventListener("wheel", onWheel, { passive: false });
    return () => {
      outerEl.removeEventListener("wheel", onWheel);
      if (settleTimer != null) clearTimeout(settleTimer);
    };
  }, [book, router]);

  return (
    <div className="flex justify-center">
      <div
        ref={outerRef}
        className="relative w-full overflow-hidden"
        style={{ height: `calc(${FRAME_MARGIN} + ${FRAME_HEIGHT} + ${FRAME_MARGIN})` }}
      >
        <motion.div
          ref={contentRef}
          initial={{ y: seed }}
          animate={{ y: offset }}
          transition={isScrolling ? { duration: 0 } : { type: "tween", duration: 0.35, ease: "easeOut" }}
          style={{ paddingLeft: FRAME_PADDING_X, paddingRight: FRAME_PADDING_X }}
        >
          {children}
        </motion.div>

        {/* Fully blurred above the frame - flat, no fade. The frame is the
            only magnified text; everything else is a hard cut to blur. */}
        <div
          className="pointer-events-none absolute inset-x-0 top-0"
          style={{ height: FRAME_MARGIN, backdropFilter: "blur(6px)", WebkitBackdropFilter: "blur(6px)" }}
        />

        {/* The magnifying-glass frame itself - a visible border marking the
            sharp/in-focus rectangle. Nothing overlays this zone, so it's
            simply never blurred. */}
        <div
          ref={frameRef}
          className="pointer-events-none absolute inset-x-0 rounded-md border-4"
          style={{ top: FRAME_MARGIN, height: FRAME_HEIGHT, borderColor: FRAME_COLOR }}
        />

        {/* Fully blurred below the frame - flat, no fade. */}
        <div
          className="pointer-events-none absolute inset-x-0 bottom-0"
          style={{ height: FRAME_MARGIN, backdropFilter: "blur(6px)", WebkitBackdropFilter: "blur(6px)" }}
        />
      </div>
    </div>
  );
}
