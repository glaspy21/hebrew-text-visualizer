# Hebrew Text Rarity Visualizer — Frontend Plan

**Status (2026-09-08): planning complete, backend prep complete, frontend
implementation NOT yet started.** This document is the reference for
whoever (or whichever session) starts that work. Read this alongside
[PROJECT_NOTES.md](PROJECT_NOTES.md) (decision history) and
[README.md](README.md) (current backend state) before writing any frontend
code.

Origin: drafted from a design conversation with a colleague, then worked
through and resolved point-by-point in a follow-up session (2026-09-08) -
several open questions in the original draft (color contrast, routing,
accessibility, translation-effort sizing) got concrete answers, recorded
inline below rather than left open.

## Product vision

The frontend is a continuous, immersive Hebrew reading experience that
helps readers see rare-root connections across distant passages. Every
part of it - visual design, animation, English translation, mobile
interfaces - must present the SAME Hebrew-root analysis, not invent a
separate one. Hebrew words and their resolved roots are the canonical
analytical data; everything else (color, English text, native apps) is a
presentation layer on top of that one source of truth.

## Architecture

Decided 2026-09-08, see PROJECT_NOTES.md's decision entry for the full
reasoning: **Next.js (App Router)**, adopted for pages/routing/rendering
only.

```
Next.js frontend  (pages, routing, rendering)
        v
Spring Boot API   (the ONLY API - ingestion, lexicon, root derivation,
        v          homograph/uncertainty, range counting, color calc, DB)
Relational database
```

No Next.js API routes duplicating backend logic. Static generation is an
optimization for known chapter pages, not the central architectural
promise - arbitrary user-selected ranges stay dynamic, fetched client-side
from Spring Boot exactly like the current Vite scaffold already does.

## Backend API contract (verified live, 2026-09-08)

All of the following exist, are tested, and were curled against a live
server this session - not just written, actually confirmed working.

**`GET /api/verses/{book}/{chapter}/{verse}`** - one verse, self-colored.
**`GET /api/verses/{book}/{chapter}?startVerse=&endVerse=`** - a range within one chapter.
**`GET /api/verses/{book}/range?startChapter=&endChapter=`** - a multi-chapter span.

All three return `VerseResponse[]`: `{ osisId, chapter, verse, words: WordResponse[] }`.

`WordResponse`:
| field | type | notes |
|---|---|---|
| `wordId` | number | stable per-occurrence identity - use this to target a specific word |
| `surfaceForm` | string | full inflected Hebrew text, prefixes included |
| `rootStrongIdRaw` | string | raw pre-resolution Strong's id, e.g. `"4427 a"` - display/debug only |
| `resolvedRootId` | number | `Root.id` - the actual grouping key |
| `resolvedRootStrongId` | string | `Root.strongId`, e.g. `"4427"` - pass this to `/api/roots/{id}` |
| `derivationUncertain` | boolean | true when the derivation chain was flagged (depth-1 cap exceeded) |
| `partOfSpeech` | string\|null | |
| `countInRange` | number\|null | occurrences of this root within the REQUESTED range only |
| `colorHexDark` | string\|null | null when `countInRange <= 1` - render NO highlight, not white |
| `colorHexLight` | string\|null | same rule, light theme |
| `homograph` | boolean | passive marker - see Color system below, never merge/hide on this |

**`GET /api/roots/{strongId}`** - rich detail, fetch on click/tap, not per-word:
`{ strongId, hebrewPointed, consonantalSkeleton, transliteration, glossEnglish, derivationUncertain, homograph }`.
404 if unknown. `glossEnglish` is null for ~239 of 8,674 lexicon entries with
no `<meaning>` tag - handle that in the UI, don't assume it's always present.

CORS already allows `http://localhost:3000` (Next.js's default dev port).

## Color system (settled and implemented - not a frontend decision anymore)

Colors render as a highlight BACKGROUND behind Hebrew text, not as the text
color. Two independently-tuned palettes, computed server-side, already WCAG
contrast-verified (see `ColorScaleCalculator`'s javadoc for the full math):

| | page bg | text | green (2x signal) | red (most frequent) |
|---|---|---|---|---|
| Dark (default) | `#121212` | `#F2F0EB` | `#0A7A40` | `#BE2828` |
| Light | `#FAF7F0` | `#1A1A1A` | `#5AAA78` | `#D2786E` |

Rendering rule: `countInRange <= 1` -> no badge at all, plain text
(`colorHexDark`/`colorHexLight` are both `null` for exactly this reason -
check that, don't special-case a color value). `countInRange >= 2` -> a
rounded highlight badge behind the word, using whichever theme is active.

**Accessibility, not yet implemented - do this in the reader, not later:**
exactly-twice is the single most important signal in the whole product
("go compare these two passages"), so it needs a non-color marker (a small
dot/glyph) in addition to the green tint - green/red alone is close to the
classic red-green colorblindness confusion pair. The 3+ fade toward red is
lower-stakes (it's meant to recede) and doesn't need the same treatment.

## Routing

Decided: `/read/[book]/[chapter]` - statically generated
(`generateStaticParams` over known chapters), a shareable entry point whose
initial analytical range is that chapter, but the reader can keep loading
past its boundary without a URL change (see "Continuous reader" below).
`/read/[book]?start=1.1&end=3.5` - always dynamic (query params), for
arbitrary ranges. Both drive the same underlying reader component/state -
the chapter route is a convenience shortcut, not a different mode.

## Analytical range vs. visible window

Two different things, and the backend already respects the distinction:

```
Analytical range = the complete passage used to calculate root counts/colors
Visible window   = the smaller portion currently rendered/visible on screen
```

`colorHexDark`/`colorHexLight`/`countInRange` are always computed
server-side over the FULL range requested, never over "what's currently on
screen" - so incrementally loading or virtualizing the DOM for a large
range is purely a frontend rendering-performance concern. It cannot affect
correctness, because the backend was never told what's visible in the
first place. No backend work needed here regardless of how large ranges get.

## Continuous reader, not chapter pages

A user selecting Genesis 1:1-3:5 should read it as one continuous stream;
chapter boundaries stay visible but don't interrupt reading. This is a
continuous reader with incremental loading, not literally infinite content
(the text has a defined beginning and end).

**Three-verse focus window:** the primary view centers roughly the latest
three verses, full clarity; earlier/later verses stay visible but subdued
(reduced opacity / lower contrast / light blur - start subtle, heavy blur
across large amounts of Hebrew hurts both performance and mobile legibility).

**Open, deliberately not decided in the abstract:** whether the focus
window follows ordinary scrolling automatically or moves through explicit
verse-navigation controls. Build a prototype of both and test - this
strongly shapes the reading feel and shouldn't be guessed at.

## Word identity and typography

`wordId` (from the API) is sufficient stable identity on its own - no need
to reconstruct book+chapter+verse+position client-side for selection or
matching, though chapter/verse are available from the parent `VerseResponse`
whenever displaying context is useful.

Hebrew typography is a major priority - the chosen font must render
consonants, vowel points, cantillation marks, spacing, and line height
clearly. Good typography matters more than decorative animation.

## Root selection interaction

Click/tap a word -> use its `resolvedRootStrongId` to (a) emphasize every
occurrence of that root within the analytical range client-side, (b) fetch
`/api/roots/{resolvedRootStrongId}` for the detail panel (meaning,
transliteration, derivation certainty, homograph warning). Must work by
tap on mobile - hover can enhance desktop but nothing essential depends on it.

## Zoomed-out occurrence overview

Clicking a significant repeated root (especially exactly-twice) can
transition from the reading view to a zoomed-out overview: one rectangle
per chapter, colored marks showing where the root occurs, visual distance
between marks communicating how far apart the occurrences are - directly
expressing the project's central literary idea (a word disappears across a
span, then returns).

Build this as plain HTML/CSS or SVG, and as a SEPARATE representation the
UI transitions to/from - do not attempt to physically shrink thousands of
real word elements into chapter boxes. Simpler, faster, more reliable.

## Animation technology

**Motion (Framer Motion)** is the default, for: focus-window movement,
highlighting matches, fading unrelated text, root-info panels, the
reader<->overview transition, chapter/marker animation. Animation should
explain relationships, not decorate. Support reduced-motion. Avoid
constant pulsing, excessive parallax, anything that delays reading or
risks motion sickness.

**GSAP**: not adopted broadly - reserve it only if the reader-to-overview
transition needs precise, cinematically-choreographed multi-element timing
that Motion demonstrably can't do well. Don't use both for ordinary
interactions.

**Three.js**: not part of the initial reader at all. Only justified if the
occurrence overview becomes genuinely 3D (depth-based distance, a camera
traveling a timeline) - the current rectangle-overview idea doesn't need it.

## Mobile strategy

First mobile milestone: an excellent responsive Next.js web experience
(PWA-installable later), not a native app. A React Native/Expo app is a
later, separate effort that shares TypeScript types, API logic, and
business rules - NOT Next.js pages, DOM CSS, or web-specific animation
code. Practical implication: keep business rules and data processing in
reusable modules, not buried inside visual Next.js components, from the
start. Design every interaction for touch first; nothing essential should
require hover.

## English translation vision

Hebrew stays the analytical source; English is a presentation layer
aligned to it - switching to English must never change which roots are
counted or colored. Because Hebrew/English word relationships aren't 1:1
(one Hebrew word -> several English words, several Hebrew words -> one
English phrase, etc.), English can't be stored as a plain per-verse string
if word-level interaction matters - it needs an alignment layer.

Data model (translations kept separate from the canonical `Word` records):
```
Translation        (name, language, edition, licensing)
Translation Verse   (translation, book, chapter, verse)
Translation Segment (English word/phrase, position in sentence)
Alignment           (segment -> one or more Hebrew words)
```

A custom **wooden translation** (deliberately close to Hebrew wording/
structure) is likely the best starting point, since it can record
alignment during creation rather than fighting a natural-English
translation's word order after the fact. Start with a small sample
(Genesis 1), not all of Genesis, before testing the interface. Clicking
English must select the underlying Hebrew root (via alignment), not search
repeated English vocabulary. Before importing any existing translation,
confirm its license actually permits storage, alignment, and display.

**Effort note:** this phase is curatorial/linguistic work, not engineering
work - producing even a Genesis-1-sized aligned wooden translation is a
different kind of effort than the rest of this plan and shouldn't be
budgeted like an engineering phase.

## Implementation phases

**Phase 1 - Next.js foundation.** Backend side of this is DONE (see API
contract above). Remaining: create the Next.js App Router project
(TypeScript, Tailwind, selective shadcn/Radix, Motion); connect to the
Spring Boot API; port useful logic from the Vite scaffold
(`frontend/src/components/HebrewWord.tsx`, `VerseRow.tsx`, the color/RTL
rendering approach) without preserving its app shell; implement the
routing split above.

**Phase 2 - basic Hebrew reader.** Continuous verses across chapter
boundaries; highlight-background coloring per the Color system above;
high-quality RTL typography; responsive/touch-friendly layout.

**Phase 3 - focus behavior.** Track the active verse; build the
three-verse focus window; subdue earlier/later verses; prototype and test
scroll-following vs. deliberate navigation (see above - genuinely open);
preserve active location in the URL/shareable state.

**Phase 4 - root interaction.** Click/tap -> select resolved root ->
emphasize matches in the analytical range -> root-info panel via
`/api/roots/{id}` -> surface uncertainty/homograph clearly -> the
exactly-twice accessibility marker (see Color system).

**Phase 5 - occurrence overview.** Chapter-rectangle overview in HTML/CSS
or SVG; occurrence markers by chapter/verse position; Motion-based
reader<->overview transition; evaluate GSAP/Three.js only per the
Animation technology guardrails above.

**Phase 6 - large-range performance.** Measure Genesis-sized ranges before
optimizing; the analytical-range/visible-window split already holds
server-side (see above) - this phase is about DOM/rendering strategy
(incremental loading, virtualization), not data correctness; lazy-load
heavy optional visualization code.

**Phase 7 - translation proof of concept.** Add the Translation/Segment/
Alignment schema; build a small wooden-English Genesis-1 sample; test
1:1, 1:many, many:1 alignments; English + parallel reading modes; clicking
English selects the aligned Hebrew root. See the effort note above before
scheduling this like the other phases.

**Phase 8 - mobile progression.** Excellent responsive/touch Next.js
experience first; PWA consideration; React Native/Expo later, reusing
shared types/logic per the Mobile strategy above.

## Immediate recommendation

```
Next.js App Router
+ TypeScript
+ Tailwind
+ selective shadcn/Radix components
+ Motion
+ existing Spring Boot backend (API contract above)
```

No Three.js initially. No GSAP until a working overview demonstrates Motion
is insufficient.

First coherent milestone:
```
Continuous Hebrew reader
-> dark-theme highlight badges (not text color - see Color system)
-> selected analytical range
-> active three-verse focus window
-> click/tap a word
-> emphasize matching roots (resolvedRootStrongId)
-> show basic root details (/api/roots/{id})
```

## Long-term principles

1. Hebrew words and resolved roots are the canonical analytical data.
2. Range-wide colors are independent of what happens to be visible.
3. English segments align to Hebrew words rather than replacing them.
4. Web and native interfaces may differ, while sharing data and application rules.
5. Animation should clarify textual relationships, not decorate.
6. Accessibility, readability, touch behavior, and performance take priority over visual spectacle.
