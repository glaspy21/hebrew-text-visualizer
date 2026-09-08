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
// How far the tracked word's bottom edge sits from the FRAME's own bottom
// edge - the small strip below it stays inside the frame's clear interior.
const TRACKED_BOTTOM_MARGIN_PX = 56;

/**
 * The "magnifying view" from the range/tracker/navigation discussion: a
 * bordered frame, vertically centered on screen, marking the sharp
 * in-focus zone - text scrolls THROUGH it (a Motion transform, not native
 * page scroll), "rolodex" style, with the tracked word pinned near the
 * frame's bottom edge as you navigate. Above and below the frame, inside
 * the same outer container, real text stays visible but graduated-blurred
 * rather than hidden - past verses above (still their real colors),
 * upcoming not-yet-counted verses below (always uncolored, since they
 * aren't part of the analytical range yet - see page.tsx's
 * peekUpcomingVerses).
 *
 * Position is computed from the delta between the tracked word's and the
 * content container's current getBoundingClientRect() - both live inside
 * the same transformed element, so a uniform translateY shifts them by
 * the same amount and cancels out of the difference, leaving the tracked
 * word's true position regardless of whatever transform is currently
 * mid-animation. (offsetTop/offsetParent looked layout-native and
 * transform-proof in theory, but offsetParent walks through the nearest
 * POSITIONED ancestor and skips right over an unpositioned content
 * wrapper - produced garbage numbers in practice; this is simpler and
 * actually correct.)
 */
export function FocusWindow({ children }: { children: React.ReactNode }) {
  const outerRef = useRef<HTMLDivElement>(null);
  const frameRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const [offset, setOffset] = useState(0);

  useLayoutEffect(() => {
    const outerEl = outerRef.current;
    const frameEl = frameRef.current;
    const contentEl = contentRef.current;
    if (!outerEl || !frameEl || !contentEl) return;
    const tracked = contentEl.querySelector<HTMLElement>('[data-tracked="true"]');
    if (!tracked) return;

    const outerTop = outerEl.getBoundingClientRect().top;
    const frameBottom = frameEl.getBoundingClientRect().bottom;
    const desiredBottom = frameBottom - outerTop - TRACKED_BOTTOM_MARGIN_PX;

    const trackedBottomRelativeToContent =
      tracked.getBoundingClientRect().bottom - contentEl.getBoundingClientRect().top;
    setOffset(desiredBottom - trackedBottomRelativeToContent);
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
          animate={{ y: offset }}
          transition={{ type: "tween", duration: 0.35, ease: "easeOut" }}
        >
          {children}
        </motion.div>

        {/* Graduated blur above the frame - solid blur near the outer edge,
            fading toward sharp as it nears the frame border. */}
        <div
          className="pointer-events-none absolute inset-x-0 top-0"
          style={{
            height: FRAME_MARGIN,
            backdropFilter: "blur(5px)",
            WebkitBackdropFilter: "blur(5px)",
            maskImage: "linear-gradient(to bottom, black 55%, transparent)",
            WebkitMaskImage: "linear-gradient(to bottom, black 55%, transparent)",
          }}
        />

        {/* The magnifying-glass frame itself - a visible border marking the
            sharp/in-focus rectangle. Nothing overlays this zone, so it's
            simply never blurred. */}
        <div
          ref={frameRef}
          className="pointer-events-none absolute inset-x-0 rounded-md border-4"
          style={{ top: FRAME_MARGIN, height: FRAME_HEIGHT, borderColor: FRAME_COLOR }}
        />

        {/* Graduated blur below the frame - fading from sharp near the
            frame border to solid blur near the outer edge. */}
        <div
          className="pointer-events-none absolute inset-x-0 bottom-0"
          style={{
            height: FRAME_MARGIN,
            backdropFilter: "blur(5px)",
            WebkitBackdropFilter: "blur(5px)",
            maskImage: "linear-gradient(to top, black 55%, transparent)",
            WebkitMaskImage: "linear-gradient(to top, black 55%, transparent)",
          }}
        />
      </div>
    </div>
  );
}
