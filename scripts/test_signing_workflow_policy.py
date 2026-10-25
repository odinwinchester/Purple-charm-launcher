#!/usr/bin/env python3
"""Static policy checks for the single-password Android signing workflows."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
WORKFLOWS = ROOT / ".github/workflows"
SIGNING_FILES = [
    WORKFLOWS / "generate-signing-keystore.yml",
    WORKFLOWS / "verify-signing-recovery.yml",
    WORKFLOWS / "stable-apk.yml",
]
ALIAS = "purple-charm-release"
errors = []

for path in SIGNING_FILES:
    content = path.read_text()
    if re.search(r"secrets\.PURPLE_(?:KEY_PASSWORD|RECOVERY_PASSPHRASE|KEY_ALIAS)", content):
        errors.append(f"{path.name}: contains a removed password or secret alias")
    if "environment: android-signing" not in content:
        errors.append(f"{path.name}: missing protected signing environment")

for path in (WORKFLOWS / "generate-signing-keystore.yml", WORKFLOWS / "verify-signing-recovery.yml", WORKFLOWS / "stable-apk.yml", ROOT / "app/build.gradle.kts"):
    if ALIAS not in path.read_text():
        errors.append(f"{path.relative_to(ROOT)}: fixed key alias is missing")

crypto = (ROOT / "scripts/keystore_crypto.py").read_text()
if "AESGCM" not in crypto or "hashlib.scrypt" not in crypto:
    errors.append("backup encryption must use authenticated AES-GCM and scrypt")

generate = SIGNING_FILES[0].read_text()
if "EXISTING_CIPHERTEXT" not in generate or "prior_runs" not in generate or "prior_artifacts" not in generate:
    errors.append("key generation must block if ciphertext or any previous run/artifact exists")
if "-storepass:file" not in generate or "-keypass:file" not in generate:
    errors.append("keytool must read password from protected files, not command-line values")

stable = SIGNING_FILES[2].read_text()
if "--passphrase-file \"$RUNNER_TEMP/store.pass\"" not in stable:
    errors.append("stable signing must derive backup decryption from the single store password file")
if "PURPLE_STORE_PASSWORD: ${{ secrets.PURPLE_STORE_PASSWORD }}" not in stable:
    errors.append("stable signing must consume the one configured password secret")
if re.search(r"--(?:storepass|keypass)\s+\$", stable):
    errors.append("stable signing passes a password value as a CLI argument")

if errors:
    print("Signing policy test failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)
print("OK: single secret, fixed alias, protected environment, authenticated backup and no password CLI arguments.")
