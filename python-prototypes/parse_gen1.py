import xml.etree.ElementTree as ET

NS = {'osis': 'http://www.bibletechnologies.net/2003/OSIS/namespace'}

tree = ET.parse('morphhb/wlc/Gen.xml')
root = tree.getroot()

def local(tag):
    return tag.split('}')[-1]

def primary_lemma(lemma_attr):
    """Strip grammatical prefixes (conjunction, article, preposition, obj marker),
    keep only the final lexical root segment."""
    parts = lemma_attr.split('/')
    return parts[-1]

running_counts = {}
results = []

for chapter in root.iter():
    if local(chapter.tag) != 'chapter':
        continue
    chapter_id = chapter.attrib.get('osisID')
    if chapter_id != 'Gen.1':
        continue
    for verse in chapter.iter():
        if local(verse.tag) != 'verse':
            continue
        verse_id = verse.attrib.get('osisID')
        for w in verse.iter():
            if local(w.tag) != 'w':
                continue
            lemma_raw = w.attrib.get('lemma', '')
            word_text = ''.join(w.itertext())
            root_key = primary_lemma(lemma_raw)
            running_counts[root_key] = running_counts.get(root_key, 0) + 1
            results.append({
                'verse': verse_id,
                'word': word_text,
                'lemma_raw': lemma_raw,
                'root': root_key,
                'occurrence': running_counts[root_key]
            })

# Print Genesis 1:1-3 as a sanity check
for r in results:
    if r['verse'] in ('Gen.1.1', 'Gen.1.2', 'Gen.1.3'):
        print(f"{r['verse']:10} {r['word']:15} root={r['root']:10} occurrence #{r['occurrence']}")

print(f"\nTotal words in Genesis 1: {len(results)}")
print(f"Unique roots in Genesis 1: {len(running_counts)}")
