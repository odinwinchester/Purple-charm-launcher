#!/usr/bin/env python3
"""At-most-once Discord announcement for approved Purple Charm releases."""
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

APPROVED = "<!-- purple-charm-release-approved -->"
CLAIMED = "<!-- purple-charm-discord-claimed -->"
ANNOUNCED_PREFIX = "<!-- purple-charm-discord-announced:"
APK_RE = re.compile(r"^Purple-Charm-Crystal-Class-v[0-9]+\.[0-9]+\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?-STABLE\.apk$")


def eligible_release(release):
    tag = release.get("tag_name", "")
    assets = release.get("assets", [])
    names = {asset.get("name", "") for asset in assets}
    return (
        not release.get("draft", True)
        and not release.get("prerelease", True)
        and tag.startswith("v")
        and APPROVED in (release.get("body") or "")
        and any(APK_RE.fullmatch(name) for name in names)
        and "release-metadata.json" in names
    )


def claim_body(body):
    body = body or ""
    if APPROVED not in body:
        raise ValueError("Release sem marcador de aprovação.")
    if CLAIMED in body or ANNOUNCED_PREFIX in body:
        return None
    return body.rstrip() + "\n\n" + CLAIMED


def request_json(method, url, *, token=None, payload=None, headers=None):
    request_headers = {"Accept": "application/vnd.github+json", "User-Agent": "PurpleCharmReleaseBot/1.0"}
    if token:
        request_headers["Authorization"] = "Bearer " + token
        request_headers["X-GitHub-Api-Version"] = "2022-11-28"
    if headers:
        request_headers.update(headers)
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    if data is not None:
        request_headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=request_headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=25) as response:
            raw = response.read()
            return json.loads(raw) if raw else None
    except Exception:
        # Do not print exception text: webhook URLs contain the webhook credential.
        raise RuntimeError("External request failed; endpoint details suppressed.") from None


def run_announcement():
    repo = os.environ["GITHUB_REPOSITORY"]
    release_id = os.environ["RELEASE_ID"]
    token = os.environ.get("GITHUB_TOKEN", "")
    webhook = os.environ.get("DISCORD_WEBHOOK_URL", "").strip()
    if not token or not webhook:
        raise RuntimeError("Required protected credentials are missing.")
    api = "https://api.github.com/repos/" + repo + "/releases/" + release_id
    release = request_json("GET", api, token=token)
    if not eligible_release(release):
        print("Release não homologada ou sem APK estável/metadados; anúncio ignorado.")
        return
    updated_body = claim_body(release.get("body"))
    if updated_body is None:
        print("Anúncio já reservado/publicado; duplicação bloqueada.")
        return
    request_json("PATCH", api, token=token, payload={"body": updated_body})
    apk = next(a for a in release["assets"] if APK_RE.fullmatch(a.get("name", "")))
    tag = release["tag_name"]
    content = (
        "💜 **Purple Charm Crystal Class — versão estável aprovada**\n"
        f"**Versão:** {tag}\n"
        f"**Lançamento:** {release['html_url']}\n"
        f"**Baixar APK:** {apk['browser_download_url']}\n"
        "Testes, versão Android e assinatura foram validados pelo GitHub Actions."
    )
    separator = "&" if "?" in webhook else "?"
    response = request_json(
        "POST",
        webhook + separator + "wait=true",
        payload={"content": content, "allowed_mentions": {"parse": []}},
        headers={"User-Agent": "PurpleCharmReleaseBot/1.0"},
    )
    message_id = str(response.get("id", "")) if isinstance(response, dict) else ""
    final_body = updated_body + (f"\n<!-- purple-charm-discord-announced:{message_id} -->" if message_id else "")
    request_json("PATCH", api, token=token, payload={"body": final_body})
    print("Anúncio enviado; marca de idempotência gravada na release.")


def self_test():
    base = {
        "draft": False,
        "prerelease": False,
        "tag_name": "v0.7.2",
        "body": "Release aprovada\\n" + APPROVED,
        "assets": [
            {"name": "Purple-Charm-Crystal-Class-v0.7.2-STABLE.apk"},
            {"name": "release-metadata.json"},
        ],
    }
    assert eligible_release(base)
    assert not eligible_release({**base, "draft": True})
    assert not eligible_release({**base, "prerelease": True})
    assert not eligible_release({**base, "body": "sem aprovação"})
    assert not eligible_release({**base, "assets": []})
    claimed = claim_body(base["body"])
    assert CLAIMED in claimed
    assert claim_body(claimed) is None
    assert claim_body(claimed + "\\n" + ANNOUNCED_PREFIX + "123 -->") is None
    try:
        claim_body("sem aprovação")
    except ValueError:
        pass
    else:
        raise AssertionError("Claim aceitou release sem aprovação.")
    print("8 verificações do bot passaram; nenhum webhook ou endpoint foi chamado.")

if __name__ == "__main__":
    if sys.argv[1:] == ["--self-test"]:
        self_test()
    else:
        try:
            run_announcement()
        except Exception as error:
            print(f"Discord release announcement failed: {type(error).__name__}", file=sys.stderr)
            raise SystemExit(1)
