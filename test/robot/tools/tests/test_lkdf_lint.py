"""Testes do validador LKDF (PROP-003, Fase 1): cada regra bloqueante pega uma violação feita de propósito, e um
projeto que segue as camadas passa limpo.

Uso: python -m unittest discover -s test/robot/tools/tests
"""

import sys
import tempfile
import textwrap
import unittest
from datetime import date
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import lkdf_lint  # noqa: E402

CLEAN = {
    "api/test/demo/demo.robot": """
        *** Settings ***
        Resource    ../../src/scenario/demo_scenario.resource
        Metadata    Suite    Demo

        *** Test Cases ***
        CT-001 - PRD-RF-001: Demo
            [Documentation]    Caso de exemplo.
            [Tags]    smoke    PRD-RF-001
            DEMO - CENARIO
        """,
    "api/src/scenario/demo_scenario.resource": """
        *** Settings ***
        Resource    ../flow/demo_flow.resource

        *** Keywords ***
        DEMO - CENARIO
            Demo - Fluxo
        """,
    "api/src/flow/demo_flow.resource": """
        *** Settings ***
        Resource    ../pom/demo_pom.resource

        *** Keywords ***
        Demo - Fluxo
            ${status}=    GET Demo
            Should Be Equal As Integers    ${status}    200
        """,
    "api/src/pom/demo_pom.resource": """
        *** Settings ***
        Resource    ../resource/demo_locator.resource

        *** Keywords ***
        GET Demo
            RETURN    200
        """,
    "api/src/resource/demo_locator.resource": """
        *** Variables ***
        ${DEMO_PATH}    /demo
        """,
}


def build(files: dict) -> Path:
    root = Path(tempfile.mkdtemp())
    for rel, text in files.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(textwrap.dedent(text).lstrip(), encoding="utf-8")
    return root


def rules(files: dict, exceptions: str | None = None) -> set:
    root = build(files)
    exc = root / "tools" / "lkdf_exceptions.txt"
    if exceptions:
        exc.parent.mkdir(parents=True, exist_ok=True)
        exc.write_text(exceptions, encoding="utf-8")
    findings, _ = lkdf_lint.lint(root, exc)
    return {f.rule for f in findings if f.severity == "erro"}


def with_change(rel: str, text: str) -> dict:
    files = dict(CLEAN)
    files[rel] = text
    return files


class LkdfLintTests(unittest.TestCase):
    def test_projeto_limpo_passa(self):
        root = build(CLEAN)
        findings, _ = lkdf_lint.lint(root, root / "nao-existe.txt")
        self.assertEqual([], findings)

    def test_001_test_chama_flow_pulando_scenario(self):
        files = with_change(
            "api/test/demo/demo.robot",
            """
            *** Settings ***
            Resource    ../../src/flow/demo_flow.resource
            Metadata    Suite    Demo

            *** Test Cases ***
            CT-001 - PRD-RF-001: Demo
                [Documentation]    x
                [Tags]    PRD-RF-001
                Demo - Fluxo
            """,
        )
        self.assertIn("LKDF-QA-001", rules(files))

    def test_002_asserção_no_test(self):
        files = with_change(
            "api/test/demo/demo.robot",
            """
            *** Settings ***
            Resource    ../../src/scenario/demo_scenario.resource
            Metadata    Suite    Demo

            *** Test Cases ***
            CT-001 - PRD-RF-001: Demo
                [Documentation]    x
                [Tags]    PRD-RF-001
                DEMO - CENARIO
                Should Be True    ${TRUE}
            """,
        )
        self.assertIn("LKDF-QA-002", rules(files))

    def test_003_scenario_chama_pom(self):
        files = with_change(
            "api/src/scenario/demo_scenario.resource",
            """
            *** Settings ***
            Resource    ../flow/demo_flow.resource

            *** Keywords ***
            DEMO - CENARIO
                GET Demo
            """,
        )
        self.assertIn("LKDF-QA-003", rules(files))

    def test_003_if_no_scenario(self):
        files = with_change(
            "api/src/scenario/demo_scenario.resource",
            """
            *** Settings ***
            Resource    ../flow/demo_flow.resource

            *** Keywords ***
            DEMO - CENARIO
                IF    ${TRUE}
                    Demo - Fluxo
                END
            """,
        )
        self.assertIn("LKDF-QA-003", rules(files))

    def test_004_keyword_técnica_e_seletor_no_flow(self):
        files = with_change(
            "api/src/flow/demo_flow.resource",
            """
            *** Settings ***
            Resource    ../pom/demo_pom.resource

            *** Keywords ***
            Demo - Fluxo
                GET On Session    demo    /api
                GET Demo    css=.botao
            """,
        )
        self.assertIn("LKDF-QA-004", rules(files))

    def test_004_chave_de_dicionário_não_é_seletor(self):
        files = with_change(
            "api/src/flow/demo_flow.resource",
            """
            *** Settings ***
            Resource    ../pom/demo_pom.resource

            *** Keywords ***
            Demo - Fluxo
                ${d}=    Create Dictionary    text=x
                GET Demo
            """,
        )
        self.assertNotIn("LKDF-QA-004", rules(files))

    def test_005_asserção_no_pom(self):
        files = with_change(
            "api/src/pom/demo_pom.resource",
            """
            *** Keywords ***
            GET Demo
                Should Be True    ${TRUE}
                RETURN    200
            """,
        )
        self.assertIn("LKDF-QA-005", rules(files))

    def test_007_ciclo_de_imports(self):
        files = dict(CLEAN)
        files["api/src/resource/a.resource"] = "*** Settings ***\nResource    b.resource\n"
        files["api/src/resource/b.resource"] = "*** Settings ***\nResource    a.resource\n"
        self.assertIn("LKDF-QA-007", rules(files))

    def test_008_api_importa_ui(self):
        files = dict(CLEAN)
        files["ui/src/resource/ui_locator.resource"] = "*** Variables ***\n${X}    css=.x\n"
        files["api/src/pom/demo_pom.resource"] = """
            *** Settings ***
            Resource    ../../../ui/src/resource/ui_locator.resource

            *** Keywords ***
            GET Demo
                RETURN    200
            """
        self.assertIn("LKDF-QA-008", rules(files))

    def test_010_caminho_absoluto_local(self):
        files = dict(CLEAN)
        files["api/src/resource/demo_locator.resource"] = "*** Variables ***\n${DADOS}    C:\\\\Users\\\\fulano\\\\dados.json\n"
        self.assertIn("LKDF-QA-010", rules(files))

    def test_exceção_vigente_cobre_e_vencida_não(self):
        files = with_change(
            "api/src/pom/demo_pom.resource",
            """
            *** Keywords ***
            GET Demo
                Should Be True    ${TRUE}
                RETURN    200
            """,
        )
        line = "LKDF-QA-005 | api/src/pom/demo_pom.resource | GET Demo | motivo | QA | {}\n"
        self.assertNotIn("LKDF-QA-005", rules(files, line.format("2999-12-31")))
        self.assertIn("LKDF-QA-005", rules(files, line.format("2000-01-01")))

    def test_exceção_incompleta_é_recusada(self):
        root = build(CLEAN)
        exc = root / "exc.txt"
        exc.write_text("LKDF-QA-005 | arquivo | sem motivo\n", encoding="utf-8")
        with self.assertRaises(ValueError):
            lkdf_lint.lint(root, exc)

    def test_validador_sobre_o_projeto_real_não_tem_erros(self):
        findings, _ = lkdf_lint.lint()
        self.assertEqual([], [str(f) for f in findings if f.severity == "erro"])


if __name__ == "__main__":
    unittest.main()
