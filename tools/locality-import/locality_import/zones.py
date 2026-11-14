"""Zone loading, topology-preserving simplification and overlap/gap measurement."""

from __future__ import annotations

from dataclasses import dataclass, field

import geopandas as gpd
import shapely
from pyproj import CRS
from shapely.geometry import MultiPolygon, Polygon
from shapely.ops import unary_union
from shapely.validation import make_valid
from topojson import Topology

from .geo import to_wgs84, utm_crs_for

DEFAULT_NAME_FIELD = "NewCorp"
DEFAULT_EXPECTED = ("Central", "East", "North", "South", "West")
GRID = 1e-6  # output precision in degrees (6 decimals)


class ZoneError(Exception):
    pass


def _polygons_only(geom):
    """Keep only polygonal parts of a (possibly mixed) geometry."""
    if geom.geom_type in ("Polygon", "MultiPolygon"):
        return geom
    parts = [g for g in getattr(geom, "geoms", []) if g.geom_type in ("Polygon", "MultiPolygon")]
    return unary_union(parts) if parts else Polygon()


def clean_zones(gdf: gpd.GeoDataFrame, name_field: str, expected) -> gpd.GeoDataFrame:
    """One row per zone: clean name, EPSG:4326, valid polygonal geometry.

    Raises ZoneError if the zone names differ from `expected` (when given).
    """
    if name_field not in gdf.columns:
        raise ZoneError(f"zones file has no '{name_field}' column; columns: {list(gdf.columns)}")
    if gdf.crs is None:
        raise ZoneError("zones file has no CRS")
    gdf = gdf.to_crs(4326)
    names = [" ".join(str(n).split()) for n in gdf[name_field]]
    if expected is not None and sorted(names) != sorted(expected):
        raise ZoneError(f"expected zones {sorted(expected)}, found {sorted(names)}")
    if len(set(names)) != len(names):
        raise ZoneError(f"duplicate zone names: {sorted(names)}")
    geoms = [_polygons_only(make_valid(g)) for g in gdf.geometry]
    out = gpd.GeoDataFrame({"name": names, "geometry": geoms}, crs=4326)
    return out.sort_values("name").reset_index(drop=True)


def load_zones(path, name_field=DEFAULT_NAME_FIELD, expected=DEFAULT_EXPECTED) -> gpd.GeoDataFrame:
    return clean_zones(gpd.read_file(path), name_field, expected)


def metric_crs(zones: gpd.GeoDataFrame) -> CRS:
    c = zones.geometry.union_all().centroid
    return utm_crs_for(c.x, c.y)


def simplify_topology(zones_m: gpd.GeoDataFrame, tolerance_m: float) -> gpd.GeoDataFrame:
    """Simplify in a metric CRS with shared arcs simplified once (no new gaps or overlaps)."""
    if tolerance_m <= 0:
        return zones_m
    topo = Topology(zones_m, prequantize=False).toposimplify(tolerance_m)
    out = topo.to_gdf(crs=zones_m.crs)
    out = out.set_index(zones_m["name"].reset_index(drop=True).index)  # keep row order
    out["name"] = zones_m["name"].values
    return gpd.GeoDataFrame(out[["name", "geometry"]], crs=zones_m.crs)


def to_output_grid(zones_m: gpd.GeoDataFrame) -> gpd.GeoDataFrame:
    """Reproject to EPSG:4326 and snap to the 6-decimal output grid, so what we measure is what we write."""
    geoms = []
    for g in zones_m.geometry:
        ll = to_wgs84(g, zones_m.crs)
        snapped = shapely.set_precision(ll, GRID)
        geoms.append(_polygons_only(make_valid(snapped)))
    return gpd.GeoDataFrame({"name": zones_m["name"].values, "geometry": geoms}, crs=4326)


@dataclass
class ZoneStats:
    areas_m2: dict[str, float] = field(default_factory=dict)
    vertices: dict[str, int] = field(default_factory=dict)
    overlaps: list[tuple[str, str, float]] = field(default_factory=list)  # (a, b, m²)
    gaps: list[float] = field(default_factory=list)  # one entry per hole of the union, m²
    interior_rings: dict[str, list[float]] = field(default_factory=dict)  # per zone, ring areas m² (holes inside one zone, e.g. an enclave filled by a neighbour)
    union_area_m2: float = 0.0

    @property
    def overlap_total_m2(self) -> float:
        return sum(a for _, _, a in self.overlaps)


def _vertex_count(g) -> int:
    return len(shapely.get_coordinates(g))


def measure(zones_m: gpd.GeoDataFrame, min_area_m2: float = 0.01) -> ZoneStats:
    """Pairwise overlap areas and gaps (= holes of the union) in square metres."""
    s = ZoneStats()
    rows = list(zip(zones_m["name"], zones_m.geometry))
    for n, g in rows:
        s.areas_m2[n] = g.area
        s.vertices[n] = _vertex_count(g)
        polys_n = g.geoms if isinstance(g, MultiPolygon) else [g]
        s.interior_rings[n] = sorted((Polygon(r).area for p in polys_n for r in p.interiors), reverse=True)
    for i, (na, ga) in enumerate(rows):
        for nb, gb in rows[i + 1:]:
            a = ga.intersection(gb).area
            if a > min_area_m2:
                s.overlaps.append((na, nb, a))
    union = unary_union([g for _, g in rows])
    s.union_area_m2 = union.area
    polys = union.geoms if isinstance(union, MultiPolygon) else [union]
    for p in polys:
        for ring in p.interiors:
            a = Polygon(ring).area
            if a > min_area_m2:
                s.gaps.append(a)
    s.gaps.sort(reverse=True)
    return s
