import os
import tempfile
import unittest
from pathlib import Path

from app.foundation import ConfigCenter, deep_merge_config
from app.services import sanitize_config


class ConfigSanitizationTests(unittest.TestCase):
    def test_deep_merge_config_merges_nested_dict(self):
        base = {"a": 1, "b": {"x": 1, "y": 2}, "c": [1, 2]}
        override = {"b": {"y": 3, "z": 4}, "c": [9]}
        merged = deep_merge_config(base, override)
        self.assertEqual(merged["a"], 1)
        self.assertEqual(merged["b"], {"x": 1, "y": 3, "z": 4})
        self.assertEqual(merged["c"], [9])

    def test_sanitize_config_removes_sensitive_keys(self):
        raw = {
            "token": "abc",
            "nested": {
                "apiKey": "k",
                "ok": 1,
            },
            "list": [{"password": "p", "x": 1}],
        }
        sanitized = sanitize_config(raw)
        self.assertNotIn("token", sanitized)
        self.assertIn("nested", sanitized)
        self.assertNotIn("apiKey", sanitized["nested"])
        self.assertEqual(sanitized["nested"]["ok"], 1)
        self.assertEqual(sanitized["list"], [{"x": 1}])

    def test_config_center_applies_override_with_deep_merge(self):
        old = os.environ.get("OPEN_GEOBOT_CONFIG_PATH")
        try:
            with tempfile.TemporaryDirectory(prefix="opengeobot-test-") as temp_dir:
                override_path = Path(temp_dir) / "override.yaml"
                override_path.write_text(
                    "\n".join(
                        [
                            "global:",
                            "  monitoring:",
                            "    citationDropAlertThreshold: 0.99",
                        ]
                    )
                    + "\n",
                    encoding="utf-8",
                )
                os.environ["OPEN_GEOBOT_CONFIG_PATH"] = str(override_path)
                center = ConfigCenter()
                self.assertEqual(center.get("global.monitoring.citationDropAlertThreshold"), 0.99)
                self.assertEqual(center.get("global.monitoring.errorRateAlertThreshold"), 0.05)
        finally:
            if old is None:
                os.environ.pop("OPEN_GEOBOT_CONFIG_PATH", None)
            else:
                os.environ["OPEN_GEOBOT_CONFIG_PATH"] = old


if __name__ == "__main__":
    unittest.main()

