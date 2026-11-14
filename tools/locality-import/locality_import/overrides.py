"""Manual per-city fixes, keyed by OSM id (names are not unique): config/<city>/overrides.csv."""

from __future__ import annotations

import csv
from pathlib import Path

from .pins import PIN_RE
from .places import normalise_name


class OverrideError(Exception):
    pass


def load_overrides(path: Path) -> list[dict]:
    """CSV header: osm_id,name,pin,reason. `name` and `pin` are optional (empty keeps the generated value)."""
    if not path.exists():
        return []
    out, seen = [], set()
    with open(path, newline="", encoding="utf-8") as f:
        for i, row in enumerate(csv.DictReader(f), start=2):
            osm = (row.get("osm_id") or "").strip()
            name = normalise_name(row.get("name"))
            pin = (row.get("pin") or "").strip()
            if not (osm or name or pin):
                continue
            where = f"{path.name}:{i}"
            if not osm:
                raise OverrideError(f"{where}: osm_id is required (e.g. node/123456)")
            if osm in seen:
                raise OverrideError(f"{where}: osm_id {osm} appears twice")
            if not name and not pin:
                raise OverrideError(f"{where}: {osm} sets neither a name nor a pin")
            if pin and not PIN_RE.match(pin):
                raise OverrideError(f"{where}: pin must be 6 digits, got {pin!r}")
            seen.add(osm)
            out.append({"osm_id": osm, "name": name, "pin": pin, "reason": (row.get("reason") or "").strip()})
    return out


def apply_overrides(rows: list[dict], overrides: list[dict]):
    """Apply each override to the locality whose `osm_ids` contains its osm_id.

    An osm_id that is in no locality (typo, outside the zones, unnamed...) raises OverrideError, so a
    stale override never goes unnoticed. Returns (rows, applied): applied = [(osm_id, old name, new name,
    old pin, new pin, reason)].
    """
    rows = [dict(r) for r in rows]
    index = {i: r for r in rows for i in r.get("osm_ids", [])}
    missing = [o["osm_id"] for o in overrides if o["osm_id"] not in index]
    if missing:
        raise OverrideError("overrides.csv: osm_id not found in any locality: " + ", ".join(missing))
    hit: dict[int, str] = {}
    applied = []
    for o in overrides:
        r = index[o["osm_id"]]
        if id(r) in hit:
            raise OverrideError(f"overrides.csv: {o['osm_id']} and {hit[id(r)]} are the same locality ('{r['name']}')")
        hit[id(r)] = o["osm_id"]
        old_name, old_pin = r["name"], r["pin"]
        if o["name"]:
            r["name"] = o["name"]
        if o["pin"]:
            r.update(pin=o["pin"], pin_office="override", pin_distance_m=None, pin_source="override")
        applied.append((o["osm_id"], old_name, r["name"], old_pin, r["pin"], o["reason"]))
    return rows, applied
