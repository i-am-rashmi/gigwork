import geopandas as gpd
from shapely.geometry import box

from locality_import import output


ROWS = [
    {"name": "Zeta", "pin": "560002", "lat": 12.9, "lng": 77.5},
    {"name": "Alpha", "pin": "", "lat": 13.0000004, "lng": 77.6},
    {"name": "Alpha", "pin": "560001", "lat": 12.5, "lng": 77.6},
]


def test_csv_sorted_rounded_and_byte_identical(tmp_path):
    a, b = tmp_path / "a.csv", tmp_path / "b.csv"
    output.write_localities_csv(ROWS, a)
    output.write_localities_csv(list(reversed(ROWS)), b)
    assert a.read_bytes() == b.read_bytes()
    assert a.read_text(encoding="utf-8") == (
        "name,pin,lat,lng\n"
        "Alpha,560001,12.500000,77.600000\n"
        "Alpha,,13.000000,77.600000\n"
        "Zeta,560002,12.900000,77.500000\n"
    )
    assert not a.read_bytes().startswith(b"\xef\xbb\xbf") and b"\r" not in a.read_bytes()


def test_zones_geojson_name_only_sorted_and_deterministic(tmp_path):
    g1 = gpd.GeoDataFrame({"name": ["West", "East"], "geometry": [box(0, 0, 1, 1), box(1, 0, 2, 1)]}, crs=4326)
    g2 = gpd.GeoDataFrame({"name": ["East", "West"], "geometry": [box(1, 0, 2, 1), box(0, 0, 1, 1)]}, crs=4326)
    a, b = tmp_path / "a.geojson", tmp_path / "b.geojson"
    output.write_zones_geojson(g1, a)
    output.write_zones_geojson(g2, b)
    assert a.read_bytes() == b.read_bytes()
    import json
    fc = json.loads(a.read_text())
    assert [f["properties"] for f in fc["features"]] == [{"name": "East"}, {"name": "West"}]
