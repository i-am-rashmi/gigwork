from shapely.geometry import box

from locality_import import clean


def P(name, x, y, place="suburb"):
    return {"name": name, "x": x, "y": y, "place": place, "osm": f"{name}{x}{y}"}


def test_filter_inside_with_buffer():
    zone = box(0, 0, 10_000, 10_000)
    pts = [P("in", 5_000, 5_000), P("buffer", 10_300, 5_000), P("out", 11_000, 5_000)]
    kept = [p["name"] for p in clean.filter_inside(pts, zone, 500)]
    assert kept == ["in", "buffer"]
    assert [p["name"] for p in clean.filter_inside(pts, zone, 0)] == ["in"]


def test_dedupe_merges_same_name_within_distance_at_centroid():
    merged, groups = clean.dedupe([P("Indiranagar", 0, 0), P("indiranagar ".strip(), 600, 0)], 1000)
    assert len(merged) == 1 and merged[0]["x"] == 300 and merged[0]["merged"] == 2
    assert groups[0].count == 2 and groups[0].max_pairwise_m == 600


def test_dedupe_keeps_same_name_beyond_distance_and_different_names():
    merged, groups = clean.dedupe([P("A", 0, 0), P("A", 2_000, 0), P("B", 10, 0)], 1000)
    assert [p["name"] for p in merged] == ["A", "A", "B"] and groups == []


def test_dedupe_chain_is_merged_and_visible_in_report():
    # 0-800-1600: neighbours are within 1 km, the ends are 1.6 km apart.
    merged, groups = clean.dedupe([P("Chain", 0, 0), P("Chain", 800, 0), P("Chain", 1600, 0)], 1000)
    assert len(merged) == 1 and merged[0]["x"] == 800
    assert groups[0].count == 3 and groups[0].max_pairwise_m == 1600


def test_dedupe_is_independent_of_input_order():
    pts = [P("A", 0, 0), P("a", 500, 0), P("B", 5, 5), P("A", 9_000, 0)]
    assert clean.dedupe(pts, 1000)[0] == clean.dedupe(list(reversed(pts)), 1000)[0]


def test_dedupe_picks_most_common_spelling():
    merged, _ = clean.dedupe([P("HSR", 0, 0), P("hsr", 10, 0), P("hsr", 20, 0)], 1000)
    assert merged[0]["name"] == "hsr"
