"""Read-only verification of the owner-supplied Cosmic Horror master."""
import hashlib
import struct
from pathlib import Path

path = Path('app/src/main/res/drawable/cosmic_horror_azathoth.png')
raw = path.read_bytes()
assert len(raw) == 4054647
assert raw[:8] == b'\x89PNG\r\n\x1a\n'
assert struct.unpack('>II', raw[16:24]) == (941, 1672)
assert raw[24:26] == bytes((8, 6))  # 8-bit RGBA
assert hashlib.sha256(raw).hexdigest() == '6622fd49e9d4456d4fabd94ae71ba4478575b8a05ec6eb810be89be67e3a46dd'
print('Azathoth original intact: PNG RGBA 941x1672; SHA-256 approved.')
