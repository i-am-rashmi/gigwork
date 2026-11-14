"""Deterministic writers for the committed seed files."""

from __future__ import annotations

import csv
import json
from pathlib import Path

from shapely.geometry import mapping
from shapely.geometry.polygon import orient


def _round(coords):
    if isinstance(coords[0], (int, float)):
        return [round(coords[0], 6), round(coords[1], 6)]
    return [_round(c) for c in coords]


def _fmt(node, depth: int) -> str:
    """Nested coordinate lists, one [x,y] position per line, so a boundary change is a reviewable line diff."""
    pad = "  " * depth
    if isinstance(node[0], (int, float)):
        return f"{pad}[{_num(node[0])},{_num(node[1])}]"
    inner = ",\n".join(_fmt(c, depth + 1) for c in node)
    return f"{pad}[\n{inner}\n{pad}]"


def _num(v: float) -> str:
    return json.dumps(round(v, 6))


def zones_geojson_text(zones_4326) -> str:
    """One feature per block; inside, every coordinate pair on its own line. Valid GeoJSON, deterministic."""
    feats = []
    for name, geom in sorted(zip(zones_4326["name"], zones_4326.geometry), key=lambda t: t[0]):
        if geom.geom_type == "Polygon":
            geom = orient(geom, 1.0)
        else:
            geom = type(geom)([orient(g, 1.0) for g in sorted(geom.geoms, key=lambda g: g.bounds)])
        m = mapping(geom)
        coords = _round(m["coordinates"])
        feats.append(
            f'{{"type":"Feature","properties":{{"name":{json.dumps(name, ensure_ascii=False)}}},'
            f'"geometry":{{"type":"{m["type"]}","coordinates":\n{_fmt(coords, 1)}\n}}}}'
        )
    return '{"type":"FeatureCollection","features":[\n' + ",\n".join(feats) + "\n]}\n"


def write_zones_geojson(zones_4326, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(zones_geojson_text(zones_4326).encode("utf-8"))


class SeedError(Exception):
    pass


_TYPE_ORDER = {"node": 0, "way": 1, "relation": 2}


def source_id(osm_ids: list[str]) -> str:
    """The stable key of a locality: its smallest OSM element id, as 'type/id'.

    Smallest by numeric id (ties: node, way, relation). OSM ids are never reused, so the key survives
    regeneration as long as the same element stays the smallest member of the locality.
    """
    def key(i: str):
        kind, _, num = i.partition("/")
        return int(num), _TYPE_ORDER.get(kind, 9), i

    return min(osm_ids, key=key)


def write_localities_csv(rows: list[dict], path: Path) -> None:
    """rows: dicts with source_id, name, pin, lat, lng. Sorted by (name, lat, lng, source_id); LF endings, UTF-8.

    Raises SeedError, before writing anything, if two rows share a source_id.
    """
    seen: dict[str, str] = {}
    for r in rows:
        if r["source_id"] in seen:
            raise SeedError(f"source_id {r['source_id']} is used by both '{seen[r['source_id']]}' and '{r['name']}'")
        seen[r["source_id"]] = r["name"]
    ordered = sorted(rows, key=lambda r: (r["name"], round(r["lat"], 6), round(r["lng"], 6), r["source_id"]))
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["source_id", "name", "pin", "lat", "lng"])
        for r in ordered:
            w.writerow([r["source_id"], r["name"], r["pin"] or "", f"{r['lat']:.6f}", f"{r['lng']:.6f}"])
