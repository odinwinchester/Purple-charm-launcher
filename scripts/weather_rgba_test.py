#!/usr/bin/env python3
"""Experimental weather PNG background removal. Never modifies original source art in-place."""
import hashlib
import io
import json
from pathlib import Path
from PIL import Image
from rembg import remove, new_session

ROOT = Path(__file__).resolve().parents[1]
base = ROOT / "app/src/main/res/drawable-nodpi"
inventories = ["docs/weather-artwork-inventory.json", "docs/weather-additional-artwork-inventory.json"]
items = [item for file in inventories for item in json.loads((ROOT / file).read_text())]
assert len(items) == 26 and len({x["path"] for x in items}) == 26
preview = ROOT / "rgba-test-preview"
preview.mkdir(exist_ok=True)
session = new_session("u2net")
report = []
for item in items:
    src = ROOT / item["path"]
    raw = src.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == item["sha256"], f"Source integrity mismatch: {src}"
    with Image.open(io.BytesIO(raw)) as im:
        rgb = im.convert("RGBA")
        size = im.size
    result = remove(rgb, session=session, alpha_matting=True, alpha_matting_foreground_threshold=240,
                    alpha_matting_background_threshold=10, alpha_matting_erode_size=5)
    out = result.convert("RGBA")
    assert out.size == size
    alpha = out.getchannel("A")
    extrema = alpha.getextrema()
    assert extrema[0] == 0 and extrema[1] == 255, f"No meaningful alpha: {src}"
    pixels = list(alpha.getdata())
    transparent = sum(a == 0 for a in pixels) / len(pixels)
    opaque = sum(a == 255 for a in pixels) / len(pixels)
    assert 0.01 < transparent < 0.95 and opaque > 0.01, f"Suspicious mask: {src}: {transparent:.3f}"
    # Preserve original color channels on retained pixels. Alpha comes from segmentation.
    rgb.putalpha(alpha)
    rgb.save(src, "PNG", optimize=True)
    # Side-by-side visual review on dark and light backgrounds.
    panels = []
    for color in ("#161020", "#e8e4ed"):
        bg = Image.new("RGBA", size, color)
        bg.alpha_composite(rgb)
        panels.append(bg.convert("RGB"))
    contact = Image.new("RGB", (size[0] * 2, size[1]))
    for i, panel in enumerate(panels):
        contact.paste(panel, (i * size[0], 0))
    contact.thumbnail((1400, 600))
    contact.save(preview / (src.stem + ".jpg"), quality=88)
    report.append(dict(path=item["path"], width=size[0], height=size[1],
                       transparent_fraction=round(transparent, 4),
                       opaque_fraction=round(opaque, 4),
                       original_sha256=item["sha256"],
                       test_sha256=hashlib.sha256(src.read_bytes()).hexdigest()))
(preview / "report.json").write_text(json.dumps(report, indent=2))
print("26 experimental RGBA images generated. Visual inspection is mandatory before approval.")
