"""Inventário das suítes Robot Framework (PROP-003, Fase 0).

Lê as suítes pelo parser do Robot Framework (robot.api), sem executar nada, e gera a baseline:
- suítes e casos por projeto (api, ui) e por domínio (pasta logo abaixo de test/);
- tags de rastreabilidade de cada caso (identificadores como PRD-RF-001, KNOW-006, REV-03, ORG-NAV-001, BUG-66);
- casos sem nenhum identificador de rastreabilidade;
- arquivos por camada LKDF (TEST, SCENARIO, FLOW, POM, RESOURCE).

Uso: python test/robot/tools/inventory.py [--out DIR]   (padrão: test/robot/results)
Gera inventory.json e inventory.md. A contagem é calculada a cada execução; não é mantida à mão.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

from robot.api import get_model
from robot.api.parsing import ModelVisitor

ROBOT_ROOT = Path(__file__).resolve().parents[1]
PROJECTS = ("api", "ui")
# Identificador de rastreabilidade: prefixo em maiúsculas e número (PRD-RF-001, KNOW-006, SAD-7.9, ASVS-V4, BUG-66).
TRACE_TAG = re.compile(r"^[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)*-[A-Z]?\d[\w.]*$")


def layer_of(path: Path) -> str:
    """Camada LKDF do arquivo, pela pasta (TEST, SCENARIO, FLOW, POM, RESOURCE ou OTHER)."""
    parts = path.relative_to(ROBOT_ROOT).parts
    if len(parts) > 1 and parts[1] == "test":
        return "TEST"
    if len(parts) > 2 and parts[1] == "src":
        return {"scenario": "SCENARIO", "flow": "FLOW", "pom": "POM", "resource": "RESOURCE"}.get(parts[2], "OTHER")
    return "OTHER"


class _Cases(ModelVisitor):
    def __init__(self) -> None:
        self.cases: list[dict] = []

    def visit_TestCase(self, node) -> None:  # noqa: N802 (API do Robot)
        tags: list[str] = []
        documented = False
        for item in node.body:
            kind = type(item).__name__
            if kind == "Tags":
                tags.extend(item.values)
            elif kind == "Documentation":
                documented = True
        self.cases.append(
            {
                "name": node.name,
                "line": node.lineno,
                "tags": tags,
                "trace": [t for t in tags if TRACE_TAG.match(t)],
                "documented": documented,
            }
        )


def collect() -> dict:
    suites = []
    layers: dict[str, Counter] = {p: Counter() for p in PROJECTS}
    for project in PROJECTS:
        base = ROBOT_ROOT / project
        for path in sorted(base.rglob("*")):
            if path.suffix not in (".robot", ".resource") or "results" in path.parts:
                continue
            layers[project][layer_of(path)] += 1
            if path.suffix != ".robot":
                continue
            visitor = _Cases()
            visitor.visit(get_model(str(path)))
            rel = path.relative_to(ROBOT_ROOT).as_posix()
            domain = path.relative_to(base / "test").parts[0] if (base / "test") in path.parents else "?"
            suites.append({"project": project, "domain": domain, "file": rel, "cases": visitor.cases})
    return {"suites": suites, "layers": {p: dict(c) for p, c in layers.items()}}


def to_markdown(data: dict) -> str:
    suites = data["suites"]
    total_cases = sum(len(s["cases"]) for s in suites)
    lines = ["# Inventário das suítes Robot (PROP-003, Fase 0)", ""]
    lines.append(f"{len(suites)} suítes, {total_cases} casos. Gerado pelo `test/robot/tools/inventory.py`.")
    lines += ["", "## Por projeto e domínio", "", "| Projeto | Domínio | Suítes | Casos |", "|---|---|---:|---:|"]
    by_domain: dict[tuple, list] = defaultdict(list)
    for s in suites:
        by_domain[(s["project"], s["domain"])].append(s)
    for (project, domain), items in sorted(by_domain.items()):
        lines.append(f"| {project} | {domain} | {len(items)} | {sum(len(s['cases']) for s in items)} |")
    lines += ["", "## Arquivos por camada LKDF", "", "| Projeto | TEST | SCENARIO | FLOW | POM | RESOURCE |", "|---|---:|---:|---:|---:|---:|"]
    for project, counts in data["layers"].items():
        lines.append(
            f"| {project} | " + " | ".join(str(counts.get(k, 0)) for k in ("TEST", "SCENARIO", "FLOW", "POM", "RESOURCE")) + " |"
        )
    trace = Counter(t for s in suites for c in s["cases"] for t in c["trace"])
    lines += ["", "## Identificadores de rastreabilidade", "", "| Identificador | Casos |", "|---|---:|"]
    lines += [f"| {tag} | {n} |" for tag, n in sorted(trace.items())]
    missing = [(s["file"], c) for s in suites for c in s["cases"] if not c["trace"]]
    lines += ["", f"## Casos sem identificador de rastreabilidade ({len(missing)})", ""]
    lines += [f"- `{f}:{c['line']}` {c['name']}" for f, c in missing] or ["Nenhum."]
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default=str(ROBOT_ROOT / "results"))
    args = parser.parse_args(argv)
    data = collect()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    (out / "inventory.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    (out / "inventory.md").write_text(to_markdown(data), encoding="utf-8")
    cases = sum(len(s["cases"]) for s in data["suites"])
    print(f"Inventário: {len(data['suites'])} suítes, {cases} casos -> {out / 'inventory.md'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
