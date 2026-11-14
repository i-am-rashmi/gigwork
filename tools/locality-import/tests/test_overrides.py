import pytest

from locality_import import overrides as ov


def write(tmp_path, text):
    f = tmp_path / "overrides.csv"
    f.write_text("osm_id,name,pin,reason\n" + text, encoding="utf-8")
    return f


ROWS = [
    {"name": "Maruthi Nagara", "pin": "560110", "osm_ids": ["node/1"], "pin_distance_m": 5.0},
    {"name": "Maruthi Nagara", "pin": "560063", "osm_ids": ["node/2", "node/3"], "pin_distance_m": 9.0},
    {"name": "Bellanduru", "pin": "", "osm_ids": ["node/4"], "pin_distance_m": None},
]


def test_override_targets_one_of_several_same_named_rows(tmp_path):
    o = ov.load_overrides(write(tmp_path, "node/3,,560037,checked\n"))
    rows, applied = ov.apply_overrides(ROWS, o)
    assert [r["pin"] for r in rows] == ["560110", "560037", ""]
    assert rows[1]["pin_source"] == "override" and applied == [("node/3", "Maruthi Nagara", "Maruthi Nagara", "560063", "560037", "checked")]


def test_empty_name_or_pin_keeps_generated_value(tmp_path):
    rows, _ = ov.apply_overrides(ROWS, ov.load_overrides(write(tmp_path, "node/4,Bellandur,,rename only\n")))
    assert rows[2]["name"] == "Bellandur" and rows[2]["pin"] == ""
    rows, _ = ov.apply_overrides(ROWS, ov.load_overrides(write(tmp_path, "node/1,,560001,pin only\n")))
    assert rows[0]["name"] == "Maruthi Nagara" and rows[0]["pin"] == "560001"


def test_unknown_osm_id_fails(tmp_path):
    with pytest.raises(ov.OverrideError, match="node/999"):
        ov.apply_overrides(ROWS, ov.load_overrides(write(tmp_path, "node/999,,560001,typo\n")))


def test_two_overrides_for_one_merged_locality_fail(tmp_path):
    with pytest.raises(ov.OverrideError):
        ov.apply_overrides(ROWS, ov.load_overrides(write(tmp_path, "node/2,,560001,a\nnode/3,,560002,b\n")))


def test_load_validation(tmp_path):
    assert ov.load_overrides(tmp_path / "missing.csv") == []
    assert ov.load_overrides(write(tmp_path, "")) == []
    for bad in ("node/1,,12,short pin\n", ",x,560001,no id\n", "node/1,,,nothing set\n", "node/1,,560001,a\nnode/1,,560002,dup\n"):
        with pytest.raises(ov.OverrideError):
            ov.load_overrides(write(tmp_path, bad))
