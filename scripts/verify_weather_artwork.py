"""Ensure all twenty approved PNGs remain exactly intact (run from repository root)."""
import hashlib
import json
from pathlib import Path

expected = json.loads(Path('docs/weather-artwork-sha256.json').read_text())
assert len(expected) == 20, f'Expected 20 originals, found {len(expected)}'
for name, digest in expected.items():
    assert hashlib.sha256(Path(name).read_bytes()).hexdigest() == digest, name
print('20/20 original weather PNGs intact (SHA-256).')
