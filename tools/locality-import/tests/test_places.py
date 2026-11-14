import json

from locality_import import places


def test_pick_name_prefers_english():
    assert places.pick_name({"name": "ಇಂದಿರಾನಗರ", "name:en": "Indiranagar"}) == "Indiranagar"


def test_pick_name_falls_back_to_name_and_skips_blank_english():
    assert places.pick_name({"name": "Koramangala", "name:en": "  "}) == "Koramangala"


def test_pick_name_unnamed_is_empty():
    assert places.pick_name({"place": "suburb"}) == ""


def test_normalise_collapses_whitespace_and_nfc():
    assert places.normalise_name("  HSR \t Layout\n") == "HSR Layout"
    assert places.normalise_name("é") == "é"  # combining accent -> NFC
    assert places.normalise_name(None) == ""


def test_parse_elements_uses_center_for_ways_and_skips_no_coords():
    raw = {"elements": [
        {"type": "node", "id": 2, "lat": 12.9, "lon": 77.6, "tags": {"place": "suburb", "name": "B"}},
        {"type": "way", "id": 1, "center": {"lat": 13.0, "lon": 77.5}, "tags": {"place": "quarter", "name:en": "A"}},
        {"type": "relation", "id": 3, "tags": {"place": "suburb", "name": "no coords"}},
    ]}
    got = places.parse_elements(raw)
    assert [(p["osm"], p["name"], p["lat"]) for p in got] == [("node/2", "B", 12.9), ("way/1", "A", 13.0)]


def test_cache_is_reused_unless_refresh(tmp_path):
    cache = tmp_path / "c" / "x.json"
    calls = []

    def fetcher(q):
        calls.append(q)
        return {"elements": [], "n": len(calls)}

    raw, fetched = places.load_raw(cache, "q", False, fetcher)
    assert fetched and raw["n"] == 1 and cache.exists()
    raw, fetched = places.load_raw(cache, "q", False, fetcher)
    assert not fetched and raw["n"] == 1 and len(calls) == 1
    raw, fetched = places.load_raw(cache, "q", True, fetcher)
    assert fetched and raw["n"] == 2
    assert json.loads(cache.read_text())["n"] == 2


def test_query_covers_all_place_types():
    q = places.build_query((77.4, 12.8, 77.8, 13.2))
    for t in ("suburb", "neighbourhood", "quarter", "village", "town", "hamlet"):
        assert t in q
    assert "12.800000,77.400000,13.200000,77.800000" in q  # (south, west, north, east)
