"""Ensure all twenty approved PNGs remain exactly intact (run from repository root)."""
import hashlib
import json
import struct
from pathlib import Path

expected = json.loads(Path('docs/weather-artwork-sha256.json').read_text())
assert len(expected) == 20, f'Expected 20 originals, found {len(expected)}'
for name, digest in expected.items():
    assert hashlib.sha256(Path(name).read_bytes()).hexdigest() == digest, name
print('20/20 original weather PNGs intact (SHA-256).')

fixtures = {}
for line in Path('app/src/test/resources/weather-artwork-sizes.tsv').read_text().splitlines():
    name, width, height = line.split('\t')
    fixtures[name] = (int(width), int(height))
assert len(fixtures) == 20
for name in expected:
    image = Path(name)
    assert struct.unpack('>II', image.read_bytes()[16:24]) == fixtures[image.stem], name
print('20/20 geometry test fixtures match the actual PNG dimensions.')

inventory = json.loads(Path('docs/weather-artwork-inventory.json').read_text())
assert len(inventory) == 20 and {r['path'] for r in inventory} == set(expected)
for item in inventory:
    image = Path(item['path'])
    assert item['sha256'] == expected[str(image)]
    assert (item['width'], item['height']) == struct.unpack('>II', image.read_bytes()[16:24])
    assert item['association']
print('20/20 inventory entries agree with original hashes, dimensions and associations.')

import re
catalog = Path('app/src/main/java/com/purplecharm/launcher/WeatherArtworkCatalog.kt').read_text()
resources = re.findall(r'R\.drawable\.([a-z0-9_]+)', catalog)
assert len(resources) == len(set(resources)), 'Duplicate drawable entries in weather catalog'
catalog_names = set(resources)
legacy_names = {Path(name).stem for name in expected}
assert legacy_names.issubset({p.stem for p in Path('app/src/main/res/drawable-nodpi').glob('*.png')}), 'Approved original weather PNG missing'
print(f'{len(legacy_names)}/{len(legacy_names)} approved original PNGs retained; inactive entries may be absent from catalog.')

additional = json.loads(Path('docs/weather-additional-artwork-inventory.json').read_text())
assert len(additional) == 6
new_paths = {item['path'] for item in additional}
assert not new_paths.intersection(expected)
new_fixtures = {}
for line in Path('app/src/test/resources/weather-additional-artwork-sizes.tsv').read_text().splitlines():
    name, width, height = line.split('\t')
    new_fixtures[name] = (int(width), int(height))
for item in additional:
    data = Path(item['path']).read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', item['path']
    assert hashlib.sha256(data).hexdigest() == item['sha256'], item['path']
    dimensions = struct.unpack('>II', data[16:24])
    assert dimensions == (item['width'], item['height']) == new_fixtures[Path(item['path']).stem]
    assert len(data) == item['bytes']
    git_blob = hashlib.sha1(f'blob {len(data)}\0'.encode() + data).hexdigest()
    assert git_blob == item['git_blob_sha'], item['path']
print('6/6 approved additional PNGs intact (SHA-256, dimensions, source blobs).')

# New v12 assets expand the drawable directory. Do not demand only 26 PNGs;
# verify every catalog reference exists and keep the original inventory checks.
directory = Path('app/src/main/res/drawable-nodpi')
available = {p.stem for p in directory.glob('*.png')}
missing = catalog_names - available
assert not missing, f'Catalog drawable resources missing from drawable-nodpi: {sorted(missing)}'
assert {Path(name).stem for name in new_paths}.issubset(available)
assert legacy_names.issubset(available)
assert len(available) == len(list(directory.glob('*.png'))), 'Duplicate PNG resource stems'
assert len(available) >= 26, f'Expected at least 26 original and additional assets, got {len(available)}'
print(f'{len(available)} PNGs present; {len(catalog_names)} catalog entries resolve to PNG files.')

# Consolidation protects every existing graphical resource, not only weather art.
all_graphics = json.loads(Path("docs/graphic-resource-sha256.json").read_text())
for path, digest in all_graphics.items():
    assert hashlib.sha256(Path(path).read_bytes()).hexdigest() == digest, path
print(f"{len(all_graphics)}/{len(all_graphics)} original graphical resources intact (SHA-256).")
