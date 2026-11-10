#!/usr/bin/env python3
"""Build and verify the Purple Charm remote icon catalog.

Run from the repository root. Requires only Python's standard library.
An unknown PNG is rejected until its Android package name is registered.
"""
import hashlib
import json
import os
import re
import struct
from pathlib import Path
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[1]
LIBRARY = ROOT / "icon-library"
CATALOG = LIBRARY / "catalog.json"
REPO = "odinwinchester/Purple-charm-launcher"
REF = os.environ.get("ICON_CATALOG_REF", "main")
REGISTRY = LIBRARY / "packages.json"


def load_packages() -> dict[str, str]:
    packages = json.loads(REGISTRY.read_text(encoding="utf-8"))
    if not isinstance(packages, dict):
        raise ValueError("packages.json must contain a JSON object")
    for name, package in packages.items():
        if not re.fullmatch(r"[a-z0-9_.-]+", name):
            raise ValueError(f"Invalid icon filename stem: {name}")
        if not isinstance(package, str) or not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", package):
            raise ValueError(f"Invalid Android package for {name}")
    if len(set(packages.values())) != len(packages):
        raise ValueError("Duplicate Android package names")
    return packages


def validate_png(data: bytes, name: str) -> None:
    if len(data) < 33 or data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{name}: not a valid PNG signature")
    if data[12:16] != b"IHDR" or struct.unpack(">I", data[8:12])[0] != 13:
        raise ValueError(f"{name}: missing PNG IHDR")
    width, height = struct.unpack(">II", data[16:24])
    if not (32 <= width <= 4096 and 32 <= height <= 4096):
        raise ValueError(f"{name}: invalid dimensions {width}x{height}")
    if len(data) > 5 * 1024 * 1024:
        raise ValueError(f"{name}: file exceeds 5 MiB")
    if b"IEND" not in data[-16:]:
        raise ValueError(f"{name}: missing PNG end marker")


def main() -> None:
    if not re.fullmatch(r"[A-Za-z0-9._/-]+", REF) or ".." in REF:
        raise ValueError("Unsafe catalog ref")
    packages = load_packages()
    previous = json.loads(CATALOG.read_text(encoding="utf-8"))
    if previous.get("schemaVersion") != 1:
        raise ValueError("Unsupported catalog schema")
    old_by_package = {e["packageName"]: e for e in previous.get("icons", [])}
    entries = []
    pngs = sorted(LIBRARY.glob("*.png"))
    unknown = [p.name for p in pngs if p.stem not in packages]
    if unknown:
        raise ValueError("Register package names in icon-library/packages.json before publishing: " + ", ".join(unknown))
    for path in pngs:
        data = path.read_bytes()
        validate_png(data, path.name)
        package = packages[path.stem]
        digest = hashlib.sha256(data).hexdigest()
        old = old_by_package.get(package, {})
        old_version = int(old.get("version", 0))
        version = max(1, old_version + (1 if old.get("sha256") and old["sha256"] != digest else 0))
        entries.append({
            "packageName": package,
            "version": version,
            "url": f"https://raw.githubusercontent.com/{REPO}/{quote(REF, safe='/')}/icon-library/{quote(path.name)}",
            "sha256": digest,
        })
    if not entries:
        raise ValueError("No PNGs found; refusing to publish an empty catalog")
    output = json.dumps({"schemaVersion": 1, "icons": entries}, indent=2, ensure_ascii=False) + "\n"
    CATALOG.write_text(output, encoding="utf-8")
    print(f"Verified {len(entries)} PNGs; catalog written for {REF}")


if __name__ == "__main__":
    main()
