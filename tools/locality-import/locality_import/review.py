"""Review files (not committed): report.md and map.html."""

from __future__ import annotations

from collections import Counter
from pathlib import Path

FAR_PIN_M = 2000


def _fmt_m2(v: float) -> str:
    return f"{v:,.2f}"


def _zone_section(title: str, stats) -> list[str]:
    lines = [f"### {title}", "", "| Zone | Area (km²) | Vertices | Interior rings (m²) |", "|---|---:|---:|---|"]
    for n in sorted(stats.areas_m2):
        rings = ", ".join(f"{a:.4f}" for a in stats.interior_rings.get(n, [])) or "none"
        lines.append(f"| {n} | {stats.areas_m2[n] / 1e6:,.3f} | {stats.vertices[n]:,} | {rings} |")
    lines += ["", "Interior rings are holes inside one zone; they are only a gap if the union of all zones still has the hole (see below).", "",
              f"- Pairwise overlaps: {'none' if not stats.overlaps else ''}"]
    for a, b, area in sorted(stats.overlaps, key=lambda t: -t[2]):
        lines.append(f"  - {a} / {b}: {_fmt_m2(area)} m²")
    lines.append(f"- Total overlap: {_fmt_m2(stats.overlap_total_m2)} m²")
    if stats.gaps:
        lines.append(f"- Gaps (holes of the union): {len(stats.gaps)}")
        for g in stats.gaps:
            lines.append(f"  - {_fmt_m2(g)} m²")
    else:
        lines.append("- Gaps (holes of the union): none")
    lines.append("")
    return lines


def build_report(ctx: dict) -> str:
    """ctx carries every number; see cli.run. Pure string building so it is easy to eyeball."""
    L = [f"# Locality import report: {ctx['city']}", ""]
    L += ["## Parameters", ""]
    L += [f"- {k}: {v}" for k, v in ctx["params"].items()]
    L += ["", "## Zones", ""]
    L += _zone_section("Source (before simplification)", ctx["zone_before"])
    L += _zone_section(f"Simplified, topology-preserving, {ctx['params']['simplify tolerance (m)']} m (as written)", ctx["zone_after"])

    L += ["## Places (OpenStreetMap)", ""]
    L += [f"- Overpass response: {'fetched now' if ctx['fetched_now'] else 'reused from cache'} (`{ctx['cache_path']}`)"]
    L += [f"- Elements with coordinates: {ctx['n_parsed']:,}"]
    types = sorted(set(ctx["by_type_before"]) | set(ctx["by_type_after"]))
    L += ["", "| place= | Fetched | Inside zones + buffer |", "|---|---:|---:|"]
    for t in types:
        L.append(f"| {t} | {ctx['by_type_before'].get(t, 0):,} | {ctx['by_type_after'].get(t, 0):,} |")
    L.append(f"| **total** | {sum(ctx['by_type_before'].values()):,} | {sum(ctx['by_type_after'].values()):,} |")
    L += ["", "## Cleaning", ""]
    L += [f"- Inside zones + buffer: {ctx['n_inside']:,} (outside dropped: {ctx['n_outside']:,})"]
    L += [f"- Unnamed dropped: {ctx['n_unnamed']:,}"]
    L += [f"- Named, before merge: {ctx['n_named']:,}"]
    L += [f"- Merge groups: {len(ctx['groups']):,}; places removed by merging: {ctx['n_named'] - ctx['n_final']:,}"]
    L += [f"- **Localities written: {ctx['n_final']:,}**", ""]

    L += ["### Duplicate merge groups", ""]
    if ctx["groups"]:
        L += ["| Names | Members | Max pairwise distance (m) |", "|---|---:|---:|"]
        for g in ctx["groups"]:
            L.append(f"| {' / '.join(g.names)} | {g.count} | {g.max_pairwise_m:,.0f} |")
    else:
        L.append("None.")
    L.append("")

    c = ctx["office_counts"]
    L += ["## PIN assignment (India Post)", ""]
    L += [f"- Directory rows: {c.total:,}",
          f"- Dropped, missing/zero/invalid coordinates: {c.bad_coords:,}",
          f"- Dropped, invalid PIN: {c.bad_pin:,}",
          f"- Dropped, outside the region (zones bbox + margin): {c.out_of_region:,}",
          f"- Dropped, PIN prefix not in {', '.join(c.prefixes)} (offices geocoded into the wrong city): {c.wrong_prefix:,}",
          f"- Offices used as candidates: {c.kept:,}"]
    rows = ctx["localities"]
    with_pin = [r for r in rows if r["pin"]]
    L += ["", f"- Localities with a PIN: {len(with_pin):,} / {len(rows):,}",
          f"- Localities **without** a PIN (no valid office within {ctx['params']['max PIN distance (m)']} m): {len(rows) - len(with_pin):,}", ""]
    L += ["### Localities without a PIN", ""]
    none = [r for r in rows if not r["pin"]]
    L += ([f"- {r['name']} ({r['lat']:.6f}, {r['lng']:.6f})" for r in none] or ["None."])
    L += ["", f"### PIN taken from more than {FAR_PIN_M // 1000} km away", ""]
    far = sorted((r for r in with_pin if r["pin_distance_m"] > FAR_PIN_M), key=lambda r: -r["pin_distance_m"])
    L += ([f"- {r['name']}: {r['pin']} ({r['pin_office']}), {r['pin_distance_m'] / 1000:.2f} km" for r in far] or ["None."])
    dist = Counter()
    for r in with_pin:
        dist["≤ 1 km" if r["pin_distance_m"] <= 1000 else "1–2 km" if r["pin_distance_m"] <= 2000 else "> 2 km"] += 1
    L += ["", "Distance to the PIN's post office: " + ", ".join(f"{k}: {dist[k]:,}" for k in ("≤ 1 km", "1–2 km", "> 2 km")), ""]
    return "\n".join(L)


def write_map(zones_4326, rows: list[dict], path: Path) -> None:
    import folium

    b = zones_4326.total_bounds
    m = folium.Map(location=[(b[1] + b[3]) / 2, (b[0] + b[2]) / 2], zoom_start=11, tiles="OpenStreetMap")
    folium.GeoJson(
        zones_4326[["name", "geometry"]],
        style_function=lambda f: {"color": "#3a5ba0", "weight": 2, "fillOpacity": 0.05},
        tooltip=folium.GeoJsonTooltip(fields=["name"]),
        name="zones",
    ).add_to(m)
    for r in rows:
        has = bool(r["pin"])
        dist = f"{r['pin_distance_m']:,.0f} m" if has else "n/a"
        popup = f"<b>{r['name']}</b><br>PIN: {r['pin'] or '(none)'}<br>Source distance: {dist}"
        folium.CircleMarker(
            [r["lat"], r["lng"]], radius=5, color="#1b7f3b" if has else "#c0392b",
            fill=True, fill_opacity=0.85, popup=folium.Popup(popup, max_width=260),
        ).add_to(m)
    path.parent.mkdir(parents=True, exist_ok=True)
    m.save(str(path))
