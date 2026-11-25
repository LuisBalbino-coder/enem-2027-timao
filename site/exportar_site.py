#!/usr/bin/env python3
"""Gera a versão estática/PWA em docs/ a partir do servidor local.

O GitHub Pages não executa Java, então o site publicado usa um instantâneo
somente leitura da API e guarda o progresso no localStorage do navegador.
Rode com o servidor principal em http://127.0.0.1:7077:

    python3 site/exportar_site.py
"""

from __future__ import annotations

import json
import shutil
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

BASE = Path(__file__).resolve().parent
RAIZ = BASE.parent
PUBLICO = RAIZ / "src" / "main" / "resources" / "public"
SEED = RAIZ / "src" / "main" / "resources" / "seed" / "calendario.json"
DOCS = RAIZ / "docs"
API = "http://127.0.0.1:7077"


def buscar(rota: str):
    url = API + rota
    try:
        with urllib.request.urlopen(url, timeout=15) as resposta:
            if resposta.status != 200:
                raise RuntimeError(f"{url} respondeu HTTP {resposta.status}")
            return json.load(resposta)
    except urllib.error.URLError as erro:
        raise RuntimeError(
            f"Não foi possível ler {url}. Suba o servidor com ./rodar.sh antes de exportar. ({erro})"
        ) from erro


def montar_snapshot() -> dict:
    saude = buscar("/api/saude")
    if saude.get("status") != "ok":
        raise RuntimeError("O servidor local não está saudável.")

    topicos = buscar("/api/topicos")
    detalhes = [
        buscar("/api/topicos/" + urllib.parse.quote(topico["slug"], safe=""))
        for topico in topicos
    ]
    marcos = json.loads(SEED.read_text(encoding="utf-8"))["marcos"]

    return {
        "geradoEm": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "origem": API,
        "config": buscar("/api/configuracoes"),
        "dashboard": buscar("/api/dashboard"),
        "materias": buscar("/api/materias"),
        "topicos": topicos,
        "detalhes": detalhes,
        "questoes": buscar("/api/questoes?limite=200"),
        "flashcards": buscar("/api/flashcards"),
        "repertorios": buscar("/api/redacao/repertorios"),
        "temas": buscar("/api/redacao/temas"),
        "competencias": buscar("/api/redacao/competencias"),
        "marcos": marcos,
    }


def gerar_index() -> str:
    html = (PUBLICO / "index.html").read_text(encoding="utf-8")
    html = html.replace('href="/styles.css"', 'href="styles.css"')
    html = html.replace('  <script src="/backup-core.js"></script>', '  <script src="backup-core.js"></script>', 1)
    html = html.replace(
        '  <link rel="icon"',
        '  <link rel="manifest" href="manifest.webmanifest">\n  <link rel="icon"',
        1,
    )
    html = html.replace(
        '  <script src="/app.js"></script>',
        '  <script>window.ENEM_ESTATICO = true;</script>\n'
        '  <script src="offline.js"></script>\n'
        '  <script src="app.js"></script>',
        1,
    )
    html = html.replace("carregar o conteúdo do banco", "carregar o conteúdo do site")

    if ('href="/styles.css"' in html or 'src="/app.js"' in html or
            'src="/backup-core.js"' in html):
        raise RuntimeError("O index gerado ainda tem caminhos absolutos incompatíveis com o Pages.")
    if ("window.ENEM_ESTATICO" not in html or 'src="offline.js"' not in html or
            'src="backup-core.js"' not in html):
        raise RuntimeError("O index gerado não ativou o modo estático.")
    return html


def main() -> int:
    print("Lendo dados do servidor local...")
    snapshot = montar_snapshot()
    print(
        f"  {len(snapshot['materias'])} matérias, {len(snapshot['topicos'])} tópicos, "
        f"{len(snapshot['detalhes'])} detalhes, {len(snapshot['questoes'])} questões, "
        f"{len(snapshot['flashcards'])} flashcards."
    )

    if DOCS.exists():
        shutil.rmtree(DOCS)
    (DOCS / "data").mkdir(parents=True)

    shutil.copy2(PUBLICO / "backup-core.js", DOCS / "backup-core.js")
    shutil.copy2(PUBLICO / "app.js", DOCS / "app.js")
    shutil.copy2(PUBLICO / "styles.css", DOCS / "styles.css")
    for nome in ("offline.js", "sw.js", "manifest.webmanifest", "icon.svg"):
        shutil.copy2(BASE / nome, DOCS / nome)
    (DOCS / ".nojekyll").write_text("", encoding="utf-8")
    (DOCS / "index.html").write_text(gerar_index(), encoding="utf-8")
    (DOCS / "data" / "snapshot.json").write_text(
        json.dumps(snapshot, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )

    print(f"Site estático gerado em {DOCS}")
    print("Teste local: python3 -m http.server 7080 --directory docs --bind 127.0.0.1")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except RuntimeError as erro:
        print(f"ERRO: {erro}", file=sys.stderr)
        sys.exit(1)
