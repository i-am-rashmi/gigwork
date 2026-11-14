"""Nearest India Post office for each locality. Pure functions on metric coordinates, plus the CSV loader."""

from __future__ import annotations

import csv
import math
import re
from dataclasses import dataclass
from pathlib import Path

PIN_RE = re.compile(r"^[1-9][0-9]{5}$")


def parse_coord(value) -> float | None:
    """Float, or None for NA / empty / non-numeric / non-finite / zero."""
    try:
        v = float(str(value).strip())
    except (TypeError, ValueError):
        return None
    if not math.isfinite(v) or v == 0.0:
        return None
    return v


@dataclass
class OfficeCounts:
    total: int = 0
    bad_coords: int = 0  # missing, NA, zero or not a number, or lat/lon out of range
    bad_pin: int = 0
    out_of_region: int = 0
    kept: int = 0


def load_offices(csv_path: Path, bbox: tuple[float, float, float, float], margin_deg: float):
    """Read the India Post directory; keep offices with valid coordinates inside `bbox` ± margin.

    bbox = (min_lon, min_lat, max_lon, max_lat). Returns (offices, counts); each office is a dict with
    pin, office, lat, lon. Dropped rows are counted by reason, never silently.
    """
    w, s, e, n = bbox
    c = OfficeCounts()
    out = []
    with open(csv_path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            c.total += 1
            lat, lon = parse_coord(row.get("latitude")), parse_coord(row.get("longitude"))
            if lat is None or lon is None or not (-90 <= lat <= 90 and -180 <= lon <= 180):
                c.bad_coords += 1
                continue
            pin = str(row.get("pincode", "")).strip()
            if not PIN_RE.match(pin):
                c.bad_pin += 1
                continue
            if not (w - margin_deg <= lon <= e + margin_deg and s - margin_deg <= lat <= n + margin_deg):
                c.out_of_region += 1
                continue
            out.append({"pin": pin, "office": str(row.get("officename", "")).strip(), "lat": lat, "lon": lon})
            c.kept += 1
    return out, c


def assign_pins(places: list[dict], offices: list[dict], max_m: float) -> list[dict]:
    """Add pin, pin_office, pin_distance_m to each place (metric x, y; offices need metric x, y too).

    Nearest office within `max_m` wins; ties break on (pin, office name). No match: pin "" and distance None.
    """
    out = []
    for p in places:
        best = None
        for o in offices:
            d = math.hypot(p["x"] - o["x"], p["y"] - o["y"])
            if d <= max_m:
                key = (d, o["pin"], o["office"])
                if best is None or key < best[0]:
                    best = (key, o)
        if best:
            out.append({**p, "pin": best[1]["pin"], "pin_office": best[1]["office"], "pin_distance_m": best[0][0]})
        else:
            out.append({**p, "pin": "", "pin_office": "", "pin_distance_m": None})
    return out
