import json

import pytest

from locality_import import config


def test_settings_missing_gives_defaults(tmp_path):
    assert config.load_settings(tmp_path) == {}


def test_settings_prefixes_loaded_and_validated(tmp_path):
    (tmp_path / "settings.json").write_text(json.dumps({"pin_prefixes": ["560", "562"]}))
    assert config.load_settings(tmp_path)["pin_prefixes"] == ["560", "562"]
    (tmp_path / "settings.json").write_text(json.dumps({"pin_prefixes": "560"}))
    with pytest.raises(ValueError):
        config.load_settings(tmp_path)
