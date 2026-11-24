"""Validador de conformidade LKDF das suítes Robot Framework (PROP-003, Fase 1).

Verifica, pelo parser do Robot Framework (sem executar as suítes), se as camadas do Layered Keyword-Driven Framework
respeitam as responsabilidades e a direção TEST -> SCENARIO -> FLOW -> POM, com RESOURCE disponível para todas.

Regras bloqueantes (erro):
  LKDF-QA-001  Imports e chamadas só para a camada abaixo ou para RESOURCE; sem pular camada nem subir.
  LKDF-QA-002  TEST sem lógica: sem asserções (Should ...) nem IF/FOR.
  LKDF-QA-003  SCENARIO sem asserções nem IF/FOR, e sem chamar POM diretamente.
  LKDF-QA-004  FLOW sem interação técnica: nenhuma keyword de biblioteca técnica (Browser, RequestsLibrary) e
               nenhum seletor literal (css=, xpath=, id=, text=).
  LKDF-QA-005  POM sem asserções funcionais (Should ...).
  LKDF-QA-007  Grafo de imports sem ciclos.
  LKDF-QA-008  API e UI não importam recursos um do outro.
  LKDF-QA-010  Sem caminho absoluto de máquina local.
Avisos:
  LKDF-QA-009  Caso sem [Documentation] ou [Tags]; suíte sem Metadata; caso sem identificador de rastreabilidade.
  LKDF-QA-010  Variável de senha, token ou segredo com valor literal fora de %{VARIAVEL=padrão}.

Exceções: test/robot/tools/lkdf_exceptions.txt, uma por linha:
  REGRA | arquivo (relativo a test/robot) | keyword ou * | motivo | responsável | revisar até (AAAA-MM-DD)

Uso: python test/robot/tools/lkdf_lint.py [raiz]   (padrão: test/robot). Sai com 1 se houver erro não excetuado.
"""

from __future__ import annotations

import re
import sys
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path

from robot.api import get_model, get_resource_model
from robot.api.parsing import ModelVisitor

DEFAULT_ROOT = Path(__file__).resolve().parents[1]
PROJECTS = ("api", "ui")
LAYERS = ("TEST", "SCENARIO", "FLOW", "POM", "RESOURCE")
# Para onde cada camada pode importar e chamar (FLOW pode reutilizar outro FLOW; POM, outro POM).
ALLOWED = {
    "TEST": {"SCENARIO", "RESOURCE"},
    "SCENARIO": {"SCENARIO", "FLOW", "RESOURCE"},
    "FLOW": {"FLOW", "POM", "RESOURCE"},
    "POM": {"POM", "RESOURCE"},
    "RESOURCE": {"RESOURCE"},
}
# Bibliotecas que o FLOW pode usar: lógica e dados, nada de interação com o sistema sob teste.
FLOW_SAFE_LIBRARIES = ("BuiltIn", "Collections", "String", "DateTime")
SELECTOR = re.compile(r"^(css|xpath|id|text)=", re.IGNORECASE)
LOCAL_PATH = re.compile(r"([A-Za-z]:\\|/home/|/Users/)")
SECRET_VAR = re.compile(r"^\$\{[^}]*(password|senha|secret|token)[^}]*\}$", re.IGNORECASE)
TRACE_TAG = re.compile(r"^[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)*-[A-Z]?\d[\w.]*$")


def normalize(name: str) -> str:
    return re.sub(r"[\s_]", "", name).lower()


@dataclass
class Finding:
    rule: str
    severity: str  # "erro" ou "aviso"
    file: str
    line: int
    keyword: str
    message: str

    def __str__(self) -> str:
        return f"{self.severity.upper():5} {self.rule}  {self.file}:{self.line}  [{self.keyword or '-'}]  {self.message}"


@dataclass
class Exception_:
    rule: str
    file: str
    keyword: str
    reason: str
    owner: str
    until: date

    def covers(self, f: Finding, today: date | None = None) -> bool:
        # Exceção vencida deixa de valer: o desvio volta a aparecer até ser corrigido ou revisto.
        current = self.until >= (today or date.today())
        return current and self.rule == f.rule and self.file == f.file and self.keyword in ("*", f.keyword)


@dataclass
class FileInfo:
    path: Path
    rel: str
    project: str
    layer: str
    model: object = None
    keywords: dict[str, str] = field(default_factory=dict)  # normalizado -> nome


def layer_of(root: Path, path: Path) -> tuple[str, str]:
    parts = path.relative_to(root).parts
    project = parts[0]
    if len(parts) > 1 and parts[1] == "test":
        return project, "TEST"
    if len(parts) > 2 and parts[1] == "src":
        return project, {"scenario": "SCENARIO", "flow": "FLOW", "pom": "POM", "resource": "RESOURCE"}.get(
            parts[2], "OTHER"
        )
    return project, "OTHER"


def library_keywords(names: tuple[str, ...]) -> set[str]:
    from robot.libdocpkg import LibraryDocumentation

    result: set[str] = set()
    for name in names:
        result |= {normalize(kw.name) for kw in LibraryDocumentation(name).keywords}
    return result


class _Collector(ModelVisitor):
    """Coleta definições, imports e chamadas de um arquivo."""

    def __init__(self) -> None:
        self.imports: list[tuple[str, int, str]] = []  # (tipo, linha, nome)
        self.keywords: list[str] = []
        self.calls: list[tuple[str, int, str, list[str]]] = []  # (keyword em que está, linha, chamada, argumentos)
        self.logic: list[tuple[str, int, str]] = []  # (keyword em que está, linha, tipo)
        self.cases: list[tuple[str, int, bool, list[str]]] = []  # (nome, linha, documentado, tags)
        self.variables: list[tuple[int, str, list[str]]] = []
        self.metadata = False
        self._owner = ""

    def visit_ResourceImport(self, node):  # noqa: N802
        self.imports.append(("resource", node.lineno, node.name))

    def visit_LibraryImport(self, node):  # noqa: N802
        self.imports.append(("library", node.lineno, node.name))

    def visit_Metadata(self, node):  # noqa: N802
        self.metadata = True

    def visit_Variable(self, node):  # noqa: N802
        if node.name:
            self.variables.append((node.lineno, node.name, list(node.value)))

    def visit_SuiteSetup(self, node):  # noqa: N802
        self._setting_call(node)

    visit_SuiteTeardown = visit_TestSetup = visit_TestTeardown = visit_SuiteSetup

    def _setting_call(self, node):
        if node.name and node.name.upper() != "NONE":
            self.calls.append(("*** Settings ***", node.lineno, node.name, list(node.args)))

    def visit_Keyword(self, node):  # noqa: N802
        self.keywords.append(node.name)
        self._owner = node.name
        self.generic_visit(node)
        self._owner = ""

    def visit_TestCase(self, node):  # noqa: N802
        documented = any(type(i).__name__ == "Documentation" for i in node.body)
        tags = [v for i in node.body if type(i).__name__ == "Tags" for v in i.values]
        self.cases.append((node.name, node.lineno, documented, tags))
        self._owner = node.name
        self.generic_visit(node)
        self._owner = ""

    def visit_Setup(self, node):  # noqa: N802
        if node.name and node.name.upper() != "NONE":
            self.calls.append((self._owner, node.lineno, node.name, list(node.args)))

    visit_Teardown = visit_Setup

    def visit_KeywordCall(self, node):  # noqa: N802
        if node.keyword:
            self.calls.append((self._owner, node.lineno, node.keyword, list(node.args)))

    def visit_If(self, node):  # noqa: N802
        self.logic.append((self._owner, node.lineno, "IF"))
        self.generic_visit(node)

    def visit_For(self, node):  # noqa: N802
        self.logic.append((self._owner, node.lineno, "FOR"))
        self.generic_visit(node)


def lint(root: Path = DEFAULT_ROOT, exceptions_file: Path | None = None) -> tuple[list[Finding], list[Finding]]:
    """Devolve (achados, achados cobertos por exceção)."""
    files: dict[Path, FileInfo] = {}
    collected: dict[Path, _Collector] = {}
    for project in PROJECTS:
        for path in sorted((root / project).rglob("*")):
            if path.suffix not in (".robot", ".resource") or "results" in path.parts:
                continue
            proj, layer = layer_of(root, path)
            info = FileInfo(path.resolve(), path.relative_to(root).as_posix(), proj, layer)
            model = get_model(str(path)) if path.suffix == ".robot" else get_resource_model(str(path))
            c = _Collector()
            c.visit(model)
            info.keywords = {normalize(k): k for k in c.keywords}
            files[info.path] = info
            collected[info.path] = c

    # Onde cada keyword do projeto é definida (por projeto: api e ui não se misturam).
    defined: dict[tuple[str, str], FileInfo] = {}
    for info in files.values():
        for norm in info.keywords:
            defined.setdefault((info.project, norm), info)
            defined.setdefault((info.project, normalize(info.path.stem) + "." + norm), info)

    safe = library_keywords(FLOW_SAFE_LIBRARIES)
    findings: list[Finding] = []
    graph: dict[Path, set[Path]] = {}

    def add(rule, severity, info, line, keyword, message):
        findings.append(Finding(rule, severity, info.rel, line, keyword, message))

    for path, info in files.items():
        c = collected[path]
        graph[path] = set()
        raw = path.read_text(encoding="utf-8")
        for number, text in enumerate(raw.splitlines(), 1):
            if LOCAL_PATH.search(text) and not text.lstrip().startswith("#"):
                add("LKDF-QA-010", "erro", info, number, "", "caminho absoluto de máquina local")
        for kind, line, name in c.imports:
            if kind != "resource":
                continue
            target = (path.parent / name).resolve()
            if target not in files:
                continue
            tinfo = files[target]
            graph[path].add(target)
            if tinfo.project != info.project:
                add("LKDF-QA-008", "erro", info, line, "", f"{info.project.upper()} importa recurso de {tinfo.project.upper()}: {name}")
            elif tinfo.layer not in ALLOWED.get(info.layer, set()):
                add("LKDF-QA-001", "erro", info, line, "", f"{info.layer} importa {tinfo.layer}: {name}")
        for owner, line, name, args in c.calls:
            norm = normalize(name)
            target = defined.get((info.project, norm))
            if target is not None:
                if target.layer not in ALLOWED.get(info.layer, set()):
                    rule = "LKDF-QA-003" if (info.layer, target.layer) == ("SCENARIO", "POM") else "LKDF-QA-001"
                    add(rule, "erro", info, line, owner, f"{info.layer} chama {target.layer}: {name} ({target.rel})")
                continue
            if info.layer in ("TEST", "SCENARIO", "POM") and norm.startswith("should"):
                rule = {"TEST": "LKDF-QA-002", "SCENARIO": "LKDF-QA-003", "POM": "LKDF-QA-005"}[info.layer]
                add(rule, "erro", info, line, owner, f"asserção em {info.layer}: {name}")
            elif info.layer == "FLOW" and norm not in safe and not norm.startswith(("runkeyword", "waituntil")):
                add("LKDF-QA-004", "erro", info, line, owner, f"keyword técnica no FLOW: {name}")
            # Seletor passado adiante (para outra keyword) é vazamento de detalhe técnico; dado de biblioteca segura
            # (ex.: chave "text=" num Create Dictionary) não é.
            if info.layer == "FLOW" and norm not in safe:
                for arg in args:
                    if SELECTOR.match(arg):
                        add("LKDF-QA-004", "erro", info, line, owner, f"seletor literal no FLOW: {arg}")
        for owner, line, kind in c.logic:
            if info.layer in ("TEST", "SCENARIO"):
                rule = "LKDF-QA-002" if info.layer == "TEST" else "LKDF-QA-003"
                add(rule, "erro", info, line, owner, f"{kind} em {info.layer}")
        if info.layer == "TEST" and info.path.suffix == ".robot":
            if not c.metadata:
                add("LKDF-QA-009", "aviso", info, 1, "", "suíte sem Metadata")
            for name, line, documented, tags in c.cases:
                if not documented:
                    add("LKDF-QA-009", "aviso", info, line, name, "caso sem [Documentation]")
                if not tags:
                    add("LKDF-QA-009", "aviso", info, line, name, "caso sem [Tags]")
                elif not any(TRACE_TAG.match(t) for t in tags):
                    add("LKDF-QA-009", "aviso", info, line, name, "caso sem identificador de rastreabilidade nas tags")
        for line, name, value in c.variables:
            literal = " ".join(value)
            locator = re.match(r"^(css|xpath|id|text)=", literal, re.IGNORECASE)
            if SECRET_VAR.match(name) and literal and not locator and not literal.startswith(("%{", "${")):
                add("LKDF-QA-010", "aviso", info, line, name, "segredo com valor literal; use %{VARIAVEL}")

    # LKDF-QA-007: ciclos no grafo de imports.
    state: dict[Path, int] = {}

    def visit(node: Path, stack: list[Path]) -> None:
        state[node] = 1
        for nxt in graph.get(node, ()):
            if state.get(nxt) == 1:
                cycle = " -> ".join(files[p].rel for p in stack[stack.index(nxt):] + [nxt])
                add("LKDF-QA-007", "erro", files[node], 1, "", f"ciclo de imports: {cycle}")
            elif state.get(nxt) is None:
                visit(nxt, stack + [nxt])
        state[node] = 2

    for node in graph:
        if state.get(node) is None:
            visit(node, [node])

    exceptions = read_exceptions(exceptions_file or root / "tools" / "lkdf_exceptions.txt")
    kept, excepted = [], []
    for f in findings:
        (excepted if any(e.covers(f) for e in exceptions) else kept).append(f)
    return kept, excepted


def read_exceptions(path: Path) -> list[Exception_]:
    result = []
    if not path.exists():
        return result
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = [p.strip() for p in line.split("|")]
        if len(parts) != 6:
            raise ValueError(f"{path}:{number}: exceção precisa de 6 campos (regra|arquivo|keyword|motivo|responsável|data)")
        until = date.fromisoformat(parts[5])
        result.append(Exception_(parts[0], parts[1], parts[2], parts[3], parts[4], until))
    return result


def main(argv: list[str] | None = None) -> int:
    argv = argv if argv is not None else sys.argv[1:]
    root = Path(argv[0]).resolve() if argv else DEFAULT_ROOT
    findings, excepted = lint(root)
    errors = [f for f in findings if f.severity == "erro"]
    warnings = [f for f in findings if f.severity == "aviso"]
    for f in sorted(findings, key=lambda f: (f.severity != "erro", f.file, f.line)):
        print(f)
    for f in excepted:
        print(f"EXCEÇÃO {f.rule}  {f.file}:{f.line}  [{f.keyword or '-'}]  {f.message}")
    print(f"\nLKDF: {len(errors)} erro(s), {len(warnings)} aviso(s), {len(excepted)} exceção(ões) registrada(s).")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
