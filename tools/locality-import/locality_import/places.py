"""OpenStreetMap places via Overpass. `fetch_overpass` is the only function that touches the network."""

from __future__ import annotations

import json
import unicodedata
from pathlib import Path
from typing import Callable

PLACE_TYPES = ("suburb", "neighbourhood", "quarter", "village", "town", "hamlet")
OVERPASS_URL = "https://overpass-api.de/api/interpreter"
USER_AGENT = "gigwork-locality-import/1.0"


def build_query(bbox: tuple[float, float, float, float], place_types=PLACE_TYPES, timeout_s: int = 180) -> str:
    """bbox = (min_lon, min_lat, max_lon, max_lat). Overpass wants (south, west, north, east)."""
    w, s, e, n = bbox
    regex = "|".join(place_types)
    return (
        f"[out:json][timeout:{timeout_s}];"
        f'nwr["place"~"^({regex})$"]({s:.6f},{w:.6f},{n:.6f},{e:.6f});'
        "out center tags;"
    )


def fetch_overpass(query: str) -> dict:
    import requests  # imported here so the pure functions and tests never need it

    resp = requests.post(OVERPASS_URL, data={"data": query}, headers={"User-Agent": USER_AGENT}, timeout=240)
    resp.raise_for_status()
    return resp.json()


def load_raw(cache_path: Path, query: str, refresh: bool, fetcher: Callable[[str], dict] = fetch_overpass) -> tuple[dict, bool]:
    """Return (raw response, fetched_now). Uses the cache unless `refresh` or no cache exists."""
    if cache_path.exists() and not refresh:
        return json.loads(cache_path.read_text(encoding="utf-8")), False
    raw = fetcher(query)
    cache_path.parent.mkdir(parents=True, exist_ok=True)
    cache_path.write_text(json.dumps(raw, ensure_ascii=False, sort_keys=True), encoding="utf-8")
    return raw, True


def normalise_name(name: str | None) -> str:
    """Unicode NFC, trimmed, internal whitespace collapsed to single spaces."""
    if not name:
        return ""
    return " ".join(unicodedata.normalize("NFC", name).split())


def pick_name(tags: dict) -> str:
    """Prefer name:en over name; empty string when neither is usable."""
    for key in ("name:en", "name"):
        n = normalise_name(tags.get(key))
        if n:
            return n
    return ""


def parse_elements(raw: dict) -> list[dict]:
    """Flatten an Overpass response to dicts: osm, place, name, lat, lon (None name = unnamed).

    Ways and relations use their `center`. Elements without coordinates are skipped.
    Sorted by osm id so downstream results never depend on response order.
    """
    out = []
    for el in raw.get("elements", []):
        lat = el.get("lat", (el.get("center") or {}).get("lat"))
        lon = el.get("lon", (el.get("center") or {}).get("lon"))
        if lat is None or lon is None:
            continue
        tags = el.get("tags", {})
        out.append(
            {
                "osm": f"{el.get('type', '?')}/{el.get('id', '?')}",
                "place": tags.get("place", ""),
                "name": pick_name(tags),
                "lat": float(lat),
                "lon": float(lon),
            }
        )
    out.sort(key=lambda r: r["osm"])
    return out
