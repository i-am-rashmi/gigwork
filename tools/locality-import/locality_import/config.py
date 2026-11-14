"""Per-city configuration, kept in config/<city>/ so a new city needs data, not code."""

from __future__ import annotations

import json
from pathlib import Path


def load_settings(config_dir: Path) -> dict:
    """config/<city>/settings.json, e.g. {"pin_prefixes": ["560", "562"]}. Missing file means defaults."""
    f = config_dir / "settings.json"
    if not f.exists():
        return {}
    data = json.loads(f.read_text(encoding="utf-8"))
    prefixes = data.get("pin_prefixes", [])
    if not isinstance(prefixes, list) or not all(isinstance(x, str) and x.isdigit() for x in prefixes):
        raise ValueError(f"{f}: pin_prefixes must be a list of digit strings")
    return data
