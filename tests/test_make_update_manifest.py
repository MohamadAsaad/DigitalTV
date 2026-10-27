from __future__ import annotations

import hashlib
import importlib.util
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parents[1] / "tools" / "make_update_manifest.py"
SPEC = importlib.util.spec_from_file_location("make_update_manifest", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class UpdateManifestTest(unittest.TestCase):
    def test_manifest_has_hash_package_and_version(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "launcher.apk"
            apk.write_bytes(b"test apk bytes")
            result = MODULE.build_manifest(apk, "3.1.0", 10, "cleanyourtv-launcher.apk")
        self.assertEqual("com.gathin.tvhome", result["packageName"])
        self.assertEqual(10, result["versionCode"])
        self.assertEqual(hashlib.sha256(b"test apk bytes").hexdigest(), result["sha256"])

    def test_manifest_rejects_path_traversal_asset_names(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "launcher.apk"
            apk.write_bytes(b"test")
            with self.assertRaises(ValueError):
                MODULE.build_manifest(apk, "3.1.0", 10, "../launcher.apk")

    def test_manifest_requires_positive_version_code(self):
        with tempfile.TemporaryDirectory() as temp:
            apk = Path(temp) / "launcher.apk"
            apk.write_bytes(b"test")
            with self.assertRaises(ValueError):
                MODULE.build_manifest(apk, "3.1.0", 0, "launcher.apk")


if __name__ == "__main__":
    unittest.main()
