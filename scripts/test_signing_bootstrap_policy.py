#!/usr/bin/env python3
"""Static checks for required-check coverage and one-time key provisioning."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
workflow = (root / ".github/workflows/signing-crypto-selftest.yml").read_text()
generator = (root / ".github/workflows/generate-signing-keystore.yml").read_text()
errors = []

pull_request = workflow.split("  pull_request:", 1)[1].split("  workflow_dispatch:", 1)[0]
if "paths:" in pull_request or "paths-ignore:" in pull_request:
    errors.append("required safe-selftests workflow must run for every PR to main")
if not re.search(r"safe-selftests:\s*\n", workflow):
    errors.append("required check job safe-selftests is missing")
if 'test "${GITHUB_RUN_ATTEMPT:-1}" = "1"' not in generator:
    errors.append("manual reruns of a key-generation run must be blocked")
if "EXISTING_CERT" not in generator or "test -z \"$EXISTING_CERT\"" not in generator:
    errors.append("existing certificate fingerprint must block another key generation")
if "prior_runs" not in generator or "prior_artifacts" not in generator:
    errors.append("previous attempts and backup artifacts must block another key generation")
if "if: ${{ always() }}" not in generator or "if-no-files-found: ignore" not in generator:
    errors.append("encrypted backup upload must be attempted after recoverable failures")
removed_password_refs = ("PURPLE_" + "KEY_PASSWORD", "PURPLE_" + "RECOVERY_PASSPHRASE")
if any(reference in generator for reference in removed_password_refs):
    errors.append("legacy password secrets remain required")

if errors:
    print("Bootstrap policy failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)
print("OK: required check is unfiltered; reruns, prior keys and fingerprints block regeneration; encrypted recovery is retained when possible.")
