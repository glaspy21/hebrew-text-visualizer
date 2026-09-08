"use client";

import { motion } from "motion/react";
import { useLayoutEffect, useRef, useState } from "react";

// A fixed constant, not a fixed verse count - however many verses fit in
// this height is what shows, which varies with how long each verse's text
// happens to be. Clamped by viewport height too, for smaller windows.
const WINDOW_HEIGHT = "min(560px, 65vh)";
// How far the tracked word's bottom edge sits from the window's own bottom
// edge - the small strip below it stays inside the clear (non-blurred) zone.
const TRACKED_BOTTOM_MARGIN_PX = 64;

/**
 * The "magnifying view" from the range/tracker/navigation discussion: a
 * fixed-size window, vertically centered on screen, that the reading
 * content scrolls THROUGH (via a transform, not native page scroll)
 * rather than the whole page scrolling - a "rolodex" feel where the
 * tracked word's screen position stays consistent (pinned near the
 * window's bottom edge) as you navigate forward or backward, and content
 * above/below fades into a graduated blur rather than disappearing at a
 * hard edge.
 *
 * Position is computed from the DELTA between the tracked word's and the
 * content container's current getBoundingClientRect() - both live inside
 * the same transformed element, so a uniform translateY shifts them by
 * the same amount and cancels out of the difference, leaving the tracked
 * word's true position relative to the content regardless of whatever
 * transform is currently mid-animation. (An earlier version tried to use
 * offsetTop/offsetParent instead, on the theory that transforms don't
 * affect layout - true, but offsetParent walks through the nearest
 * POSITIONED ancestor, which skipped right over the unpositioned content
 * element and produced garbage numbers; this is simpler and was actually
 * correct.)
 */
export function FocusWindow({ children }: { children: React.ReactNode }) {
  const windowRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const [offset, setOffset] = useState(0);

  useLayoutEffect(() => {
    const windowEl = windowRef.current;
    const contentEl = contentRef.current;
    if (!windowEl || !contentEl) return;
    const tracked = contentEl.querySelector<HTMLElement>('[data-tracked="true"]');
    if (!tracked) return;

    const trackedBottomRelativeToContent =
      tracked.getBoundingClientRect().bottom - contentEl.getBoundingClientRect().top;
    const desiredBottom = windowEl.clientHeight - TRACKED_BOTTOM_MARGIN_PX;
    setOffset(desiredBottom - trackedBottomRelativeToContent);
    // Re-measure whenever the rendered content itself changes (a new verse
    // list / tracker position from navigation) - not on every unrelated
    // re-render (e.g. a theme toggle), which the empty-array suggestion
    // the linter defaults to would miss entirely.
  }, [children]);

  return (
    <div className="flex justify-center">
      <div ref={windowRef} className="relative w-full overflow-hidden" style={{ height: WINDOW_HEIGHT }}>
        <motion.div
          ref={contentRef}
          animate={{ y: offset }}
          transition={{ type: "tween", duration: 0.35, ease: "easeOut" }}
        >
          {children}
        </motion.div>

        {/* Graduated blur - content fades toward soft focus at the top and
            bottom edges instead of cutting off abruptly. The tracked word's
            own small margin at the bottom stays inside the clear zone. */}
        <div
          className="pointer-events-none absolute inset-x-0 top-0 h-20"
          style={{
            backdropFilter: "blur(4px)",
            WebkitBackdropFilter: "blur(4px)",
            maskImage: "linear-gradient(to bottom, black, transparent)",
            WebkitMaskImage: "linear-gradient(to bottom, black, transparent)",
          }}
        />
        <div
          className="pointer-events-none absolute inset-x-0 bottom-0 h-10"
          style={{
            backdropFilter: "blur(4px)",
            WebkitBackdropFilter: "blur(4px)",
            maskImage: "linear-gradient(to top, black, transparent)",
            WebkitMaskImage: "linear-gradient(to top, black, transparent)",
          }}
        />
      </div>
    </div>
  );
}
