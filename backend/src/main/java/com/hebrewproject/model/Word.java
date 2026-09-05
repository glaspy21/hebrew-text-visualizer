package com.hebrewproject.model;

import jakarta.persistence.*;

/**
 * One word, in reading-order position within its verse.
 *
 * We deliberately store BOTH the raw OSHB lemma string (for debugging/re-processing
 * later without re-parsing the XML) AND the parsed morphology fields (for querying).
 * Storing only the raw string would mean every query needs string parsing at read
 * time; storing only parsed fields would mean losing the ability to double check
 * or reprocess later. A little redundancy here is a legitimate, common tradeoff.
 */
@Entity
@Table(name = "words", indexes = {
        @Index(name = "idx_root_strong_id", columnList = "rootStrongIdRaw")
})
public class Word {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Full text exactly as written, prefixes included, e.g. "בְּ/רֵאשִׁ֖ית"
    // This is what powers "exact inflected word" search (Phase 2 feature).
    @Column(nullable = false, length = 100)
    private String surfaceForm;

    // The RAW lemma attribute straight from OSHB, e.g. "b/7225" - kept as-is
    // so we can always re-derive anything without re-parsing the source XML.
    @Column(nullable = false, length = 30)
    private String rawLemma;

    // The Strong's ID of the CONTENT word only, prefixes already stripped, e.g. "7225".
    // This is an intermediate value - Phase 2 will resolve this further into
    // a true Root via the derivation-chain logic (root_finder_v2.py), at which
    // point `root` below gets populated. Until then, this raw ID IS what our
    // MVP heatmap counts against.
    @Column(nullable = false, length = 15)
    private String rootStrongIdRaw;

    // Parsed out of OSHB's morph code (e.g. "HVqp3ms" -> V, q, p, 3, m, s).
    // Nullable because particles/proper nouns don't carry full verb morphology.
    @Column(length = 20)
    private String partOfSpeech;   // V(erb), N(oun), A(djective), etc.
    @Column(length = 20)
    private String stem;           // e.g. "qal", "piel", "hiphil"
    @Column(length = 20)
    private String tense;          // e.g. "perfect", "imperfect", "participle"
    @Column(length = 5)
    private String person;         // 1/2/3
    @Column(length = 10)
    private String gender;         // masculine/feminine/common
    @Column(length = 10)
    private String grammaticalNumber; // singular/plural/dual ("number" is a reserved-ish word, renamed for clarity)

    // Position within the verse, 0-indexed - needed for correct left-to-right
    // reconstruction of the verse in the UI (Hebrew renders RTL, but the
    // underlying array order is still "first word to last word").
    @Column(nullable = false)
    private Integer positionInVerse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verse_id", nullable = false)
    private Verse verse;

    // Nullable on purpose: populated once Phase 2's derivation-chain enrichment runs.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_id")
    private Root root;

    @OneToOne(mappedBy = "word", cascade = CascadeType.ALL, orphanRemoval = true)
    private RootOccurrence occurrence;

    protected Word() {
    }

    public Word(String surfaceForm, String rawLemma, String rootStrongIdRaw, Integer positionInVerse, Verse verse) {
        this.surfaceForm = surfaceForm;
        this.rawLemma = rawLemma;
        this.rootStrongIdRaw = rootStrongIdRaw;
        this.positionInVerse = positionInVerse;
        this.verse = verse;
    }

    public Long getId() { return id; }
    public String getSurfaceForm() { return surfaceForm; }
    public String getRawLemma() { return rawLemma; }
    public String getRootStrongIdRaw() { return rootStrongIdRaw; }
    public String getPartOfSpeech() { return partOfSpeech; }
    public void setPartOfSpeech(String partOfSpeech) { this.partOfSpeech = partOfSpeech; }
    public String getStem() { return stem; }
    public void setStem(String stem) { this.stem = stem; }
    public String getTense() { return tense; }
    public void setTense(String tense) { this.tense = tense; }
    public String getPerson() { return person; }
    public void setPerson(String person) { this.person = person; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getGrammaticalNumber() { return grammaticalNumber; }
    public void setGrammaticalNumber(String grammaticalNumber) { this.grammaticalNumber = grammaticalNumber; }
    public Integer getPositionInVerse() { return positionInVerse; }
    public Verse getVerse() { return verse; }
    public Root getRoot() { return root; }
    public void setRoot(Root root) { this.root = root; }
    public RootOccurrence getOccurrence() { return occurrence; }
}
