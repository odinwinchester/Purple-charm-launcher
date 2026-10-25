#!/usr/bin/env bash
set -euo pipefail
umask 077
for tool in keytool openssl base64 python3; do command -v "$tool" >/dev/null; done
work="$(mktemp -d)"
trap 'python3 - "$work" <<'"'"'PY'"'"'
import pathlib, shutil, sys
shutil.rmtree(pathlib.Path(sys.argv[1]), ignore_errors=True)
PY' EXIT
export PYTHONDONTWRITEBYTECODE=1
python3 -c 'import cryptography' || { echo "cryptography indisponível." >&2; exit 1; }
openssl rand -hex 24 > "$work/store.pass"
openssl rand -hex 24 > "$work/key.pass"
openssl rand -hex 32 > "$work/recovery.pass"
chmod 600 "$work"/*.pass
keytool -genkeypair -noprompt -alias purple-charm-test -keyalg RSA -keysize 2048 -validity 2 -keystore "$work/test.jks" -storetype JKS -dname "CN=Purple Charm Disposable Test" -storepass:file "$work/store.pass" -keypass:file "$work/key.pass" >/dev/null 2>&1
keytool -exportcert -alias purple-charm-test -keystore "$work/test.jks" -storepass:file "$work/store.pass" -file "$work/original.der" >/dev/null 2>&1
python3 scripts/keystore_crypto.py encrypt "$work/test.jks" "$work/test.pccks" --passphrase-file "$work/recovery.pass"
base64 -w0 "$work/test.pccks" > "$work/test.pccks.b64"
base64 -d "$work/test.pccks.b64" > "$work/decoded.pccks"
python3 scripts/keystore_crypto.py decrypt "$work/decoded.pccks" "$work/restored.jks" --passphrase-file "$work/recovery.pass"
cmp "$work/test.jks" "$work/restored.jks"
keytool -exportcert -alias purple-charm-test -keystore "$work/restored.jks" -storepass:file "$work/store.pass" -file "$work/restored.der" >/dev/null 2>&1
cmp "$work/original.der" "$work/restored.der"
openssl rand -hex 32 > "$work/wrong.pass"
if python3 scripts/keystore_crypto.py decrypt "$work/decoded.pccks" "$work/should-not-exist.jks" --passphrase-file "$work/wrong.pass"; then
  echo "Falha: ciphertext aceitou senha incorreta." >&2
  exit 1
fi
test ! -e "$work/should-not-exist.jks"
cp "$work/decoded.pccks" "$work/tampered.pccks"
python3 - "$work/tampered.pccks" <<'PY'
import pathlib, sys
p = pathlib.Path(sys.argv[1])
data = bytearray(p.read_bytes())
data[-1] ^= 1
p.write_bytes(data)
PY
if python3 scripts/keystore_crypto.py decrypt "$work/tampered.pccks" "$work/should-not-exist.jks" --passphrase-file "$work/recovery.pass"; then
  echo "Falha: ciphertext adulterado foi aceito." >&2
  exit 1
fi
test ! -e "$work/should-not-exist.jks"
echo "OK: keystore temporário recuperado byte a byte; certificado idêntico; senha incorreta e alteração rejeitadas."
