#!/usr/bin/env python3
"""Publica a lista homologada no Discord via webhook configurado no GitHub Secrets."""
import json
import os
import urllib.request

url = os.environ.get("DISCORD_WEBHOOK_URL", "").strip()
if not url:
    raise SystemExit("DISCORD_WEBHOOK_URL ausente. Configure o Secret no GitHub.")

with open("docs/compatibilidade.json", encoding="utf-8") as f:
    data = json.load(f)

def lines(status, symbol, empty):
    names = [d["modelo"] for d in data["aparelhos"] if d["status"] == status]
    return "\n".join(f"{symbol} {name}" for name in names) if names else empty

content = (
    "💜 **PURPLE CHARM LAUNCHER — COMPATIBILIDADE OFICIAL**\n"
    f"**Versão:** {data['versao']}\n\n"
    "🟢 **COMPATÍVEIS (testados)**\n"
    + lines("compativel", "✓", "Nenhum modelo homologado até o momento.")
    + "\n\n🔴 **INCOMPATÍVEIS (confirmados)**\n"
    + lines("incompativel", "✕", "Nenhum modelo confirmado até o momento.")
    + "\n\n🟡 **EM TESTES**\n"
    + lines("em_teste", "?", "Nenhum modelo cadastrado.")
    + "\n\n**Requisitos preliminares:** Android 8.0+, RAM recomendada 4 GB, 1 GB livre.\n"
    "⚠️ A lista reflete testes realizados, não apenas especificações."
)
if len(content) > 2000:
    raise SystemExit("Mensagem excede 2000 caracteres; divida a lista antes de publicar.")
req = urllib.request.Request(
    url, data=json.dumps({"content": content, "allowed_mentions": {"parse": []}}).encode(),
    headers={"Content-Type": "application/json", "User-Agent": "PurpleCharmCompatibility/1.0"},
    method="POST",
)
with urllib.request.urlopen(req, timeout=20) as response:
    if response.status not in (200, 204):
        raise SystemExit(f"Discord retornou HTTP {response.status}")
print("Mensagem enviada ao Discord.")
