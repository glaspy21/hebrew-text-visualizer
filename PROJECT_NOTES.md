# Hebrew Text Rarity Visualizer — Project Notes

## Session: 2026-09-07 — homograph flagging wired into Java

Closed roadmap item 2, following the scoping decision from the previous
homograph write-up below (cluster on CONFIRMED PRIMITIVE roots only -
`StrongsLexiconEntry.isPrimitiveRoot()` true for a root's own lexicon entry,
not merely `derivationUncertain == false`). Landed as four reviewable
commits:

1. Added `Root.homograph`, a persisted boolean, defaulting false.
2. Added `HomographClusterer` (pure logic, no DB/Spring context) - groups
   `Root` rows by `consonantalSkeleton` among confirmed primitives only,
   keeping clusters of size 2+. `HomographClustererTest` runs it against the
   real vendored `HebrewStrong.xml` and **re-verifies the 83-cluster figure
   by actually computing it** (previously only a manual write-up claim), plus
   confirms H193 is correctly excluded despite passing a naive
   `derivationUncertain` check.
3. Wired it into `GenesisIngestionRunner` as a post-ingestion pass over every
   persisted `Root` row, persisting the flag both ways (so a stale flag can't
   survive unnoticed). Deliberately made this run on EVERY startup, not
   gated behind Gen.xml's checksum-skip check - otherwise it hits the exact
   operational catch from the root-derivation session (a DB ingested before
   this feature existed would never get flags set without manual
   intervention). `GenesisIngestionRunnerIntegrationTest` covers the שחט
   case directly against the real ingested data.
4. Exposed the flag via `VerseController.WordResponse.homograph` - additive
   metadata on the existing per-word response; grouping, counting, and
   coloring are all unchanged. Verified live against the real running API for
   both cited verses (see table below).

**Real numbers, re-verified by actually running the code (not trusted from
the prior write-up alone):**
- **83 clusters among ALL 1,335+ primitive-marked entries in the whole
  vendored lexicon** - confirmed exactly via `HomographClustererTest`,
  running the real `HomographClusterer` + `StrongsLexiconParser` against the
  real `HebrewStrong.xml`. This is the reference figure from "Key design
  decisions" below, now backed by passing code rather than a manual count.
- **26 clusters / 53 roots flagged among Genesis's own 1,249 ingested
  roots**, under the same confirmed-primitives-only filter - this is the
  actual production number, from a live ingestion run. It's a different,
  smaller figure than the whole-lexicon 83 (expected: Genesis only uses a
  subset of all primitive roots) and than the 113/25-way-noisy-cluster
  figures previously estimated under a looser, non-primitives-only filter.

**Real bug hit and fixed along the way:** Hibernate's `ddl-auto=update`
generates a bare `alter table roots add column homograph boolean not null`
with no default value - H2 rejects this against a table that already has
rows (`NULL not allowed for column "HOMOGRAPH"`). Same class of issue, same
fix, as the previous session's `IngestionMetadata` gap: delete the local
`backend/data/` H2 files once and let a fresh ingestion recreate the schema
from scratch. This project's `ddl-auto=update` is explicitly scoped to local
dev only (see `application.properties`'s own comment) - a real migration
tool (Flyway/Liquibase) would handle this gracefully, but isn't worth
adopting yet at this project's size.

## Session: 2026-09-07 — root-derivation enrichment wired into Java

Closed the biggest documented gap between the Python prototypes and the live
Java pipeline: root derivation is now real, in the running app, not just
validated in standalone scripts. Landed as five reviewable commits:

1. Vendored `HebrewStrong.xml` (2.75MB, 8,674 entries) from
   openscriptures/HebrewLexicon into `backend/src/main/resources/data/`,
   documented in DATA_SOURCES.md with its checksum and upstream commit -
   this unblocked actually running (not just reading) the derivation logic.
2. Added the missing `RootRepository`.
3. Ported `root_finder_v2.py`'s depth-1-capped derivation walk to Java
   (`StrongsLexiconParser`, `RootDerivationResolver`) - tested against the
   real vendored lexicon (not mocks), re-validating all five cases from the
   table below.
4. Wired it into `GenesisIngestionRunner`: every word's raw Strong's ID is
   now resolved and linked via `Word.root`, with `Root` rows get-or-created
   and cached per ingestion run.
5. Switched `RangeColorCalculator` to group by the resolved `Root` instead
   of `rootStrongIdRaw` - the actual visible behavior change. Verified live:
   every occurrence of king/reign across Genesis 36 (12 combined) now
   returns identical count and color from the real API.

**Real bug found and fixed along the way:** OSHB tags ~17% of Genesis's
words (3,574 of 20,612) with a homonym-sense suffix in their lemma (e.g.
`"4427 a"`), which `HebrewStrong.xml` has no entry for - only the bare
`"4427"`. Without normalizing this before lexicon lookup, every one of those
words would've been wrongly flagged as unresolved (including the one place
in Genesis the "reign" verb itself appears - Gen 36:31). Fixed via
`GenesisIngestionRunner.toLexiconLookupId()`, used only for lexicon lookup;
`rootStrongIdRaw` itself is left untouched, per its documented "raw" contract.

**Real numbers from a full ingestion run:** 1,837 distinct raw Strong's IDs
collapse down to **1,249** true resolved roots, of which **287** are flagged
`derivationUncertain` (chain went deeper than the depth-1 cap, or the ID
wasn't found in the lexicon at all - e.g. the rare bare-prefix-only lemma
edge case, ~2.6% of words, see `GenesisIngestionRunner.toLexiconLookupId`'s
doc comment).

**Operational catch for anyone with an existing local DB:** `IngestionMetadata`
only tracks `Gen.xml`'s checksum, not `HebrewStrong.xml`'s - so a DB ingested
before this session's changes will NOT automatically re-ingest and pick up
`Root`/`Word.root` population (Gen.xml itself hasn't changed). Delete
`backend/data/` once and restart to force a fresh ingestion.

**Deliberately out of scope this session:** homograph detection (a separate
concern per "Key design decisions" below - grouping resolved roots that
happen to share a consonantal skeleton despite being unrelated words). Root
rows do get a `consonantalSkeleton` value (needed to satisfy the NOT NULL
column), but nothing clusters on it yet - still roadmap item 2 below.

## Session: 2026-09-05 — backend consolidation & verification

Before starting frontend work for real, did a full pass to make sure the
backend is actually reproducible by someone other than this machine's
existing `data/`/`target/` state. Findings:

- **Real bug: `.gitignore`'s bare `data/` pattern was silently excluding
  `src/main/resources/data/Gen.xml` from git.** A bare `data/` (no leading
  slash) matches a directory named `data` at ANY depth, not just the
  repo-root local-H2-db folder it was meant for. This meant `Gen.xml` — the
  actual source text the whole app ingests — was never committed. A fresh
  clone would have had nothing to ingest. Fixed by anchoring the pattern
  (`/backend/data/`, `/backend/target/`) and committing `Gen.xml` for real.
- **Moved the Spring Boot project into `backend/`** (via `git mv`, history
  preserved) to make room for `frontend/` as a sibling, per the README's
  layout section.
- **Replaced the `verseRepository.count() > 0` ingestion skip check** with an
  `IngestionMetadata` entity keyed on source file + SHA-256 checksum, written
  only after a full ingest succeeds. Verified all three states work:
  (1) fresh empty DB → ingests and records metadata, (2) restart with
  matching checksum → skips cleanly, (3) verse rows present but metadata
  record missing (simulated by deleting the row mid-state, standing in for a
  crashed prior run) → detected and self-healed via wipe-and-reingest, no FK
  errors. This directly fixes the exact failure mode described below under
  "Bugs hit and fixed this session" from the varchar(5) incident, which used
  to require manually deleting `data/` by hand.
- **Found and documented (not silently "fixed") a real discrepancy:** the
  ingested word count is 20,612, not the 20,629 raw `<w>` tags in `Gen.xml`.
  Root cause: 17 verses carry a Ketiv/Qere pair (Masoretic written-vs-read
  alternate for one word position); the Qere alternate is nested inside
  `<note><rdg type="x-qere">` rather than being a direct child of `<verse>`,
  so `GenesisIngestionRunner`'s direct-children-only loop naturally keeps
  only the Ketiv. This is a defensible choice (the literal written text,
  no double-counting one word position) but was never documented anywhere
  before now — see the inline comment in `GenesisIngestionRunner` and the
  full verse list in `DATA_SOURCES.md`. The old README's example log line
  ("20629 words processed") was simply never actually verified against a
  real run.
- **Added `DATA_SOURCES.md`** recording the exact OSHB/morphhb file used,
  its embedded revision history, a SHA-256 fingerprint of the shipped
  `Gen.xml`, verified license text for both morphhb and HebrewLexicon (CC BY
  4.0 for the tagging/lemma layers, Public Domain for the underlying WLC/BDB/
  Strong's text — confirmed against both repos' READMEs directly, not
  assumed), and the required attribution line.
- **Added automated tests** (`ColorScaleCalculatorTest`,
  `RangeColorCalculatorTest`) covering the white/green/dark-red blend
  (including a regression test that no yellow/orange ever appears — the
  exact bug the HSL-rotation version had), the range-relative max, and that
  a root occurring twice gets an identical color on both occurrences
  regardless of what else is in the range.
- **Verified reproducibility end-to-end**: materialized the staged working
  tree into a clean temp directory via `git archive` (no `data/`/`target/`
  carried over), ran `mvn clean package` and `mvn spring-boot:run` from
  scratch there, confirmed ingestion completes from an empty DB, and
  exercised all three API endpoints plus edge cases (invalid verse → 404,
  unknown book/chapter range → empty list, not an error).
- **Not yet done:** none of this has been committed/pushed — changes are
  staged locally pending explicit go-ahead, per this session's own working
  agreement about not committing without being asked.

## Current status (prior session)

**Working end-to-end:** Spring Boot backend running locally, real Genesis
data ingested, REST API serving range-based, literarily-meaningful colors.
Pushed to GitHub. README written. This is a genuinely functional MVP of the
core backend — next major milestones are the frontend and lexicon enrichment.

**GitHub repo:** github.com/glaspy21/hebrew-text-visualizer (confirm exact
URL/visibility once pushed)
**Local path:** ~/Development/hebrew-text-visualizer (backend now at
`~/Development/hebrew-text-visualizer/backend/`, see session note above)

## What the project does

Visualizes a literary device in the Hebrew Bible: when an author brings back
a rare root word for exactly its SECOND appearance, that's often a deliberate
signal inviting the reader to compare the two passages. The tool colors every
word by how often its true Hebrew root recurs within whatever range of text
is being viewed:
- Root appears once in range -> white (not yet a pattern)
- Root appears exactly twice in range -> bright green, on BOTH occurrences
  (the signal - go compare these two passages)
- Root appears 3+ times -> fades toward dark, almost-black red as it
  approaches the range's own most-frequent root - common words recede into
  the background so they don't compete with the green signal

Colors are computed LIVE per requested range, not fixed once at ingestion -
the whole point is that "rare" is relative to what you're currently reading.

## Data sources (both open/CC-licensed)
- **openscriptures/morphhb** — Westminster Leningrad Codex, per-word
  morphology/lemma tagging (`wlc/Gen.xml`, one file per book)
- **openscriptures/HebrewLexicon** — `HebrewStrong.xml` (Strong's dictionary
  with derivation chains via `<source>` tags), `BrownDriverBriggs.xml` (BDB,
  more academically rigorous, explicitly flags disputed etymologies)

## Key design decisions and why

**Root grouping, not surface word matching.** Track roots via Strong's
derivation chains (walk `<source>` references back to whatever's marked "a
primitive root"), not raw lemma strings — so a verb, noun, and adjective from
the same root (e.g. מָלַךְ "reign" / מֶלֶךְ "king" / מַלְכָּה "queen") all
count as the same recurring idea. Grammatical prefixes (ו/ה/ב/ל/כ/את) are
stripped before counting so they don't dominate the signal.

**Derivation chains are capped at depth 1, and deeper chains get FLAGGED
rather than auto-resolved.** Case study: Elohim's Strong's chain goes 4 hops
deep to a disputed 19th-century etymology (root meaning "twist/strength" via
"ram"). BDB itself calls this "intricate...conclusions dubious" and lists a
competing theory (root אלה, "to fear/revere"). Nobody actually knows which is
right, including the sources themselves — so the tool refuses to silently
pick a side on cases like this.

**Homograph detection is separate from root grouping.** Some roots are
spelled identically once Masoretic vowel points are stripped, despite being
totally unrelated words (e.g. שָׂחַט "squeeze" Gen 40:11 vs. שָׁחַט
"slaughter" Gen 22:10 — both שחט in bare consonants). These get flagged for
the UI, not merged and not ignored. **Done as of the 2026-09-07 homograph
session above** — `HomographClusterer` implements exactly the decision
below, wired into ingestion and exposed as `Root.homograph` /
`WordResponse.homograph`. The design decision, verified against the real
vendored lexicon before implementation, and re-verified by actually running
the code afterward:

- **83 clusters is real, but only for TRUE PRIMITIVE roots** (1,335
  primitives → exactly 83 clusters, up to 6-way) - this is almost certainly
  where the "83" figure originally came from.
- **Clustering on what the app actually stores instead** (`Root` rows,
  including flagged depth-1 stopping points like Elohim's H433) gives **518
  clusters across 1,219 of 3,706 roots - a third of all roots** - because
  non-primitive intermediate nodes collide far more than true primitives do.
- Among just the 1,249 roots Genesis actually uses: 113 genuine clusters,
  plus one noisy 25-way "cluster" from roots with no real lexicon entry
  (empty-skeleton fallback, e.g. bare-prefix-only lemmas) that must be
  filtered out or the count is meaningless.
- **Decided (2026-09-07): cluster on confirmed primitive roots only** -
  matches the documented "83", and is the more conservative choice: a `Root`
  already flagged `derivationUncertain` (287 of 1,249 in Genesis, e.g.
  Elohim's H433) never participates in homograph clustering either, since we
  can't confirm it's truly primitive in the first place. Don't conflate
  `derivationUncertain == false` with "is primitive," though - they're not
  quite the same set. H193 ("from an unused root meaning to twist...") has NO
  `<source>` refs and so resolves with `flagged=false`, but its source text
  never says "a primitive root" either - it'd pass a `derivationUncertain`
  check but fail a strict `isPrimitiveRoot()` one. "Confirmed primitive"
  means the latter: filter on `StrongsLexiconEntry.isPrimitiveRoot()` being
  true for the root's own lexicon entry, not on the absence of a flag.
- **Re-verified (2026-09-07, homograph session): 83 is confirmed real** -
  `HomographClustererTest` computes it directly from the real
  `HebrewStrong.xml`, not trusted from this write-up. The actual production
  figure, over just Genesis's 1,249 ingested roots under this same filter,
  is **26 clusters / 53 roots flagged** - smaller than 83 because Genesis
  only uses a subset of all primitive roots, and a different (stricter)
  number than the 113/25-way-noisy-cluster estimates above, which used a
  looser, non-primitives-only filter.

**Idea, not yet scoped: an active "search/view by bare consonants" mode**
(distinct from homograph flagging above - don't conflate the two). Homograph
flagging is passive: a quiet marker on the existing true-root view, warning
"this word's bare consonants also match something unrelated." What's
described here is different - a toggle the reader actively switches on, so
words are grouped/searched by `Root.consonantalSkeleton` directly instead of
by resolved Strong's-ID root, deliberately surfacing pairs like שָׂחַט
(squeeze) and שָׁחַט (slaughter) - two different primitive roots (H7818,
H7819) that only collide once vowel points are stripped - as a single view.
The raw data already exists (`consonantalSkeleton` is computed and stored
for every `Root`), so this needs no new backend derivation work - it's a
frontend + API feature (a different grouping mode plus a UI toggle), most
naturally designed alongside the React frontend work, not the homograph
flagging handoff.

**Coloring is uniform-per-root-within-range, NOT positional.** (This
superseded an earlier "progressive" design where color depended on a word's
position in reading order vs. a running max - that version made the SAME
root show different colors depending on which occurrence you were looking
at, which doesn't serve the actual goal.) Real validated example: אֶחָד
("one") occurs exactly twice in Gen 1:1-2:10 - once in 1:5 ("one day"), once
in 1:9 ("one place") - both render identical bright green (#00C800).

**Color scale is a direct RGB blend, not an HSL hue rotation.** An earlier
version rotated through the color wheel from green(120deg) to red(0deg),
which visually swept through yellow/orange - a side effect nobody asked for.
Fixed to blend directly between two fixed endpoints: WHITE(255,255,255),
BRIGHT_GREEN(0,200,0), DARK_RED(40,0,0). Only shades of green and red appear.

**The scale's red-end ceiling = max count across ALL words in the CURRENT
RANGE**, grammatical particles included (explicit user choice - tested and
confirmed this makes semantically meaningful words like "God"/"earth" land
much closer to red than excluding particles would, since without particles
in the ceiling, content words rarely reach true red).

## Backend architecture (Spring Boot, Java 17)

Now at `backend/` (moved from repo root this session, see above).

```
backend/src/main/java/com/hebrewproject/
  model/        Verse, Word, Root, RootOccurrence, IngestionMetadata
  repository/   Spring Data JPA repositories
  service/      ColorScaleCalculator, RangeColorCalculator, GenesisIngestionRunner
  controller/   VerseController - REST API
  config/       WebConfig - CORS for local frontend dev
backend/src/test/java/com/hebrewproject/service/
  ColorScaleCalculatorTest, RangeColorCalculatorTest
```

- `GenesisIngestionRunner` parses `src/main/resources/data/Gen.xml` once on
  startup (DOM parser, XXE-hardened), strips prefixes, stores every word.
  Ingestion is now guarded by `IngestionMetadata` (source file + SHA-256
  checksum), not a bare row-count check - see the session note above.
- `RangeColorCalculator` computes colors LIVE per request: groups whatever
  words are in the requested range by root, counts occurrences within that
  list specifically, finds the range's own max, colors accordingly, applies
  uniformly to every word sharing a root.
- `VerseController` fetches ALL words across the WHOLE requested range FIRST,
  then calls the color calculator ONCE over the combined list - critical,
  since per-verse coloring would miss cross-verse patterns entirely (the
  אֶחָד example spans verses 5 and 9).
- `RootOccurrence.colorHex` is unused/nullable - superseded by live
  computation. `runningCount` still stored as raw data, not currently used
  by the main API.
- `Word.rootStrongIdRaw` is still the RAW Strong's ID, kept as-is per its
  documented contract - `Word.root` is now populated at ingestion time (see
  the 2026-09-07 session above) and is what `RangeColorCalculator` actually
  groups by.

## Bugs hit and fixed this session
- `partOfSpeech` column was declared `varchar(5)` but `parseMorphology()`
  writes full words like "Particle" (8 chars) / "Preposition" (11 chars) -
  crashed ingestion partway through with a DataIntegrityViolationException.
  Fixed: widened column to varchar(20). Also required wiping the local H2
  `data/` folder before rerunning, since partial ingestion had already
  written some verses, which would've made the app skip re-ingestion
  entirely on the next run (guarded by `if (verseRepository.count() > 0)`).

## Validated root-derivation test cases (now checked in both Python and Java)

| Words | Result | Notes |
|---|---|---|
| מֶלֶךְ (king) / מָלַךְ (reign) / מַלְכָּה (queen) | Same root (H4427) | Clean cross-POS derivation. Java-verified live: 12 combined occurrences across Genesis 36, identical color |
| צֵלָע (rib, Gen 2:22) / צֹלֵעַ (limping, Gen 32:32) | Same root (H6760) | Real intertextual echo |
| שָׂחַט (squeeze, Gen 40:11) / שָׁחַט (slaughter, Gen 22:10) | Different roots, same consonantal skeleton | HOMOGRAPH, not a shared root - not yet clustered on (see "Not yet built" #2) |
| צַדִּיק (righteous, Gen 6:9) | Traces to verb צָדַק (H6663) | Adjective -> verb grouping works |
| אֱלֹהִים (Elohim) | FLAGGED, resolves one hop to H433, not chained further | Disputed etymology, see above |

Java regression coverage: `RootDerivationResolverTest` (pure walk, against
the real vendored lexicon) and `GenesisIngestionRunnerIntegrationTest` (full
Spring context + real ingestion, checks actual persisted `Word.root` state)
in `backend/src/test/java/com/hebrewproject/service/`.

## Python prototype files (in hebrew-project-code/ output folder)
- `parse_gen1.py` — OSIS XML parsing, prefix stripping
- `color_scale_v2.py` — the corrected direct-RGB-blend color function (use
  this one, not the original `color_scale.py`, which used HSL hue rotation)
- `root_finder_v2.py` — Strong's derivation-chain walker with MAX_DEPTH=1 cap
- `extract_root_letters.py` — pulls real Hebrew root spelling for display

## Tech stack (for resume alignment — Bandwidth Java/Azure job target)
- Backend: Java + Spring Boot + Spring Data JPA (built, running locally)
- DB: H2 locally now -> Azure SQL or Postgres flexible server (not started)
- Deploy: Docker + Azure Kubernetes Service (not started)
- Monitoring: Azure Application Insights (not started)
- Frontend: React + TypeScript (early Vite scaffold started this session -
  fetches a verse range and renders colored RTL Hebrew; not feature-complete)
- Stretch: AI agent layer (tool-calling) on top of the REST API (not started)

Resume currently lists these as "Currently studying" / "Currently building"
- accurate honesty check: do NOT upgrade to a claimed skill/resume bullet
until each piece is actually built and running, per earlier discussion about
not overclaiming ahead of real experience.

## Not yet built (roadmap)
1. ~~Lexicon-derivation enrichment wired into the live Java pipeline~~ - DONE,
   see the 2026-09-07 root-derivation session above
2. ~~Homograph flagging in the DB~~ - DONE, see the 2026-09-07 homograph
   session above
3. React frontend (rendering the actual colored Hebrew text visually)
4. Docker containerization
5. Azure deployment (App Service or AKS) + Application Insights
6. Multi-word / arbitrary-start-point proximity search (Phase 2 feature)
7. Inflection-specific filtering UI (search by stem/tense/person/gender)
8. Global word index for word-by-word/verse-by-verse navigation UI (backend
   range queries already support this via startVerse/endVerse params -
   frontend navigation UI not built)
9. Active "search/view by bare consonants" toggle - separate, not-yet-scoped
   idea, see "Key design decisions" above; not the same feature as #2

## Git workflow established
- Repo initialized, `.gitignore` excludes `backend/target/` and `backend/data/`
  (local H2 db regenerates fresh on ingestion, shouldn't be committed).
  **Fixed this session:** the original patterns were bare `target/`/`data/`
  with no leading slash, which match at any depth - that's what silently
  excluded `backend/src/main/resources/data/Gen.xml` (the actual source text)
  from git the whole time. Now anchored so only the root-level build/db dirs
  are ignored.
- Habit going forward: `git add .` -> `git commit -m "..."` -> `git push`
  after each meaningful change
