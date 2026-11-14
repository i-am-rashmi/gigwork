"""Nearest India Post office for each locality. Pure functions on metric coordinates, plus the CSV loader."""

from __future__ import annotations

import csv
import math
import re
import statistics
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
    shared_point: int = 0  # offices stacked on one exact point with 3+ other PINs (geocoder default)
    outliers: int = 0  # valid coordinates and PIN, but far from the rest of that PIN's offices (all India)
    unverifiable_pins: int = 0  # PINs with 1-2 offices: never filtered, cannot be cross-checked
    out_of_region: int = 0
    wrong_prefix: int = 0  # valid coordinates in the region, but a PIN that belongs to another area (bad geocode)
    prefixes: tuple = ()
    kept: int = 0


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    p = math.pi / 180
    h = math.sin((lat2 - lat1) * p / 2) ** 2 + math.cos(lat1 * p) * math.cos(lat2 * p) * math.sin((lon2 - lon1) * p / 2) ** 2
    return 2 * 6_371_000 * math.asin(math.sqrt(h))


def drop_shared_points(offices: list[dict], min_pins: int = 3, decimals: int = 4):
    """Drop offices whose coordinates (rounded to `decimals`, ~11 m) are shared with offices of `min_pins`+ different PINs.

    Several unrelated offices at one exact point is a geocoder's fallback location (all the 'Bengaluru' offices
    landing on one spot in Basavanagudi), not where they are. Two PINs at one point can be real, so the default
    needs three. Returns (kept, dropped); dropped offices carry `shared_pins`.
    """
    cells: dict[tuple, list[dict]] = {}
    for o in offices:
        cells.setdefault((round(o["lat"], decimals), round(o["lon"], decimals)), []).append(o)
    kept, dropped = [], []
    for key in sorted(cells):
        group = cells[key]
        n = len({o["pin"] for o in group})
        if n >= min_pins:
            dropped.extend({**o, "shared_pins": n} for o in group)
        else:
            kept.extend(group)
    return kept, dropped


MIN_OFFICES_FOR_MEDIAN = 3


def find_outliers(offices: list[dict], max_m: float):
    """Offices located more than `max_m` from the median location of their PIN's offices.

    India Post has offices whose coordinates are wrong but whose PIN is valid (a Bengaluru office geocoded
    to another town), so a PIN's own offices vote on where it is: the median of their latitudes and of their
    longitudes. A median only means something with 3+ offices. PINs with one or two offices are never filtered
    (with two, the midpoint is equally far from both, so neither can be called the wrong one); they are returned
    as `unverifiable`.

    Returns (kept, outliers, unverifiable_pins). Each outlier is the office dict plus dist_m and n_offices.
    """
    by_pin: dict[str, list[dict]] = {}
    for o in offices:
        by_pin.setdefault(o["pin"], []).append(o)
    kept, outliers, unverifiable = [], [], set()
    for pin in sorted(by_pin):
        group = by_pin[pin]
        if len(group) < MIN_OFFICES_FOR_MEDIAN:
            unverifiable.add(pin)
            kept.extend(group)
            continue
        mlat = statistics.median(o["lat"] for o in group)
        mlon = statistics.median(o["lon"] for o in group)
        for o in group:
            d = haversine_m(o["lat"], o["lon"], mlat, mlon)
            if d > max_m:
                outliers.append({**o, "dist_m": d, "n_offices": len(group)})
            else:
                kept.append(o)
    return kept, outliers, unverifiable


def read_offices(csv_path: Path):
    """Every directory row with valid coordinates and a valid PIN, plus counts of what was dropped."""
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
            out.append({"pin": pin, "office": str(row.get("officename", "")).strip(), "lat": lat, "lon": lon})
    return out, c


def in_region(o: dict, bbox: tuple[float, float, float, float], margin_deg: float) -> bool:
    w, s, e, n = bbox
    return w - margin_deg <= o["lon"] <= e + margin_deg and s - margin_deg <= o["lat"] <= n + margin_deg


def load_offices(csv_path: Path, bbox: tuple[float, float, float, float], margin_deg: float, outlier_m: float = 4_000, shared_point_pins: int = 3):
    """Read the India Post directory and return (candidate offices, counts, info).

    Order: valid coordinates and PIN -> drop offices stacked on one point -> drop per-PIN location outliers (judged against all of the PIN's
    offices, wherever they are) -> keep those inside `bbox` ± margin. bbox = (min_lon, min_lat, max_lon, max_lat).
    Every drop is counted by reason. `info` has the outliers inside the region, the total outlier count, and
    the set of unverifiable (1-2 office) PINs.
    """
    valid, c = read_offices(csv_path)
    valid, shared = drop_shared_points(valid, shared_point_pins)
    c.shared_point = len(shared)
    kept, outliers, single = find_outliers(valid, outlier_m)
    c.outliers = len(outliers)
    c.unverifiable_pins = len(single)
    cand = [o for o in kept if in_region(o, bbox, margin_deg)]
    c.out_of_region = len(kept) - len(cand)
    c.kept = len(cand)
    info = {
        "outliers": sorted((o for o in outliers if in_region(o, bbox, margin_deg)), key=lambda o: (o["pin"], o["office"])),
        "shared": sorted((o for o in shared if in_region(o, bbox, margin_deg)), key=lambda o: (o["pin"], o["office"])),
        "unverifiable_pins": single,
    }
    return cand, c, info


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
            out.append({**p, "pin": best[1]["pin"], "pin_office": best[1]["office"], "pin_distance_m": best[0][0], "pin_source": "auto"})
        else:
            out.append({**p, "pin": "", "pin_office": "", "pin_distance_m": None, "pin_source": ""})
    return out


def modal_prefix(offices: list[dict], digits: int = 3) -> str:
    """The most common PIN prefix among the offices (ties: lowest). Empty string if there are none."""
    from collections import Counter

    counts = Counter(o["pin"][:digits] for o in offices)
    return sorted(counts, key=lambda k: (-counts[k], k))[0] if counts else ""


def filter_by_prefix(offices: list[dict], prefixes) -> tuple[list[dict], int]:
    """Keep offices whose PIN starts with one of `prefixes`.

    India Post coordinates are sometimes wrong (an office in another district geocoded into this city),
    so a nearby office with an unrelated PIN prefix must never supply a PIN. Returns (kept, dropped count).
    """
    prefixes = tuple(prefixes)
    kept = [o for o in offices if o["pin"].startswith(prefixes)]
    return kept, len(offices) - len(kept)

