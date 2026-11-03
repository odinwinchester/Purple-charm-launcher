# Purple Charm Icon Library

This is the optional online collection of custom app icons. Bundled Purple Charm artwork has priority, then verified downloaded icons, then the original Purple Charm mask fallback.

Add a PNG (max 1 MB, dimensions up to 1024 x 1024) under `icons/`. Compute its SHA-256, then add its exact Android package identifier to `catalog.json`:

```json
{
  "schema": 1,
  "icons": {
    "com.example.application": {
      "file": "example.png",
      "sha256": "64 lowercase hexadecimal characters"
    }
  }
}
```

The app fetches the catalog from the repository's **main** branch at most once every 24 hours. No installed-app list is transmitted. Icons are stored in private app storage, verified by SHA-256, and work offline after download. The first empty catalog is intentional: do not publish placeholder artwork. New artwork must be reviewed before publication.

Security: GitHub's raw content host is trusted as the publisher, so repository write access must be protected. HTTPS and file hashes provide transfer and integrity checks, but hashes from the same catalog are not a cryptographic publisher signature. Never allow unreviewed pull requests to update this catalog.
