import geopandas as gpd
import pytest
from shapely.geometry import Polygon, box

from locality_import import zones


def gdf(names, geoms, crs=4326):
    return gpd.GeoDataFrame({"NewCorp": names, "geometry": geoms}, crs=crs)


def test_clean_zones_names_and_order():
    z = zones.clean_zones(gdf([" West ", "East"], [box(0, 0, 1, 1), box(1, 0, 2, 1)]), "NewCorp", ["East", "West"])
    assert list(z["name"]) == ["East", "West"]


def test_unexpected_zones_raise():
    with pytest.raises(zones.ZoneError):
        zones.clean_zones(gdf(["A", "B"], [box(0, 0, 1, 1), box(1, 0, 2, 1)]), "NewCorp", ["A", "B", "C"])


def test_invalid_geometry_is_fixed():
    bow = Polygon([(0, 0), (1, 1), (1, 0), (0, 1)])  # self-intersecting
    z = zones.clean_zones(gdf(["A"], [bow]), "NewCorp", None)
    assert z.is_valid.all() and z.geometry.iloc[0].area > 0


def test_measure_reports_overlap():
    g = gpd.GeoDataFrame({"name": ["A", "B"], "geometry": [box(0, 0, 100, 100), box(90, 0, 200, 100)]}, crs=32643)
    s = zones.measure(g)
    assert [(a, b, round(m)) for a, b, m in s.overlaps] == [("A", "B", 1000)]
    assert s.gaps == []


def test_measure_reports_gap_as_hole_of_the_union():
    # Four zones ring an empty 100 x 100 square: no overlap, one gap of 10,000 m².
    ring = [box(0, 0, 300, 100), box(0, 200, 300, 300), box(0, 100, 100, 200), box(200, 100, 300, 200)]
    g = gpd.GeoDataFrame({"name": list("ABCD"), "geometry": ring}, crs=32643)
    s = zones.measure(g)
    assert s.overlaps == [] and [round(a) for a in s.gaps] == [10_000]


def test_topology_simplification_keeps_shared_edges_shared():
    # Two neighbours sharing a wiggly edge: after simplifying, there must be no overlap and no gap.
    wiggle = [(100, y + (3 if y % 20 else -3) * 0.5) for y in range(0, 1001, 10)]
    left = Polygon([(0, 0)] + wiggle + [(0, 1000)])
    right = Polygon([(100, 0)] + wiggle[1:-1][::-1][::-1] + [(100, 1000), (200, 1000), (200, 0)])
    z = gpd.GeoDataFrame({"name": ["L", "R"], "geometry": [left, right]}, crs=32643)
    before = zones.measure(z)
    after = zones.measure(zones.simplify_topology(z, 15))
    assert after.vertices["L"] < before.vertices["L"]
    assert after.overlaps == [] and after.gaps == []
