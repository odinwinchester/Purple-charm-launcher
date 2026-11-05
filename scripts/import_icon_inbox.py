#!/usr/bin/env python3
"""Import approved Android-package-named PNGs from icon-library/inbox.

Example: icon-library/inbox/com.openai.chatgpt.png
No icon is redesigned or modified: bytes are copied unchanged.
"""
import json
import re
import shutil
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
LIB = ROOT / "icon-library"
INBOX = LIB / "inbox"
REGISTRY = LIB / "packages.json"
PACKAGE = re.compile(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+")

def validate(path: Path):
    if not PACKAGE.fullmatch(path.stem):
        raise ValueError(f"{path.name}: filename must be the Android package name")
    if path.stat().st_size > 2 * 1024 * 1024:
        raise ValueError(f"{path.name}: PNG exceeds 2 MiB")
    with Image.open(path) as im:
        if im.format != "PNG" or not (32 <= im.width <= 2048 and 32 <= im.height <= 2048):
            raise ValueError(f"{path.name}: invalid PNG or dimensions")
        if im.mode not in ("RGBA", "P") or "A" not in im.convert("RGBA").getbands():
            raise ValueError(f"{path.name}: RGBA required")
        alpha = im.convert("RGBA").getchannel("A")
        low, high = alpha.getextrema()
        if low != 0 or high == 0:
            raise ValueError(f"{path.name}: transparent background and visible artwork required")
        w, h = im.size
        if any(alpha.getpixel(pt) != 0 for pt in ((0,0),(w-1,0),(0,h-1),(w-1,h-1))):
            raise ValueError(f"{path.name}: all four corners must be transparent")
        histogram = alpha.histogram()
        if histogram[0] < (w * h) // 20:
            raise ValueError(f"{path.name}: at least 5% must be fully transparent")
        im.verify()

def main():
    paths = sorted(INBOX.glob("*.png")) if INBOX.exists() else []
    if not paths:
        print("Inbox empty; nothing to publish")
        return
    packages = json.loads(REGISTRY.read_text(encoding="utf-8"))
    # Validate all files before touching any destination.
    for path in paths:
        validate(path)
        if path.stem in packages.values() and packages.get(path.stem) != path.stem:
            raise ValueError(f"{path.name}: package already mapped to another icon")
    for path in paths:
        packages[path.stem] = path.stem
        shutil.copyfile(path, LIB / path.name)
        path.unlink()
    REGISTRY.write_text(json.dumps(packages, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Imported {len(paths)} approved icons")

if __name__ == "__main__":
    main()
