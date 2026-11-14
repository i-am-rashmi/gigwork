from locality_import import pins

HEADER = "circlename,regionname,divisionname,officename,pincode,officetype,delivery,district,statename,latitude,longitude\n"


def O(pin, office, x, y):
    return {"pin": pin, "office": office, "x": x, "y": y}


def test_nearest_office_wins_within_max():
    got = pins.assign_pins([{"name": "A", "x": 0, "y": 0}], [O("560002", "far", 2_000, 0), O("560001", "near", 500, 0)], 3_000)
    assert got[0]["pin"] == "560001" and got[0]["pin_distance_m"] == 500


def test_no_office_within_max_gives_empty_pin():
    got = pins.assign_pins([{"name": "A", "x": 0, "y": 0}], [O("560001", "x", 3_001, 0)], 3_000)
    assert got[0]["pin"] == "" and got[0]["pin_distance_m"] is None


def test_equal_distance_tie_breaks_deterministically():
    offices = [O("560009", "z", 100, 0), O("560003", "y", -100, 0)]
    assert pins.assign_pins([{"name": "A", "x": 0, "y": 0}], offices, 3_000)[0]["pin"] == "560003"
    assert pins.assign_pins([{"name": "A", "x": 0, "y": 0}], offices[::-1], 3_000)[0]["pin"] == "560003"


def test_parse_coord_rejects_na_zero_and_junk():
    for bad in ("NA", "", "0", "0.0", "abc", "nan", "inf", None):
        assert pins.parse_coord(bad) is None
    assert pins.parse_coord("12.9716") == 12.9716


def test_load_offices_discards_invalid_and_out_of_region(tmp_path):
    f = tmp_path / "pins.csv"
    f.write_text(
        HEADER
        + 'c,r,d,"Good B.O",560001,BO,Delivery,D,KARNATAKA,12.97,77.59\n'
        + 'c,r,d,"NA B.O",560002,BO,Delivery,D,KARNATAKA,NA,NA\n'
        + 'c,r,d,"Zero B.O",560003,BO,Delivery,D,KARNATAKA,0,0\n'
        + 'c,r,d,"Far B.O",600001,BO,Delivery,D,TAMIL NADU,13.08,80.27\n'
        + 'c,r,d,"BadPin B.O",5600,BO,Delivery,D,KARNATAKA,12.98,77.60\n'
        + 'c,r,d,"Swapped B.O",560004,BO,Delivery,D,KARNATAKA,77.59,12.97\n',
        encoding="utf-8",
    )
    offices, c, _ = pins.load_offices(f, (77.4, 12.8, 77.8, 13.2), 0.05)
    assert [o["pin"] for o in offices] == ["560001"]
    assert (c.total, c.bad_coords, c.bad_pin, c.out_of_region, c.kept) == (6, 2, 1, 2, 1) and c.outliers == 0


def test_filter_by_prefix_drops_offices_geocoded_from_elsewhere():
    offices = [O("560001", "a", 0, 0), O("562125", "b", 0, 0), O("577534", "wrong city", 0, 0)]
    kept, dropped = pins.filter_by_prefix(offices, ("560", "562"))
    assert [o["pin"] for o in kept] == ["560001", "562125"] and dropped == 1


def test_wrong_prefix_office_never_wins_even_if_nearest():
    offices, _ = pins.filter_by_prefix([O("577534", "wrong", 10, 0), O("560001", "right", 2_500, 0)], ("560",))
    assert pins.assign_pins([{"name": "A", "x": 0, "y": 0}], offices, 3_000)[0]["pin"] == "560001"


def test_modal_prefix():
    assert pins.modal_prefix([O("560001", "a", 0, 0), O("560002", "b", 0, 0), O("577001", "c", 0, 0)]) == "560"
    assert pins.modal_prefix([]) == ""



def OF(pin, name, lat, lon):
    return {"pin": pin, "office": name, "lat": lat, "lon": lon}


def test_outlier_far_from_the_median_of_its_pin_is_dropped():
    offices = [OF("560004", "a", 12.95, 77.57), OF("560004", "b", 12.96, 77.58), OF("560004", "c", 12.95, 77.58),
               OF("560004", "Basavanagudi-ish wrong", 12.97, 77.65)]  # ~7 km east of the others
    kept, out, single = pins.find_outliers(offices, 4_000)
    assert [o["office"] for o in out] == ["Basavanagudi-ish wrong"] and len(kept) == 3 and single == set()
    assert 4_000 < out[0]["dist_m"] < 10_000 and not out[0]["pair"]


def test_close_offices_are_all_kept_and_threshold_is_configurable():
    offices = [OF("560001", "a", 12.95, 77.57), OF("560001", "b", 12.96, 77.58), OF("560001", "c", 12.955, 77.575)]
    assert pins.find_outliers(offices, 4_000)[1] == []
    assert len(pins.find_outliers(offices, 100)[1]) >= 1


def test_single_office_pin_is_unverifiable_not_dropped():
    kept, out, single = pins.find_outliers([OF("560009", "lonely", 12.9, 77.6)], 4_000)
    assert len(kept) == 1 and out == [] and single == {"560009"}


def test_two_far_apart_offices_are_both_dropped_as_a_pair():
    kept, out, _ = pins.find_outliers([OF("560017", "N", 12.95, 77.57), OF("560017", "V", 13.5, 77.57)], 4_000)
    assert kept == [] and [o["pair"] for o in out] == [True, True]
    kept, out, _ = pins.find_outliers([OF("560017", "N", 12.95, 77.57), OF("560017", "V", 12.96, 77.57)], 4_000)
    assert len(kept) == 2 and out == []


def test_outlier_is_judged_against_all_offices_of_the_pin_even_outside_the_region(tmp_path):
    f = tmp_path / "p.csv"
    f.write_text(HEADER + "".join(
        f'c,r,d,"{n}",560004,BO,Delivery,D,KARNATAKA,{la},{lo}\n'
        for n, la, lo in [("a", 12.95, 77.57), ("b", 12.96, 77.58), ("c", 12.95, 77.58), ("wrong", 12.97, 77.65)]), encoding="utf-8")
    offices, c, info = pins.load_offices(f, (77.4, 12.8, 77.8, 13.2), 0.05)
    assert sorted(o["office"] for o in offices) == ["a", "b", "c"] and c.outliers == 1
    assert [o["office"] for o in info["outliers"]] == ["wrong"]


def test_offices_stacked_on_one_point_with_three_pins_are_dropped():
    pt = (12.9447, 77.574)
    offices = [OF("560017", "Vimanapura", *pt), OF("560043", "Kalyananagar", *pt), OF("560038", "Indiranagar", *pt),
               OF("560004", "Basavanagudi H.O", 12.9454, 77.5735), OF("560001", "MG Road", 12.97, 77.6)]
    kept, dropped = pins.drop_shared_points(offices)
    assert sorted(o["office"] for o in dropped) == ["Indiranagar", "Kalyananagar", "Vimanapura"] and dropped[0]["shared_pins"] == 3
    assert sorted(o["office"] for o in kept) == ["Basavanagudi H.O", "MG Road"]


def test_two_pins_at_one_point_and_many_offices_of_one_pin_are_kept():
    a = [OF("560032", "R T Nagar", 13.0216, 77.5928), OF("560006", "J C Nagar", 13.0216, 77.5928)]
    b = [OF("560001", "x", 12.9, 77.5), OF("560001", "y", 12.9, 77.5), OF("560001", "z", 12.9, 77.5)]
    assert pins.drop_shared_points(a + b)[1] == []
