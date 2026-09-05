# Hebrew Text Rarity Visualizer

A tool for surfacing a literary pattern in the Hebrew Bible: when an author
deliberately brings back a rare root word for its second appearance, that
repetition is often an intentional signal inviting the reader to compare the
two passages. This project visualizes that pattern directly, computing a
color for every word based on how often its true Hebrew root recurs within
whatever span of text you're reading.

## The core idea

- A word's root that appears **once** in the displayed range is **white** —
  not yet a pattern.
- A root that appears **exactly twice** is **bright green**, applied to both
  occurrences — this is the signal: the text is drawing a deliberate
  connection between two passages, and the reader is meant to go compare them.
- A root that appears **three or more times** fades from green toward a
  dark, almost-black red as it approaches whichever root is most frequent in
  that range — common, structural words recede into the background so they
  don't compete visually with the rare, meaningful connections.

Colors are computed live, per requested range — not baked in once. The same
root can be white in one view and green in a wider one, because the pattern
is about relative rarity within what you're actually reading, not a fixed
global fact about the word.

## Why this is harder than it sounds: true root derivation

Hebrew nouns, verbs, and adjectives from the same three-consonant root often
carry different dictionary entries (e.g. מֶלֶךְ "king" and מָלַךְ "to reign"
are different Strong's numbers but the same root). This project resolves
words back to their true root by walking Strong's Concordance's own
derivation chains — with a depth cap, since deep chains often reflect
speculative 19th-century etymology rather than settled scholarship (verified
against Brown-Driver-Briggs, which explicitly flags several of these as
disputed). It also detects homographs — different roots that happen to look
identical once Masoretic vowel points are stripped away (e.g. squeeze vs.
slaughter, both שחט in bare consonants).

**Status:** the derivation-chain enrichment above is validated in Python
prototypes but not yet wired into the live Java pipeline — see
[PROJECT_NOTES.md](PROJECT_NOTES.md) for details. The live API today groups
words by their raw Strong's ID (prefixes stripped), not yet by resolved root.

## Tech stack

- **Backend:** Java 17+, Spring Boot 3.3, Spring Data JPA
- **Database:** H2 (local dev) — designed to move to Azure SQL / Postgres
- **Frontend:** React + TypeScript (Vite) — early scaffold, see
  [`frontend/`](frontend/)
- **Data sources:** OpenScriptures Hebrew Bible (Westminster Leningrad Codex
  with morphology tagging) and OpenScriptures Hebrew Lexicon (Strong's +
  Brown-Driver-Briggs), both open/CC-licensed — see
  [DATA_SOURCES.md](DATA_SOURCES.md) for exact versions and license text
- **Planned:** Docker, Azure Kubernetes Service, Application Insights, an AI
  agent layer for natural-language queries

## Repository layout

```
backend/                 Spring Boot API (Java 17, Maven)
  src/main/java/com/hebrewproject/
    model/                Verse, Word, Root, RootOccurrence, IngestionMetadata - JPA entities
    repository/           Spring Data JPA repositories
    service/              ColorScaleCalculator, RangeColorCalculator, GenesisIngestionRunner
    controller/           VerseController - REST API
    config/               WebConfig - CORS for local frontend dev
  src/main/resources/data/Gen.xml   vendored OSHB source text (see DATA_SOURCES.md)
  src/test/java/...       automated tests for the color algorithm and range behavior

frontend/                 React + TypeScript (Vite) - early scaffold, not yet feature-complete
```

On startup, `GenesisIngestionRunner` parses `Gen.xml` once, strips
grammatical prefixes from each word's lemma, and stores every word with its
verse, position, and morphology. Colors are not stored — `RangeColorCalculator`
computes them fresh whenever a range is requested, since the whole point is
that a word's color depends on the range you're viewing it within.

Ingestion tracks completion explicitly: an `IngestionMetadata` row (source
file + SHA-256 checksum + counts + timestamp) is written only after a full
ingest succeeds. On startup, the app only skips re-ingesting when that record
exists **and** its checksum matches the current `Gen.xml` — any mismatch, or
its absence despite existing verse rows (e.g. a prior run crashed mid-way),
triggers a clean wipe-and-reingest instead of silently trusting whatever's in
the tables.

## Running the backend locally

Requires **Java 17+** and **Maven**.

```bash
cd backend
mvn spring-boot:run
```

First run parses Genesis and populates a local H2 database (`backend/data/`,
gitignored). Watch for:

```
[Ingestion] Done. 1533 verses, 20612 words processed. 1837 unique roots found.
```

(20,612 rather than the file's raw 20,629 `<w>` tags — see the Ketiv/Qere
note in [DATA_SOURCES.md](DATA_SOURCES.md).)

Then query the API:

```
http://localhost:8080/api/verses/Gen/1?startVerse=1&endVerse=20
```

### Running tests

```bash
cd backend
mvn test
```

Covers `ColorScaleCalculator` (the white → green → dark-red blend, including
a regression test that no yellow/orange ever appears) and
`RangeColorCalculator` (range-scoped counting: a root's color depends only on
the list of words passed in for that specific request, recomputed fresh each
call).

## API

| Endpoint | Description |
|---|---|
| `GET /api/verses/{book}/{chapter}/{verse}` | A single verse |
| `GET /api/verses/{book}/{chapter}?startVerse=&endVerse=` | A verse range within one chapter |
| `GET /api/verses/{book}/range?startChapter=&endChapter=` | A multi-chapter span |

Every response includes each word's Hebrew text, root ID, part of speech,
count within the requested range, and computed color. Only Genesis (`Gen`)
is ingested today; other books return an empty list rather than an error.

## Frontend

An early Vite + React + TypeScript scaffold lives in [`frontend/`](frontend/).
It queries the chapter/verse-range endpoint above and renders the returned
words RTL with their computed colors. Run it with:

```bash
cd frontend
npm install
npm run dev
```

The backend's `WebConfig` allows CORS from `http://localhost:5173` for this.

## Status / Roadmap

- [x] Data pipeline (OSHB parsing, prefix stripping)
- [x] Range-scoped, uniform-per-root color algorithm
- [x] Spring Boot API serving Genesis with live-computed colors
- [x] Automated tests for the color algorithm and range behavior
- [x] Explicit dataset/version tracking for ingestion (checksum-based, not "table is non-empty")
- [ ] Lexicon-derivation enrichment wired into the live API (currently
      validated in Python prototypes, not yet ported into the Java pipeline)
- [ ] Homograph flagging in the database
- [ ] React frontend (early scaffold exists, not feature-complete)
- [ ] Docker + Azure deployment (App Service or AKS)
- [ ] Multi-word / verse-range proximity search

## Data licensing

Text and lexicon data from OpenScriptures, licensed CC BY 4.0. See
[DATA_SOURCES.md](DATA_SOURCES.md) for exact file versions, checksums, and
full attribution text, and
[openscriptures/morphhb](https://github.com/openscriptures/morphhb) /
[openscriptures/HebrewLexicon](https://github.com/openscriptures/HebrewLexicon)
upstream.
