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
    offices, c = pins.load_offices(f, (77.4, 12.8, 77.8, 13.2), 0.05)
    assert [o["pin"] for o in offices] == ["560001"]
    assert (c.total, c.bad_coords, c.bad_pin, c.out_of_region, c.kept) == (6, 2, 1, 2, 1)


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


def test_overrides_apply_by_name_and_report_unmatched(tmp_path):
    f = tmp_path / "o.csv"
    f.write_text("name,pin,reason\nBellanduru,560103,checked on India Post site\nNowhere,560001,typo\n", encoding="utf-8")
    ov = pins.load_overrides(f)
    rows = [{"name": "Bellanduru", "pin": "", "pin_distance_m": None}, {"name": "Other", "pin": "560001", "pin_distance_m": 5.0}]
    out, applied, unmatched = pins.apply_overrides(rows, ov)
    assert out[0]["pin"] == "560103" and out[0]["pin_source"] == "override" and out[1]["pin"] == "560001"
    assert applied == [("Bellanduru", "(none)", "560103", "checked on India Post site", 1)]
    assert [u["name"] for u in unmatched] == ["Nowhere"]


def test_overrides_missing_empty_and_invalid(tmp_path):
    assert pins.load_overrides(tmp_path / "none.csv") == []
    e = tmp_path / "e.csv"; e.write_text("name,pin,reason\n", encoding="utf-8")
    assert pins.load_overrides(e) == []
    import pytest
    bad = tmp_path / "b.csv"; bad.write_text("name,pin,reason\nX,12,why\n", encoding="utf-8")
    with pytest.raises(ValueError):
        pins.load_overrides(bad)
