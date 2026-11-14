"""Inside-zone filtering and duplicate merging. Pure functions on metric (x, y) coordinates."""

from __future__ import annotations

import math
import re
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
                "osm_ids": sorted(m["osm"] for m in comp if m.get("osm")),
            }
            merged.append(rec)
            if len(comp) > 1:
                mx = max(_dist(a, b) for i, a in enumerate(comp) for b in comp[i + 1:])
                groups.append(MergeGroup(sorted(counts), len(comp), mx))
    merged.sort(key=lambda p: (p["name"], p["x"], p["y"]))
    groups.sort(key=lambda g: (-g.max_pairwise_m, g.names))
    return merged, groups


# Names that only make sense inside a parent area: "4th Block", "Sector 2", "Further Extension", "3rd Cross".
_ORDINAL = r"(?:\d+(?:st|nd|rd|th)?|[ivx]+)"
_UNIT = r"(?:block|stage|phase|sector|cross|main)"
GENERIC_RE = re.compile(
    rf"^(?:{_ORDINAL}\s+{_UNIT}(?:\s+\d+[a-z]?)?|{_UNIT}\s+{_ORDINAL}[a-z]?|(?:further|new|old|north|south|east|west)?\s*extension)$",
    re.IGNORECASE,
)
_HAS_UNIT_RE = re.compile(rf"\b{_UNIT}\b|\bextension\b", re.IGNORECASE)
PARENT_TYPES = ("suburb", "neighbourhood")  # in order of preference: a suburb beats a nearer neighbourhood


def is_generic(name: str) -> bool:
    return bool(GENERIC_RE.match(name.strip()))


def family(name: str) -> str:
    """The kind of subdivision a generic name is: 'sector', 'phase', 'block', 'stage', 'cross', 'main', 'extension'."""
    low = name.casefold()
    for kind in ("sector", "phase", "block", "stage", "cross", "main", "extension"):
        if re.search(rf"\b{kind}\b", low):
            return kind
    return low


@dataclass
class PrefixRow:
    osm: str
    original: str
    new: str
    parent: str
    distance_m: float
    own_parent: str  # the parent this place would have had on its own (before the family vote)
    family_size: int


def _parent_candidates(p: dict, parents: list[dict], radius_m: float, parent_types) -> list[tuple]:
    """Eligible parents within the radius, best first: preferred type, then distance, name, osm id."""
    rank = {t: i for i, t in enumerate(parent_types)}
    return sorted(
        (rank[q["place"]], _dist(p, q), q["name"], q.get("osm", "")) for q in parents if _dist(p, q) <= radius_m
    )


def prefix_generic(places: list[dict], radius_m: float, parent_types=PARENT_TYPES):
    """Give generic names a parent: "4th Block" -> "Jayanagar 4th Block".

    Parent: a place whose type is in `parent_types`, whose own name is not generic or a block/stage/sector.
    Preference is by type first (a suburb within the radius beats a nearer neighbourhood), then distance.
    Generic names of the same family ("Sector N", "Nth Phase", ...) that lie within `radius_m` of each other
    (chained) share one parent: the majority of their individual matches (ties: smallest total distance, then name),
    provided it is within the radius of that place; otherwise the place keeps its own match.
    Generic places with no parent in range are dropped.

    Returns (places, rows, dropped): rows are PrefixRow, dropped = [(osm, original name)].
    """
    parents = [p for p in places if p["place"] in parent_types and p["name"] and not _HAS_UNIT_RE.search(p["name"])]
    generic = [p for p in places if is_generic(p["name"])]
    own = {id(p): _parent_candidates(p, parents, radius_m, parent_types) for p in generic}

    # connected components per family
    comp_of: dict[int, int] = {}
    by_family: dict[str, list[dict]] = {}
    for p in generic:
        by_family.setdefault(family(p["name"]), []).append(p)
    comps: list[list[dict]] = []
    for fam in sorted(by_family):
        members = by_family[fam]
        parent_ix = list(range(len(members)))

        def find(i):
            while parent_ix[i] != i:
                parent_ix[i] = parent_ix[parent_ix[i]]
                i = parent_ix[i]
            return i

        for i in range(len(members)):
            for j in range(i + 1, len(members)):
                if _dist(members[i], members[j]) <= radius_m:
                    parent_ix[find(i)] = find(j)
        groups: dict[int, list[dict]] = {}
        for i, m in enumerate(members):
            groups.setdefault(find(i), []).append(m)
        for g in groups.values():
            for m in g:
                comp_of[id(m)] = len(comps)
            comps.append(g)

    winner: dict[int, str] = {}
    for ci, g in enumerate(comps):
        votes: dict[str, list[float]] = {}
        for m in g:
            if own[id(m)]:
                _, d, name, _ = own[id(m)][0]
                votes.setdefault(name, []).append(d)
        if votes:
            winner[ci] = sorted(votes, key=lambda n: (-len(votes[n]), sum(votes[n]), n))[0]

    out, rows, dropped = [], [], []
    for p in places:
        if not is_generic(p["name"]):
            out.append(p)
            continue
        cands = own[id(p)]
        if not cands:
            dropped.append((p.get("osm", ""), p["name"]))
            continue
        _, d_own, own_name, _ = cands[0]
        chosen, dist = own_name, d_own
        w = winner.get(comp_of[id(p)])
        if w is not None:
            near = [c for c in cands if c[2] == w]
            if near:  # nearest parent called `w` that is in range of this place
                nearest = min(near, key=lambda c: (c[1], c[3]))
                chosen, dist = w, nearest[1]
        new = f"{chosen} {p['name']}"
        rows.append(PrefixRow(p.get("osm", ""), p["name"], new, chosen, dist, own_name, len(comps[comp_of[id(p)]])))
        out.append({**p, "name": new})
    rows.sort(key=lambda r: (r.new, r.osm))
    return out, rows, sorted(dropped)
