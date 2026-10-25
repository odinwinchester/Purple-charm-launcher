#!/usr/bin/env python3
"""Authenticated encryption for the Android signing keystore backup."""
import argparse
import hashlib
import os
import pathlib
import secrets
import sys

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MAGIC = b"PURPLE-CHARM-KEYSTORE-V1\x00"
SALT_SIZE = 16
NONCE_SIZE = 12
KEY_SIZE = 32
SCRYPT_N = 1 << 15
SCRYPT_R = 8
SCRYPT_P = 1


def derive(passphrase: bytes, salt: bytes) -> bytes:
    if len(passphrase) < 20:
        raise ValueError("Recovery phrase must contain at least 20 bytes.")
    return hashlib.scrypt(
        passphrase,
        salt=salt,
        n=SCRYPT_N,
        r=SCRYPT_R,
        p=SCRYPT_P,
        dklen=KEY_SIZE,
        maxmem=64 * 1024 * 1024,
    )


def encrypt(source: pathlib.Path, target: pathlib.Path, password_file: pathlib.Path) -> None:
    data = source.read_bytes()
    passphrase = password_file.read_bytes().rstrip(b"\r\n")
    salt = secrets.token_bytes(SALT_SIZE)
    nonce = secrets.token_bytes(NONCE_SIZE)
    header = MAGIC + salt + nonce
    ciphertext = AESGCM(derive(passphrase, salt)).encrypt(nonce, data, header)
    target.write_bytes(header + ciphertext)
    target.chmod(0o600)


def decrypt(source: pathlib.Path, target: pathlib.Path, password_file: pathlib.Path) -> None:
    payload = source.read_bytes()
    minimum = len(MAGIC) + SALT_SIZE + NONCE_SIZE + 16
    if len(payload) < minimum or not payload.startswith(MAGIC):
        raise ValueError("Encrypted backup format is invalid.")
    offset = len(MAGIC)
    salt = payload[offset : offset + SALT_SIZE]
    nonce = payload[offset + SALT_SIZE : offset + SALT_SIZE + NONCE_SIZE]
    header = payload[: offset + SALT_SIZE + NONCE_SIZE]
    passphrase = password_file.read_bytes().rstrip(b"\r\n")
    plaintext = AESGCM(derive(passphrase, salt)).decrypt(nonce, payload[len(header) :], header)
    target.write_bytes(plaintext)
    target.chmod(0o600)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("operation", choices=("encrypt", "decrypt"))
    parser.add_argument("source", type=pathlib.Path)
    parser.add_argument("target", type=pathlib.Path)
    parser.add_argument("--passphrase-file", required=True, type=pathlib.Path)
    args = parser.parse_args()
    try:
        if args.operation == "encrypt":
            encrypt(args.source, args.target, args.passphrase_file)
        else:
            decrypt(args.source, args.target, args.passphrase_file)
    except Exception as exc:
        # Keep error text generic; never include input, password, or payload.
        print(f"Keystore {args.operation} failed: {type(exc).__name__}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
