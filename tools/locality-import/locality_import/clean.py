"""Inside-zone filtering and duplicate merging. Pure functions on metric (x, y) coordinates."""

from __future__ import annotations

import math
from collections import Counter
from dataclasses import dataclass

from shapely.geometry import Point


def filter_inside(places: list[dict], zone_union_m, buffer_m: float) -> list[dict]:
    """Keep places (dicts with metric x, y) inside the zone union grown by `buffer_m`."""
    area = zone_union_m.buffer(buffer_m) if buffer_m > 0 else zone_union_m
    return [p for p in places if area.covers(Point(p["x"], p["y"]))]


@dataclass
class MergeGroup:
    names: list[str]
    count: int
    max_pairwise_m: float


def _dist(a: dict, b: dict) -> float:
    return math.hypot(a["x"] - b["x"], a["y"] - b["y"])


def dedupe(places: list[dict], within_m: float) -> tuple[list[dict], list[MergeGroup]]:
    """Merge places with the same normalised name (case-insensitive) that are within `within_m`.

    Groups are connected components (single linkage), so a chain A-B-C merges even if A-C > within_m;
    that is exactly why `MergeGroup.max_pairwise_m` is reported. The merged place sits at the centroid
    of its members and takes the most common spelling (ties: alphabetically first). Output is sorted
    by (name, x, y) and does not depend on input order.
    """
    by_key: dict[str, list[dict]] = {}
    for p in places:
        by_key.setdefault(p["name"].casefold(), []).append(p)

    merged, groups = [], []
    for key in sorted(by_key):
        members = sorted(by_key[key], key=lambda p: (p["x"], p["y"], p.get("osm", "")))
        parent = list(range(len(members)))

        def find(i):
            while parent[i] != i:
                parent[i] = parent[parent[i]]
                i = parent[i]
            return i

        for i in range(len(members)):
            for j in range(i + 1, len(members)):
                if _dist(members[i], members[j]) <= within_m:
                    parent[find(i)] = find(j)
        comps: dict[int, list[dict]] = {}
        for i, m in enumerate(members):
            comps.setdefault(find(i), []).append(m)

        for comp in comps.values():
            counts = Counter(m["name"] for m in comp)
            name = sorted(counts, key=lambda n: (-counts[n], n))[0]
            rec = {
                "name": name,
                "x": sum(m["x"] for m in comp) / len(comp),
                "y": sum(m["y"] for m in comp) / len(comp),
                "place": sorted(m["place"] for m in comp)[0],
                "merged": len(comp),
            }
            merged.append(rec)
            if len(comp) > 1:
                mx = max(_dist(a, b) for i, a in enumerate(comp) for b in comp[i + 1:])
                groups.append(MergeGroup(sorted(counts), len(comp), mx))
    merged.sort(key=lambda p: (p["name"], p["x"], p["y"]))
    groups.sort(key=lambda g: (-g.max_pairwise_m, g.names))
    return merged, groups
