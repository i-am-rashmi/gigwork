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


def Q(name, x, y, place="neighbourhood"):
    return P(name, x, y, place)


def test_is_generic():
    for n in ("4th Block", "1st Block", "Sector 3", "6th Block", "Further Extension", "3rd Cross", "2nd Stage", "Phase 2", "Main 5", "II Phase"):
        assert clean.is_generic(n), n
    for n in ("Jayanagar 4th Block", "HSR Layout", "BTM Layout 2nd Stage", "Koramangala", "Extension Road Colony"):
        assert not clean.is_generic(n), n


def test_generic_gets_nearest_parent_within_radius():
    places = [Q("Jayanagar", 0, 0), Q("Far Place", 1_500, 0), P("4th Block", 400, 0)]
    out, rows, dropped = clean.prefix_generic(places, 2_000)
    assert "Jayanagar 4th Block" in [p["name"] for p in out]
    assert [(r.original, r.new, r.parent, r.distance_m) for r in rows] == [("4th Block", "Jayanagar 4th Block", "Jayanagar", 400)] and dropped == []


def test_generic_without_parent_in_range_is_dropped():
    out, rows, dropped = clean.prefix_generic([Q("Jayanagar", 0, 0), P("Sector 2", 2_500, 0)], 2_000)
    assert [p["name"] for p in out] == ["Jayanagar"] and [n for _, n in dropped] == ["Sector 2"] and rows == []


def test_parent_must_not_be_a_block_village_or_generic():
    places = [Q("Jayanagar 1st Block", 10, 0), P("Hamlet", 20, 0, "hamlet"), P("Vill", 25, 0, "village"), Q("Sector 1", 30, 0), Q("Real Parent", 900, 0), P("4th Block", 0, 0)]
    _, rows, _ = clean.prefix_generic(places, 2_000)
    assert [r.new for r in rows if r.original == "4th Block"] == ["Real Parent 4th Block"]


def test_suburb_beats_a_nearer_neighbourhood():
    places = [Q("Near Nbhd", 100, 0), P("Big Suburb", 1_800, 0, "suburb"), P("2nd Block", 0, 0)]
    _, rows, _ = clean.prefix_generic(places, 2_000)
    assert rows[0].new == "Big Suburb 2nd Block"
    # but a suburb outside the radius loses to a neighbourhood inside it
    _, rows, _ = clean.prefix_generic([Q("Near Nbhd", 100, 0), P("Big Suburb", 2_500, 0, "suburb"), P("2nd Block", 0, 0)], 2_000)
    assert rows[0].new == "Near Nbhd 2nd Block"


def test_family_shares_one_parent_by_majority_vote():
    # Sectors 1-3 are close together; two are nearest to HSR, one is nearest to Agara. All three get HSR.
    places = [P("HSR Layout", 0, 0, "suburb"), P("Agara", 1_900, 0, "suburb"),
              P("Sector 1", 300, 0), P("Sector 2", 600, 0), P("Sector 3", 1_100, 0)]
    out, rows, _ = clean.prefix_generic(places, 2_000)
    assert {r.new for r in rows} == {"HSR Layout Sector 1", "HSR Layout Sector 2", "HSR Layout Sector 3"}
    assert [r.own_parent for r in rows if r.original == "Sector 3"] == ["Agara"]
    assert all(r.family_size == 3 for r in rows)


def test_families_do_not_vote_across_kinds_or_far_apart():
    places = [P("A", 0, 0, "suburb"), P("B", 10_000, 0, "suburb"), P("Sector 1", 100, 0), P("Sector 2", 10_100, 0), P("2nd Phase", 200, 0)]
    _, rows, _ = clean.prefix_generic(places, 2_000)
    assert {r.new for r in rows} == {"A Sector 1", "B Sector 2", "A 2nd Phase"}


def test_vote_winner_out_of_range_for_a_member_keeps_its_own_parent():
    places = [P("A", 0, 0, "suburb"), P("A2", 0, 100, "suburb"), P("B", 3_000, 0, "suburb"),
              P("Sector 1", 100, 0), P("Sector 2", 150, 0), P("Sector 3", 1_900, 0), P("Sector 4", 2_900, 0)]
    _, rows, _ = clean.prefix_generic(places, 2_000)
    by = {r.original: r.new for r in rows}
    assert by["Sector 4"] == "B Sector 4"  # A is 2.9 km away, so Sector 4 keeps its own match


def test_prefixed_name_merges_with_existing_duplicate_after_dedupe_and_keeps_osm_ids():
    places = [Q("Jayanagar", 0, 0), Q("Jayanagar 4th Block", 100, 0), P("4th Block", 200, 0)]
    out, _, _ = clean.prefix_generic(places, 2_000)
    merged, groups = clean.dedupe(out, 1000)
    assert sorted(p["name"] for p in merged) == ["Jayanagar", "Jayanagar 4th Block"] and groups[0].count == 2
    block = [m for m in merged if m["name"] == "Jayanagar 4th Block"][0]
    assert len(block["osm_ids"]) == 2


def test_merged_place_uses_node_coordinates_not_group_centroid():
    node = {**P("Sector 1", 0, 0), "osm": "node/1"}
    rel = {**P("Sector 1", 600, 0), "osm": "relation/2"}
    merged, groups = clean.dedupe([rel, node], 1000)
    assert (merged[0]["x"], merged[0]["y"]) == (0, 0) and merged[0]["osm_ids"] == ["node/1", "relation/2"]
    assert groups[0].max_pairwise_m == 600


def test_two_nodes_and_a_relation_use_the_node_centroid_and_relation_alone_is_used_if_no_node():
    a, b = {**P("X", 0, 0), "osm": "node/1"}, {**P("X", 200, 0), "osm": "node/2"}
    r = {**P("X", 900, 0), "osm": "relation/3"}
    assert clean.dedupe([a, b, r], 1000)[0][0]["x"] == 100
    assert clean.dedupe([r, {**P("X", 1_500, 0), "osm": "way/4"}], 1000)[0][0]["x"] == 1_200
