import re, unicodedata

with open('HebrewStrong.xml', encoding='utf-8') as f:
    content = f.read()

def strip_to_consonants(word):
    decomposed = unicodedata.normalize('NFD', word)
    consonants_only = ''.join(c for c in decomposed if unicodedata.category(c) != 'Mn')
    consonants_only = ''.join(c for c in consonants_only if '\u05D0' <= c <= '\u05EA')
    return consonants_only

entries = {}
for m in re.finditer(r'<entry id="H(\d+[a-z]?)">(.*?)</entry>', content, re.DOTALL):
    entry_id = m.group(1)
    body = m.group(2)
    source_match = re.search(r'<source>(.*?)</source>', body, re.DOTALL)
    source_text = source_match.group(1) if source_match else ''
    is_primitive = 'primitive root' in source_text or 'a primitive' in source_text
    refs = re.findall(r'src="H(\d+[a-z]?)"', source_text)
    w_match = re.search(r'<w pos="[^"]*"[^>]*>([^<]+)</w>', body)
    pos_match = re.search(r'<w pos="([^"]*)"', body)
    hebrew = w_match.group(1) if w_match else None
    entries[entry_id] = {
        'primitive': is_primitive, 'refs': refs,
        'hebrew_pointed': hebrew,
        'consonantal_skeleton': strip_to_consonants(hebrew) if hebrew else None,
        'pos': pos_match.group(1) if pos_match else '?'
    }

def find_root(strong_id, depth=0):
    if strong_id not in entries or depth > 10:
        return strong_id
    e = entries[strong_id]
    if e['primitive'] or not e['refs']:
        return strong_id
    return find_root(e['refs'][0], depth + 1)

# Build the full root-letters table for every word we've validated so far
test_words = {
    '4428': 'king (melek)',
    '6763': 'rib (tsela)',
    '6662': 'righteous (tsaddiq)',
    '1254': 'created (bara)',
    '430':  'God/Elohim',
}

print(f"{'word':22} {'Strong ID':10} {'root ID':10} {'root (pointed)':16} {'root (letters only)'}")
print("-" * 90)
for wid, label in test_words.items():
    root_id = find_root(wid)
    root_entry = entries.get(root_id, {})
    print(f"{label:22} H{wid:9} H{root_id:9} {root_entry.get('hebrew_pointed','?'):16} {root_entry.get('consonantal_skeleton','?')}")
