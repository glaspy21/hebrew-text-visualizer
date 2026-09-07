import re

with open('HebrewStrong.xml', encoding='utf-8') as f:
    content = f.read()

entries = {}
for m in re.finditer(r'<entry id="H(\d+[a-z]?)">(.*?)</entry>', content, re.DOTALL):
    entry_id = m.group(1)
    body = m.group(2)
    source_match = re.search(r'<source>(.*?)</source>', body, re.DOTALL)
    source_text = source_match.group(1) if source_match else ''
    is_primitive = 'primitive root' in source_text or 'a primitive' in source_text
    refs = re.findall(r'src="H(\d+[a-z]?)"', source_text)
    w_match = re.search(r'<w pos="[^"]*"[^>]*>([^<]+)</w>', body)
    entries[entry_id] = {
        'primitive': is_primitive, 'refs': refs,
        'hebrew': w_match.group(1) if w_match else '?',
    }

MAX_DEPTH = 1  # cap: only follow one derivation hop by default

def find_root(strong_id, max_depth=MAX_DEPTH):
    """Returns (root_id, flagged) where flagged=True means the chain was
    longer than max_depth and we stopped early rather than auto-merging."""
    trail = [strong_id]
    current = strong_id
    depth = 0
    while True:
        if current not in entries:
            return current, False
        e = entries[current]
        if e['primitive'] or not e['refs']:
            return current, False
        if depth >= max_depth:
            return current, True  # flagged: deeper chain exists but we stopped
        current = e['refs'][0]
        trail.append(current)
        depth += 1

# Re-validate every case we've tested so far
tests = {
    '4428': 'king -> should still resolve to 4427 (reign)',
    '6763': 'rib -> should still resolve to 6760 (limp)',
    '6662': 'righteous(adj) -> should still resolve to 6663 (be righteous)',
    '1254': 'created -> already primitive, resolves to itself',
    '430':  'Elohim -> should now be FLAGGED, not silently chained to 193',
}

print(f"{'word':45} {'root':8} {'flagged?'}")
print("-" * 65)
for wid, label in tests.items():
    root, flagged = find_root(wid)
    print(f"{label:45} H{root:6} {'⚠ YES - review manually' if flagged else 'no'}")
