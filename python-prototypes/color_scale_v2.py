def occurrence_to_color(occurrence: int, max_occurrence: int) -> str:
    """
    1st occurrence -> white (exact)
    2nd occurrence -> bright green (exact)
    max_occurrence -> dark red, almost black (exact)
    everything between 2 and max -> straight-line RGB blend between those two endpoints,
    positioned by (occurrence - 2) / (max_occurrence - 2)
    """
    WHITE = (255, 255, 255)
    BRIGHT_GREEN = (0, 200, 0)
    DARK_RED = (40, 0, 0)  # "almost black" red

    if occurrence <= 1:
        return _to_hex(WHITE)
    if max_occurrence <= 2:
        return _to_hex(BRIGHT_GREEN)  # edge case: no meaningful range yet

    t = (occurrence - 2) / (max_occurrence - 2)
    t = max(0.0, min(t, 1.0))  # clamp

    r = BRIGHT_GREEN[0] + (DARK_RED[0] - BRIGHT_GREEN[0]) * t
    g = BRIGHT_GREEN[1] + (DARK_RED[1] - BRIGHT_GREEN[1]) * t
    b = BRIGHT_GREEN[2] + (DARK_RED[2] - BRIGHT_GREEN[2]) * t
    return _to_hex((r, g, b))

def _to_hex(rgb):
    return "#{:02X}{:02X}{:02X}".format(int(rgb[0]), int(rgb[1]), int(rgb[2]))

# Sanity check against a max of 1005 (the real corpus max, H853 "et")
for occ in [1, 2, 5, 50, 200, 500, 1005]:
    print(f"occurrence {occ:>5} (max=1005) -> {occurrence_to_color(occ, 1005)}")
