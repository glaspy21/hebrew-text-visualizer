"use client";

import { motion } from "motion/react";
import { useLayoutEffect, useRef, useState } from "react";

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
 */
export function FocusWindow({ children }: { children: React.ReactNode }) {
  const outerRef = useRef<HTMLDivElement>(null);
  const frameRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  // Lazy initializers (not a ref read during render, which the lint
  // correctly forbids) - `seed` is frozen once for this instance's whole
  // lifetime, used as Motion's explicit `initial` value.
  const [seed] = useState(() => lastOffset);
  const [offset, setOffset] = useState(seed);

  // KNOWN BUG, not yet fixed: live-measured after a verse-boundary crossing
  // (Gen 1:13 wordsIncluded=0 -> retreat -> Gen 1:12 wordsIncluded=18) and
  // the tracked word ("טוֹב", verse 12's real last word) landed ~250px
  // below the frame's bottom edge, not at its center. Direct measurement:
  // desiredCenter computed as 376.6 (correct - matches the frame's own
  // center relative to outer), but the applied transform was -759.156 while
  // recomputing the SAME formula moments later against the live DOM gave
  // -1010.1 - a ~250px discrepancy between what should apply and what's
  // actually applied. Two live-verified facts ruled out so far:
  // (1) not a stale-render/timing issue - re-measured ~1.2s after the
  //     crossing settled, well past the 350ms transition;
  // (2) the frame element itself is being found correctly (its rect matches
  //     the CSS clamp() values exactly).
  // Not yet diagnosed: why the DOM's actual transform doesn't match what
  // this effect computes and calls setOffset with. Suspect either (a) a
  // second, later effect run overwriting nextOffset with a stale
  // measurement taken before Motion finished applying a PRIOR transform (an
  // ordering issue between this effect and Motion's own internal effects on
  // the same remount - see the "seed" doc comment above for a related but
  // different Motion-timing bug already fixed), or (b) getBoundingClientRect
  // being read against a not-yet-reflowed layout for the newly-lengthened
  // upcomingVerses content on this particular verse. Start by re-running the
  // same live-instrumented trace used to fix the remount-glitch bug above
  // (poll the applied transform value + log this effect's inputs/outputs on
  // every run, not just assume one run happens per crossing).
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
          transition={{ type: "tween", duration: 0.35, ease: "easeOut" }}
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
