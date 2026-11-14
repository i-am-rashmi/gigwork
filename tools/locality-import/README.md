# locality-import

Offline tool that generates the **reference seed data for one city**: zone boundaries and a list of localities (name, PIN, centre point) for "type your area or PIN" search (data model §4, Reference Data). A locality is a search shortcut only; real locations are coordinates.

It writes two committed files per city to `infra/seed/<city>/` (`zones.geojson`, `localities.csv`) plus review files in `tools/locality-import/output/<city>/` (not committed). A later change loads the committed files into the reference schema. The output is city-agnostic: **adding a city means running this tool again, with no code changes.**

## Setup

Python **3.12+** (tested with 3.14.5).

```bash
cd tools/locality-import
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
pytest                     # no network needed
```

## Inputs (not committed; put them in `input/`)

| Input | What |
|---|---|
| Zones file | KML or GeoJSON, one polygon per zone, with a name property (default `NewCorp`). Any CRS; reprojected to EPSG:4326. |
| India Post CSV | "All India Pincode Directory" (data.gov.in): `officename, pincode, latitude, longitude, ...`. Rows with `NA`, zero or out-of-region coordinates are discarded. |

OpenStreetMap places are fetched from the Overpass API on the first run and cached in `input/cache/<city>-overpass.json`. Later runs reuse the cache; pass `--refresh-osm` to fetch again.

## Run

```bash
cd tools/locality-import && source .venv/bin/activate
python -m locality_import --city bengaluru \
  --zones input/bangalore_zones.kml \
  --pins input/all_india_pincode_directory.csv \
  [--refresh-osm]
```

Options (defaults in brackets): `--buffer-m` [500] keep places this far outside the zones; `--max-pin-m` [3000] farthest post office a PIN may come from; `--dedupe-m` [1000] merge same-named places this close; `--parent-m` [2000] radius for finding a parent area for generic names; `--pin-prefixes` overrides the city's configured PIN prefixes; `--simplify-m` [10] zone simplification tolerance; `--zone-name-field` [`NewCorp`]; `--expected-zones` [`Central,East,North,South,West`] the exact zone names the file must contain (the run stops if they differ; an empty value disables the check).

## What it does

1. **Zones**: one polygon per zone, clean name, EPSG:4326, invalid geometry repaired. Simplified in metres (the city's UTM zone) with a topology-preserving method, so shared edges stay shared and simplification creates no gaps or overlaps. Overlap (m², pairwise) and gaps (holes of the union of all zones, m² each) are reported before and after.
2. **Places**: OSM `place=suburb|neighbourhood|quarter|village|town|hamlet` within the zones' bounding box, kept only if inside the union of zones plus the buffer.
3. **Clean**: `name:en` over `name`, whitespace normalised, unnamed dropped. Generic names that mean nothing alone (`4th Block`, `Sector 2`, `Further Extension`, `3rd Cross`...) get a parent as prefix (`HSR Layout Sector 2`): a suburb within the parent radius is preferred over a nearer neighbourhood. Generic names of the same family (sector, phase, block...) within the radius of each other share the majority parent of their individual matches. With no parent in range the row is dropped. This is a heuristic: review `prefixed.csv` (every prefixed row). Then same name within the dedupe distance merged to the group's centroid. Every merge group is listed in the report (a chain A-B-C can merge even if A and C are farther apart than the threshold; the report shows the largest pairwise distance).
4. **PIN**: nearest valid post office within the max distance, measured in metres in the projected CRS, among offices whose PIN starts with one of the city's prefixes (India Post has offices in other districts whose coordinates fall inside the city). No match: empty PIN, listed in the report. Then manual overrides are applied and listed.
5. **Output**: deterministic (same inputs give byte-identical files).

## Per-city configuration (committed): `config/<city>/`

| File | Content |
|---|---|
| `settings.json` | `{"pin_prefixes": ["560", "562"]}`: PIN prefixes that belong to the city. Without it the most common 3-digit prefix in the region is used (and the report says so). |
| `overrides.csv` | `osm_id,name,pin,reason`: manual fixes keyed by OSM id (names are not unique). `name` and `pin` are optional; empty keeps the generated value. The id may be any OSM element merged into the locality (`node/123`; the report and map popups show them). An id that matches no locality fails the run. Applied after automatic assignment and listed in the report. |

## Output

Committed, in `infra/seed/<city>/`:

- `zones.geojson`: simplified zones, properties: `name` only.
- `localities.csv`: `source_id,name,pin,lat,lng`; `source_id` is the stable key (smallest OSM id among the merged places, e.g. `node/123`; unique, the run fails otherwise); lat/lng with 6 decimals, sorted by name. `pin` may be empty.

Review only, in `tools/locality-import/output/<city>/`: `report.md`, `map.html` and `prefixed.csv`.

## Reviewing the output

1. Read `report.md`: zone overlap/gap figures (expect none, or tiny), counts by place type before and after the inside-zone filter, merge groups (look for chains and distant members), localities without a PIN, and PINs taken from more than 2 km away.
2. Open `map.html`: zones, localities coloured by "has PIN" (green/red); click a point for name, PIN and distance to the source post office. Look for points outside zones, odd clusters and wrongly named places.
3. Run twice and `git diff`: unchanged inputs must produce no diff.
4. Fix data problems at the source or via the options; do not hand-edit generated files.

## Adding a new city

1. Put the city's zone boundaries (KML/GeoJSON) in `input/`. The India Post CSV is national, so it is reused as is.
2. Create `config/pune/settings.json` with the city's PIN prefixes (and optionally an empty `overrides.csv` with the header `osm_id,name,pin,reason`).
3. Run with a new slug, and pass the zone names if they differ from the default:
   ```bash
   python -m locality_import --city pune --zones input/pune_zones.geojson \
     --pins input/all_india_pincode_directory.csv \
     --zone-name-field name --expected-zones "Zone A,Zone B,Zone C"
   ```
4. Review as above, then commit `config/pune/` and `infra/seed/pune/`.

## Data sources and attribution

| Data | Source | Licence |
|---|---|---|
| Places | © OpenStreetMap contributors, via the Overpass API | [ODbL 1.0](https://opendatacommons.org/licenses/odbl/1-0/) |
| Zone boundaries (Bengaluru) | Greater Bengaluru Authority Corporations Delimitation 2025 — OpenCity (data.opencity.in), contributed by Vaidya R; original source: Government of Karnataka. | ODbL 1.0 (the dataset page states no licence, so OpenCity's site-wide terms apply: <https://opencity.in/faqs/>) |
| PIN codes and post-office coordinates | India Post, "All India Pincode Directory", via data.gov.in | Government Open Data License - India (GODL) |

The generated files in `infra/seed/` are derived from ODbL sources (OpenStreetMap and OpenCity), so they are themselves made available under ODbL 1.0. See `infra/seed/README.md`.
