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
