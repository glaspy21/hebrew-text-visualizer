package com.hebrewproject.service;

import com.hebrewproject.model.IngestionMetadata;
import com.hebrewproject.model.Root;
import com.hebrewproject.model.RootOccurrence;
import com.hebrewproject.model.Verse;
import com.hebrewproject.model.Word;
import com.hebrewproject.repository.IngestionMetadataRepository;
import com.hebrewproject.repository.RootOccurrenceRepository;
import com.hebrewproject.repository.RootRepository;
import com.hebrewproject.repository.VerseRepository;
import com.hebrewproject.repository.WordRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Port of parse_gen1.py, wired into Spring's lifecycle via ApplicationRunner
 * (Spring calls run() once, automatically, right after the application context
 * finishes starting up - this is the idiomatic Spring Boot way to do "run this
 * once at boot" work, instead of e.g. a static main-method call).
 *
 * This is intentionally a straightforward DOM parse (DocumentBuilderFactory,
 * built into the JDK - no extra library needed) rather than a streaming parser.
 * Genesis is ~1.9MB of XML; DOM loading the whole thing into memory is fine at
 * this scale and much simpler to read/debug than a streaming (StAX) parser.
 * If we ever ingest the entire 39-book Old Testament in one pass, StAX would
 * be worth revisiting - that's a real, deliberate tradeoff, not an oversight.
 */
@Component
public class GenesisIngestionRunner implements ApplicationRunner {

    private static final String SOURCE_FILE = "data/Gen.xml";
    private static final String LEXICON_FILE = "data/HebrewStrong.xml";

    private final VerseRepository verseRepository;
    private final WordRepository wordRepository;
    private final RootOccurrenceRepository rootOccurrenceRepository;
    private final IngestionMetadataRepository ingestionMetadataRepository;
    private final RootRepository rootRepository;
    private final StrongsLexiconParser lexiconParser;
    private final RootDerivationResolver rootDerivationResolver;

    public GenesisIngestionRunner(VerseRepository verseRepository,
                                   WordRepository wordRepository,
                                   RootOccurrenceRepository rootOccurrenceRepository,
                                   IngestionMetadataRepository ingestionMetadataRepository,
                                   RootRepository rootRepository,
                                   StrongsLexiconParser lexiconParser,
                                   RootDerivationResolver rootDerivationResolver) {
        this.verseRepository = verseRepository;
        this.wordRepository = wordRepository;
        this.rootOccurrenceRepository = rootOccurrenceRepository;
        this.ingestionMetadataRepository = ingestionMetadataRepository;
        this.rootRepository = rootRepository;
        this.lexiconParser = lexiconParser;
        this.rootDerivationResolver = rootDerivationResolver;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        byte[] xmlBytes = readResourceBytes(SOURCE_FILE);
        String checksum = sha256Hex(xmlBytes);

        Optional<IngestionMetadata> existing = ingestionMetadataRepository.findById(SOURCE_FILE);
        if (existing.isPresent() && existing.get().getSha256Checksum().equals(checksum)) {
            System.out.printf("[Ingestion] %s already ingested at sha256 %s (%d verses, %d words, completed %s) - skipping.%n",
                    SOURCE_FILE, checksum, existing.get().getVerseCount(), existing.get().getWordCount(),
                    existing.get().getCompletedAt());
            return;
        }

        // Any mismatch (new dataset content) or absence of a completed-ingestion
        // record (first run, or a prior run that crashed before recording one)
        // means whatever's in these tables can't be trusted as complete - wipe
        // and rebuild from scratch rather than trying to patch/dedupe it.
        if (existing.isPresent()) {
            System.out.println("[Ingestion] Source file checksum changed since last ingest - re-ingesting.");
        } else if (verseRepository.count() > 0) {
            System.out.println("[Ingestion] Existing verse data found with no completed-ingestion record " +
                    "(likely an interrupted prior run) - wiping and re-ingesting.");
        } else {
            System.out.println("[Ingestion] Starting Genesis ingestion...");
        }
        rootOccurrenceRepository.deleteAllInBatch();
        wordRepository.deleteAllInBatch();
        verseRepository.deleteAllInBatch();
        existing.ifPresent(ingestionMetadataRepository::delete);

        Document doc = parseDocument(xmlBytes);

        // Loaded once per ingestion run, not per word - HebrewStrong.xml doesn't
        // change within a run, and re-parsing ~8,700 entries per word would be
        // wasteful. rootCache is the get-or-create memo for resolved Root rows
        // within this same run (see resolveRoot below) - separate from the
        // lexicon map, which is read-only.
        Map<String, StrongsLexiconEntry> lexiconEntries = lexiconParser.parseClasspathResource(LEXICON_FILE);
        Map<String, Root> rootCache = new HashMap<>();

        // running root-occurrence tally, exactly like running_counts in parse_gen1.py
        Map<String, Integer> runningCounts = new HashMap<>();
        long canonicalOrder = 0;
        int wordCount = 0;

        NodeList verseNodes = doc.getElementsByTagName("verse");
        for (int v = 0; v < verseNodes.getLength(); v++) {
            Element verseEl = (Element) verseNodes.item(v);
            String osisId = verseEl.getAttribute("osisID"); // e.g. "Gen.1.1"
            String[] parts = osisId.split("\\.");
            if (parts.length != 3) continue; // defensive: skip malformed IDs

            String book = parts[0];
            int chapterNumber = Integer.parseInt(parts[1]);
            int verseNumber = Integer.parseInt(parts[2]);
            canonicalOrder++;

            Verse verse = new Verse(book, chapterNumber, verseNumber, canonicalOrder);
            verseRepository.save(verse); // save now so it has an ID for the Word FK

            // Only DIRECT <w> children of <verse> count as words. 17 verses in
            // Genesis carry a Ketiv/Qere pair (the Masoretic "written" vs.
            // "read" alternate for one word position) - the Ketiv is a direct
            // child, while the Qere alternate is nested inside
            // <w type="x-ketiv">...</w><note><rdg type="x-qere"><w>...</w></rdg></note>.
            // Only walking direct children keeps the Ketiv (the literal written
            // text) and deliberately ignores the nested Qere, so each word
            // position is counted exactly once. See DATA_SOURCES.md for the
            // full verse list. Total ingested word count is 20,612, not the
            // file's raw 20,629 <w> tag count - that gap is this, not a bug.
            int positionInVerse = 0;
            NodeList children = verseEl.getChildNodes();
            for (int c = 0; c < children.getLength(); c++) {
                Node child = children.item(c);
                if (child.getNodeType() != Node.ELEMENT_NODE) continue;
                Element el = (Element) child;
                if (!"w".equals(el.getTagName())) continue; // skip <seg> (punctuation) etc.

                String surfaceForm = el.getTextContent();
                String rawLemma = el.getAttribute("lemma");
                String rootStrongIdRaw = primaryLemma(rawLemma);

                Root root = resolveRoot(rootStrongIdRaw, lexiconEntries, rootCache);

                Word word = new Word(surfaceForm, rawLemma, rootStrongIdRaw, positionInVerse, verse);
                word.setRoot(root);
                parseMorphology(el.getAttribute("morph"), word);

                int newCount = runningCounts.merge(rootStrongIdRaw, 1, Integer::sum);

                // Word must be saved first so it has a generated ID - RootOccurrence's
                // @JoinColumn(name = "word_id") needs that ID to exist before it can
                // reference it as a foreign key.
                Word savedWord = wordRepository.save(word);
                RootOccurrence occurrence = new RootOccurrence(savedWord, newCount);
                rootOccurrenceRepository.save(occurrence);

                positionInVerse++;
                wordCount++;
            }
        }

        // Written only now, after everything above succeeded - its presence at a
        // matching checksum is what future runs treat as proof of a complete ingest.
        ingestionMetadataRepository.save(new IngestionMetadata(
                SOURCE_FILE, checksum, (int) canonicalOrder, wordCount, Instant.now()));

        System.out.printf("[Ingestion] Done. %d verses, %d words processed. %d unique roots found.%n",
                canonicalOrder, wordCount, runningCounts.size());
    }

    /**
     * Resolve a word's raw Strong's ID to its true Root via
     * RootDerivationResolver, and get-or-create the corresponding Root row -
     * reusing one across every word in this run that resolves to the same ID
     * (rootCache), and reusing one already persisted by an earlier ingestion
     * run (rootRepository.findByStrongId) since Root rows aren't wiped
     * alongside Word/Verse on re-ingestion - they're sourced from
     * HebrewStrong.xml, a separate, stable file from Gen.xml.
     *
     * Root.hebrewPointed/consonantalSkeleton are NOT NULL columns, so a
     * resolved ID with no lexicon entry of its own (e.g. "UNKNOWN" for a
     * missing lemma attribute) falls back to the ID itself/empty string
     * rather than leaving those blank - and is marked derivationUncertain
     * either way, since there's no lexicon data to trust for it.
     */
    private Root resolveRoot(String rawStrongId, Map<String, StrongsLexiconEntry> lexiconEntries, Map<String, Root> rootCache) {
        RootResolution resolution = rootDerivationResolver.resolve(toLexiconLookupId(rawStrongId), lexiconEntries);
        String resolvedId = resolution.getRootStrongId();

        Root cached = rootCache.get(resolvedId);
        if (cached != null) return cached;

        Root root = rootRepository.findByStrongId(resolvedId).orElseGet(() -> {
            StrongsLexiconEntry entry = lexiconEntries.get(resolvedId);
            String hebrewPointed = (entry != null && entry.getHebrewPointed() != null) ? entry.getHebrewPointed() : resolvedId;
            String consonantalSkeleton = (entry != null && entry.getConsonantalSkeleton() != null) ? entry.getConsonantalSkeleton() : "";
            Root newRoot = new Root(resolvedId, hebrewPointed, consonantalSkeleton);
            newRoot.setDerivationUncertain(resolution.isFlagged() || entry == null);
            return rootRepository.save(newRoot);
        });

        rootCache.put(resolvedId, root);
        return root;
    }

    /**
     * Strip OSHB's homonym-sense suffix, e.g. "4427 a" -&gt; "4427" (a trailing
     * space + single lowercase letter marking "sense a of this Strong's
     * number" - distinct from HebrewStrong.xml's own no-space entry-id suffix
     * convention, e.g. "H1a" for a genuinely separate dictionary entry).
     * HebrewStrong.xml has no entry for "4427a"/"4427 a", only "4427" itself -
     * about 3,574 of Genesis's 20,612 words (~17%) carry this suffix in their
     * lemma, so without stripping it here, RootDerivationResolver would treat
     * every one of them as an unknown ID and flag it, rather than actually
     * resolving it. rootStrongIdRaw itself is left untouched (still the raw
     * OSHB value) - this normalization exists only for lexicon lookup.
     */
    private String toLexiconLookupId(String rawStrongId) {
        return rawStrongId.replaceAll("\\s[a-z]$", "");
    }

    /**
     * Strip grammatical prefixes from a lemma attribute, keeping only the final
     * (content-word) segment. e.g. "b/7225" -> "7225", "c/d/776" -> "776".
     * Direct port of primary_lemma() from parse_gen1.py.
     */
    private String primaryLemma(String lemmaAttr) {
        if (lemmaAttr == null || lemmaAttr.isEmpty()) return "UNKNOWN";
        String[] parts = lemmaAttr.split("/");
        return parts[parts.length - 1];
    }

    /**
     * Best-effort parse of OSHB's morph code (e.g. "HVqp3ms") into readable fields.
     * NOTE: this is intentionally a partial decoder covering the common verb/noun
     * cases - OSHB's full morphology code table is extensive. Flagged here as a
     * deliberate scope decision, not an oversight: good enough for MVP display,
     * worth expanding against the official OSHB morphology docs before Phase 2's
     * "filter by exact inflection" feature ships.
     */
    private void parseMorphology(String morphCode, Word word) {
        if (morphCode == null || morphCode.isEmpty()) return;
        // strip leading language marker "H" (Hebrew) or "A" (Aramaic) and split prefixes
        String[] segments = morphCode.split("/");
        String main = segments[segments.length - 1];
        if (main.startsWith("H") || main.startsWith("A")) {
            main = main.substring(1);
        }
        if (main.isEmpty()) return;

        char posChar = main.charAt(0);
        switch (posChar) {
            case 'V' -> word.setPartOfSpeech("Verb");
            case 'N' -> word.setPartOfSpeech("Noun");
            case 'A' -> word.setPartOfSpeech("Adjective");
            case 'P' -> word.setPartOfSpeech("Pronoun");
            case 'R' -> word.setPartOfSpeech("Preposition");
            case 'C' -> word.setPartOfSpeech("Conjunction");
            case 'D' -> word.setPartOfSpeech("Adverb");
            case 'T' -> word.setPartOfSpeech("Particle");
            default -> word.setPartOfSpeech(String.valueOf(posChar));
        }
        // Verb codes look like "Vqp3ms": stem, aspect, person, gender, number
        if (posChar == 'V' && main.length() >= 2) {
            char stemChar = main.charAt(1);
            word.setStem(switch (stemChar) {
                case 'q' -> "qal";
                case 'N' -> "niphal";
                case 'p' -> "piel";
                case 'P' -> "pual";
                case 'h' -> "hiphil";
                case 'H' -> "hophal";
                case 't' -> "hithpael";
                default -> String.valueOf(stemChar);
            });
        }
    }

    private byte[] readResourceBytes(String path) throws Exception {
        try (var is = new ClassPathResource(path).getInputStream()) {
            return is.readAllBytes();
        }
    }

    private String sha256Hex(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private Document parseDocument(byte[] xmlBytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // Security hardening: disable external entity resolution (prevents XXE
        // attacks). Worth doing on ANY XML parser that reads external files,
        // even ones you trust today - defense in depth.
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (var is = new ByteArrayInputStream(xmlBytes)) {
            return builder.parse(is);
        }
    }

}
