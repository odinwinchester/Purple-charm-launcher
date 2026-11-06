"""Validate current weather artwork and active graphic-resource integrity."""
import hashlib
import json
import re
import struct
from pathlib import Path

catalog = Path("app/src/main/java/com/purplecharm/launcher/WeatherArtworkCatalog.kt").read_text()
resources = re.findall(r"R\.drawable\.([a-z0-9_]+)", catalog)
assert len(resources) == len(set(resources)), "Duplicate drawable entries in weather catalog"
directory = Path("app/src/main/res/drawable-nodpi")
available = {p.stem for p in directory.glob("*.png")}
missing = set(resources) - available
assert not missing, f"Missing catalog resources: {sorted(missing)}"
assert len(available) == len(list(directory.glob("*.png"))), "Duplicate PNG stems"
print(f"{len(resources)} catalog resources available.")

additional = json.loads(Path("docs/weather-additional-artwork-inventory.json").read_text())
assert len(additional) == 6
fixtures = {}
for line in Path("app/src/test/resources/weather-additional-artwork-sizes.tsv").read_text().splitlines():
    name, width, height = line.split("\t")
    fixtures[name] = (int(width), int(height))
for item in additional:
    path = Path(item["path"])
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n", str(path)
    assert hashlib.sha256(data).hexdigest() == item["sha256"], str(path)
    assert struct.unpack(">II", data[16:24]) == (item["width"], item["height"]) == fixtures[path.stem]
    assert len(data) == item["bytes"]
    assert hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest() == item["git_blob_sha"]
print("6/6 additional approved PNGs verified.")

all_graphics = json.loads(Path("docs/graphic-resource-sha256.json").read_text())
for path, digest in all_graphics.items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == digest, path
print(f"{len(all_graphics)} active graphical resources verified.")
