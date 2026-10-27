#!/usr/bin/env python3
"""Create the small metadata file consumed by the optional GitHub updater add-on."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


PACKAGE_NAME = "com.gathin.tvhome"


def build_manifest(apk: Path, version_name: str, version_code: int, asset_name: str) -> dict[str, object]:
    if not apk.is_file():
        raise FileNotFoundError(apk)
    if version_code < 1:
        raise ValueError("version code must be positive")
    if not asset_name.endswith(".apk") or Path(asset_name).name != asset_name:
        raise ValueError("asset name must be a plain APK filename")
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    return {
        "schema": 1,
        "packageName": PACKAGE_NAME,
        "versionName": version_name,
        "versionCode": version_code,
        "apk": asset_name,
        "sha256": digest,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--version-code", required=True, type=int)
    parser.add_argument("--asset-name", default="cleanyourtv-launcher.apk")
    parser.add_argument("--output", type=Path, default=Path("update-manifest.json"))
    args = parser.parse_args()
    manifest = build_manifest(args.apk, args.version_name, args.version_code, args.asset_name)
    args.output.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
