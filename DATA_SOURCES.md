# Data sources, versions, and licensing

## Hebrew text: Open Scriptures Hebrew Bible (OSHB / morphhb)

- **Source repository:** https://github.com/openscriptures/morphhb
- **File used:** `wlc/Gen.xml`, vendored into this repo at
  `backend/src/main/resources/data/Gen.xml`
- **Underlying text:** Westminster Leningrad Codex (WLC), contributed by
  Christopher V. Kimball via http://www.tanach.us/Tanach.xml — **Public
  Domain**
- **Morphological tagging layer (OSHB's own contribution on top of the WLC
  text):** **Creative Commons Attribution 4.0 International (CC BY 4.0)**
  — https://creativecommons.org/licenses/by/4.0/. Per the morphhb README,
  required attribution is: *"credit the Open Scriptures Hebrew Bible
  Project."*
- **Revision history embedded in the file's own OSIS header** (this is the
  most precise version marker available — the exact upstream git commit this
  copy was downloaded at was not recorded at the time, so treat this repo's
  checksum below as the canonical fingerprint going forward):
  - 2018.12.14 — Release of full morphology
  - 2016.03.03 — Updated to WLC version 4.20
  - 2013.12.11 — First release of morphology on selected portions
  - 2013.07.10 — Updated to WLC version 4.18
  - 2012.07.18 — Updated to WLC version 4.16
  - 2011.01.21 — Updated to WLC version 4.14
  - 2008.08.08 — Converted from TEI markup to OSIS book files
- **Exact fingerprint of the file shipped in this repo:**
  - SHA-256: `0526e5c9a5fb4d907847645f954ed3d1268fa69decbd872056cedd2668d86449`
  - Size: 1,859,928 bytes
  - Content: 1,533 `<verse>` elements, 20,629 `<w>` (word) elements
  - This checksum is recorded automatically at ingestion time (see
    `IngestionMetadata` / `GenesisIngestionRunner`) — if this file is ever
    re-synced from upstream, the app detects the checksum change and
    re-ingests automatically; update the numbers above to match.

### Known parsing note: Ketiv/Qere words are not double-counted

17 verses contain a Ketiv/Qere pair — a place where the Masoretic tradition
preserves two forms of the same word: the *Ketiv* ("what is written", the
literal consonantal text, marked `type="x-ketiv"`) and the *Qere* ("what is
read", the traditionally-recited alternate pointing, nested inside
`<note><rdg type="x-qere">`). `GenesisIngestionRunner` only inspects direct
`<w>` children of `<verse>`, which captures the Ketiv and skips the nested
Qere alternate — so the ingested word count is **20,612**, not the file's raw
20,629 `<w>` tag count. This is a deliberate, documented choice (keep the
literal written text, don't double-count a single word position as two
occurrences), not silent data loss. See Gen.8.17, Gen.13.3, Gen.14.2,
Gen.14.8, Gen.24.33, Gen.25.23, Gen.27.3, Gen.27.29, Gen.30.11 (×2),
Gen.36.5, Gen.36.14, Gen.39.20, Gen.43.28, Gen.49.10, Gen.49.11 (×2) for the
full list.

## Hebrew Lexicon (planned, not yet vendored into this repo)

- **Source repository:** https://github.com/openscriptures/HebrewLexicon
- **Files referenced by the design (`HebrewStrong.xml`,
  `BrownDriverBriggs.xml`):** not yet present in this repository. Per
  PROJECT_NOTES.md, the derivation-chain logic that would consume these files
  has only been validated in standalone Python prototypes and has not been
  ported into the Java ingestion pipeline yet (see Root/Word.root FK, both
  currently unpopulated).
- **License:** Creative Commons Attribution 4.0 International (CC BY 4.0)
  for the lemma/derivation data itself; the underlying Brown-Driver-Briggs
  and Strong's dictionary text is Public Domain. Same attribution
  requirement as above: *"credit the Open Scriptures Hebrew Bible Project."*
- When this data is vendored in, record its exact source file(s), a SHA-256
  checksum, and the upstream commit/release it came from here, the same way
  Gen.xml is documented above.

## Attribution notice (for any public-facing build of this project)

> Hebrew text and morphological tagging from the Open Scriptures Hebrew
> Bible (OSHB) project, https://github.com/openscriptures/morphhb, licensed
> under CC BY 4.0. Underlying Westminster Leningrad Codex text via
> http://www.tanach.us, in the Public Domain.
