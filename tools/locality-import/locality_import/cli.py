"""python -m locality_import --city bengaluru --zones <kml|geojson> --pins <csv> [--refresh-osm]"""

from __future__ import annotations

import argparse
import math
import sys
from collections import Counter
from pathlib import Path

import geopandas as gpd
from shapely.geometry import box

from . import clean, config, output, pins, places, review, zones
from .geo import lonlat_to_xy, xy_to_lonlat

TOOL_DIR = Path(__file__).resolve().parent.parent
REPO_ROOT = TOOL_DIR.parent.parent


def parse_args(argv=None):
    p = argparse.ArgumentParser(prog="locality_import", description=__doc__)
    p.add_argument("--city", required=True, help="city slug, used for output folders (e.g. bengaluru)")
    p.add_argument("--zones", required=True, type=Path, help="zones file (KML or GeoJSON)")
    p.add_argument("--pins", required=True, type=Path, help="India Post All India Pincode Directory CSV")
    p.add_argument("--refresh-osm", action="store_true", help="re-fetch from Overpass instead of using the cache")
    p.add_argument("--zone-name-field", default=zones.DEFAULT_NAME_FIELD)
    p.add_argument("--expected-zones", default=",".join(zones.DEFAULT_EXPECTED),
                   help="comma-separated zone names the file must contain exactly; empty string disables the check")
    p.add_argument("--buffer-m", type=float, default=500, help="keep places this far outside the zones")
    p.add_argument("--max-pin-m", type=float, default=3000, help="max distance to a post office for a PIN")
    p.add_argument("--pin-prefixes", default="",
                   help="comma-separated PIN prefixes belonging to the city; overrides config/<city>/settings.json. "
                        "Post offices with other prefixes are ignored (India Post coordinates are sometimes wrong). "
                        "Default if neither is set: the most common 3-digit prefix among offices in the region")
    p.add_argument("--parent-m", type=float, default=2000, help="radius to find a parent area for generic names like '4th Block'")
    p.add_argument("--config-dir", type=Path, default=None, help="default: tools/locality-import/config/<city>")
    p.add_argument("--dedupe-m", type=float, default=1000, help="merge same-named places within this distance")
    p.add_argument("--simplify-m", type=float, default=10, help="zone simplification tolerance in metres")
    p.add_argument("--seed-dir", type=Path, default=None, help="default: <repo>/infra/seed/<city>")
    p.add_argument("--review-dir", type=Path, default=None, help="default: tools/locality-import/output/<city>")
    p.add_argument("--cache-dir", type=Path, default=None, help="default: tools/locality-import/input/cache")
    return p.parse_args(argv)


def run(args, fetcher=places.fetch_overpass) -> dict:
    seed_dir = args.seed_dir or REPO_ROOT / "infra" / "seed" / args.city
    review_dir = args.review_dir or TOOL_DIR / "output" / args.city
    cache_path = (args.cache_dir or TOOL_DIR / "input" / "cache") / f"{args.city}-overpass.json"

    # 1. Zones: measure, simplify in metres, snap to the output grid, measure what will be written.
    expected = [z.strip() for z in args.expected_zones.split(",") if z.strip()] or None
    src = zones.load_zones(args.zones, args.zone_name_field, expected)
    crs = zones.metric_crs(src)
    src_m = src.to_crs(crs)
    before = zones.measure(src_m)
    simp_m = zones.simplify_topology(src_m, args.simplify_m)
    out_zones = zones.to_output_grid(simp_m)
    after = zones.measure(out_zones.to_crs(crs))
    zone_union_m = out_zones.to_crs(crs).geometry.union_all()

    # 2. Places from OSM, bbox = zones bbox grown by the buffer (so buffer-zone places can be fetched).
    pad = args.buffer_m / 111_000 * 1.5
    w, s, e, n = out_zones.total_bounds
    bbox = (w - pad, s - pad, e + pad, n + pad)
    query = places.build_query(bbox)
    raw, fetched_now = places.load_raw(cache_path, query, args.refresh_osm, fetcher)
    parsed = places.parse_elements(raw)
    for p in parsed:
        p["x"], p["y"] = lonlat_to_xy(p["lon"], p["lat"], crs)
    by_type_before = Counter(p["place"] for p in parsed)
    inside = clean.filter_inside(parsed, zone_union_m, args.buffer_m)
    by_type_after = Counter(p["place"] for p in inside)

    # 3. Clean: drop unnamed, give generic names a parent (or drop them), merge duplicates.
    named_all = [p for p in inside if p["name"]]
    named, prefixed, generic_dropped = clean.prefix_generic(named_all, args.parent_m)
    merged, groups = clean.dedupe(named, args.dedupe_m)

    # 4. PINs from valid post offices near the region.
    margin_deg = (args.buffer_m + args.max_pin_m) / 111_000 * 1.5
    offices, office_counts = pins.load_offices(args.pins, (w, s, e, n), margin_deg)
    cfg_dir = args.config_dir or TOOL_DIR / "config" / args.city
    settings = config.load_settings(cfg_dir)
    cli_prefixes = tuple(x.strip() for x in args.pin_prefixes.split(",") if x.strip())
    if cli_prefixes:
        prefixes, prefix_source = cli_prefixes, "--pin-prefixes"
    elif settings.get("pin_prefixes"):
        prefixes, prefix_source = tuple(settings["pin_prefixes"]), f"config/{args.city}/settings.json"
    else:
        prefixes, prefix_source = (pins.modal_prefix(offices),), "most common prefix in the region (no config)"
    offices, office_counts.wrong_prefix = pins.filter_by_prefix(offices, prefixes)
    office_counts.prefixes, office_counts.kept = prefixes, len(offices)
    for o in offices:
        o["x"], o["y"] = lonlat_to_xy(o["lon"], o["lat"], crs)
    assigned = pins.assign_pins(merged, offices, args.max_pin_m)
    rows = []
    for a in assigned:
        lon, lat = xy_to_lonlat(a["x"], a["y"], crs)
        rows.append({**a, "lat": round(lat, 6), "lng": round(lon, 6)})
    overrides = pins.load_overrides(cfg_dir / "pin-overrides.csv")
    rows, ov_applied, ov_unmatched = pins.apply_overrides(rows, overrides)

    # 5. Committed outputs, then review files.
    output.write_zones_geojson(out_zones, seed_dir / "zones.geojson")
    output.write_localities_csv(rows, seed_dir / "localities.csv")
    params = {
        "buffer (m)": args.buffer_m, "max PIN distance (m)": args.max_pin_m, "dedupe distance (m)": args.dedupe_m, "parent radius (m)": args.parent_m,
        "simplify tolerance (m)": args.simplify_m, "metric CRS": f"EPSG:{crs.to_epsg()}",
        "zones file": args.zones.name, "pin file": args.pins.name,
    }
    ctx = {
        "city": args.city, "params": params, "zone_before": before, "zone_after": after,
        "fetched_now": fetched_now, "cache_path": str(cache_path.relative_to(TOOL_DIR)) if cache_path.is_relative_to(TOOL_DIR) else str(cache_path),
        "n_parsed": len(parsed), "by_type_before": dict(by_type_before), "by_type_after": dict(by_type_after),
        "n_inside": len(inside), "n_outside": len(parsed) - len(inside), "n_unnamed": len(inside) - len(named_all),
        "n_named": len(named_all), "n_after_generic": len(named), "n_final": len(merged), "groups": groups,
        "prefix_source": prefix_source, "prefixed": prefixed, "generic_dropped": generic_dropped,
        "ov_applied": ov_applied, "ov_unmatched": ov_unmatched,
        "office_counts": office_counts, "localities": rows,
    }
    review_dir.mkdir(parents=True, exist_ok=True)
    (review_dir / "report.md").write_text(review.build_report(ctx), encoding="utf-8")
    review.write_map(out_zones, rows, review_dir / "map.html")
    ctx["seed_dir"], ctx["review_dir"] = seed_dir, review_dir
    return ctx


def main(argv=None) -> int:
    args = parse_args(argv)
    try:
        ctx = run(args)
    except zones.ZoneError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    print(f"{ctx['n_final']} localities -> {ctx['seed_dir']}")
    print(f"review: {ctx['review_dir'] / 'report.md'}")
    return 0
