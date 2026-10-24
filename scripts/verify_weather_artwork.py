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
assert len(resources) == 26 and len(set(resources)) == 26
assert set(resources[:20]) == {Path(name).stem for name in expected}
print('20/20 original catalog entries preserved.')

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
all_paths = set(expected) | new_paths
assert {str(p) for p in Path('app/src/main/res/drawable-nodpi').glob('*.png')} == all_paths
assert set(resources) == {Path(name).stem for name in all_paths}
print('26/26 PNGs intact; six new files match owner source blobs, sizes and SHA-256.')
