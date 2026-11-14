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


def zones_geojson_text(zones_4326) -> str:
    feats = []
    for name, geom in sorted(zip(zones_4326["name"], zones_4326.geometry), key=lambda t: t[0]):
        if geom.geom_type == "Polygon":
            geom = orient(geom, 1.0)
        else:
            geom = type(geom)([orient(g, 1.0) for g in sorted(geom.geoms, key=lambda g: g.bounds)])
        m = mapping(geom)
        feats.append(
            {"type": "Feature", "properties": {"name": name},
             "geometry": {"type": m["type"], "coordinates": _round(m["coordinates"])}}
        )
    return json.dumps({"type": "FeatureCollection", "features": feats}, sort_keys=True, ensure_ascii=False, separators=(",", ":")) + "\n"


def write_zones_geojson(zones_4326, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(zones_geojson_text(zones_4326).encode("utf-8"))


def write_localities_csv(rows: list[dict], path: Path) -> None:
    """rows: dicts with name, pin, lat, lng. Sorted by (name, lat, lng); LF endings, UTF-8, no BOM."""
    ordered = sorted(rows, key=lambda r: (r["name"], round(r["lat"], 6), round(r["lng"], 6)))
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(["name", "pin", "lat", "lng"])
        for r in ordered:
            w.writerow([r["name"], r["pin"] or "", f"{r['lat']:.6f}", f"{r['lng']:.6f}"])
