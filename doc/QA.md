# Plano de QA — AQuA-QE Enterprise Agility Platform (MVP)

| | |
|---|---|
| **Versão** | 1.1 |
| **Data** | 2026-11-24 |
| **Status** | Em uso. Reconciliado com o código e o pipeline na Fase 0 da [PROP-003](propostas/PROP-003-evolucao-da-qualidade-e-automacao-de-testes.md). |
| **Documentos-base** | [PRD v4.0](PRD.md), [SAD v1.0](SAD.md), [SSD v1.0](SSD.md), [UX/UI v1.0](UX-UI.md), [Revisão 01](REVIEW-01.md), [Proposta de evolução](EVOLUTION-PROPOSAL.md), [PROP-003](propostas/PROP-003-evolucao-da-qualidade-e-automacao-de-testes.md) |

Este documento define a estratégia de testes do MVP: níveis, ferramentas, arquitetura da automação, convenções, rastreabilidade, ambiente e critérios de entrada e saída. Os testes de aceitação de API e de interface usam **Robot Framework** organizado pelo **Layered Keyword-Driven Framework (LKDF)** ([dufelizardo/Layered-Keyword-Driven-Framework-LKDF](https://github.com/dufelizardo/Layered-Keyword-Driven-Framework-LKDF)), o mesmo padrão já aplicado no projeto Mais Saúde Pública.

**Situação (2026-11-24; [PROP-003](propostas/PROP-003-evolucao-da-qualidade-e-automacao-de-testes.md), Fase 0).** O plano está em uso. Todos os níveis da [seção 2](#2-níveis-de-teste-e-ferramentas) existem e rodam no pipeline:
- JUnit com Testcontainers e Spring Modulith (`src/test`);
- Vitest no frontend;
- Robot de API e de interface (`test/robot`).

A baseline da automação Robot não é mantida à mão: o `test/robot/tools/inventory.py` a calcula a cada execução e publica `inventory.md` como artefato do pipeline. Em 2026-11-24 eram **14 suítes e 51 casos** (9 de API e 5 de interface), todos com identificador de rastreabilidade. As camadas LKDF são verificadas automaticamente ([seção 3.4](#34-conformidade-verificada-guardrail-lkdf)).

Esses números são uma fotografia, não uma medida de cobertura. A matriz de cobertura por domínio e a de autorização são as Fases 2 e 3 da PROP-003.

---

## 1. Objetivo e escopo

**Em escopo.**
- Os requisitos do MVP listados no [SAD, seção 14](SAD.md#14-rastreabilidade--requisitos-do-mvp) (PRD-RF-001 a PRD-RF-038, recorte do MVP).
- Os contratos do [SSD](SSD.md): endpoints, códigos de erro, regras de gate, formato dos documentos, eventos.
- As telas e jornadas da [UX/UI](UX-UI.md).
- Os critérios de aceite das frentes da [Proposta de evolução](EVOLUTION-PROPOSAL.md), à medida que forem aprovadas.
- Os achados da [Revisão 01](REVIEW-01.md) que pedem teste: REV-01, REV-02, REV-03, REV-06 e REV-13.

**Fora de escopo neste momento.**
- Requisitos das Fases 2 e 3.
- Testes de desempenho e carga. As metas p95/p99 da F04 e os volumes ainda estão em aberto ([seção 11](#11-pendências)).
- Testes de instalação air-gapped (Fase 3).

## 2. Níveis de teste e ferramentas

| Nível | O que verifica | Ferramenta | Onde fica | Executa contra |
|---|---|---|---|---|
| Unidade | Regras de domínio isoladas: avaliação de gate, cálculo de hash, validação de hierarquia. | JUnit 5 | `src/test` | Código, em processo. |
| Integração | Repositórios, transação com outbox, migrações. | Spring Boot Test + Testcontainers (PostgreSQL) | `src/test` | Banco real efêmero. |
| Fronteiras de módulo | Nenhum módulo acessa internals de outro (ADR-04). | Spring Modulith `ApplicationModules.verify()` | `src/test` | Código, no build. |
| **Aceitação de API** | Contratos do SSD, autorização, gates, auditoria, de fora para dentro. | **Robot Framework + RequestsLibrary**, padrão LKDF | `test/robot/api` | Aplicação de pé, via HTTP. |
| **Jornadas e interface** | Fluxos da UX/UI e comportamento das telas. | **Robot Framework + Browser** (Playwright), padrão LKDF | `test/robot/ui` | Frontend e API de pé, no navegador. |
| Unidade do frontend | Componentes, serviços e funções puras do Angular. | Vitest (Angular CLI); no Windows, roda em contêiner `node:24-alpine` | `frontend/src/**/*.spec.ts` | Código, em processo. |

Os testes Robot **complementam** os testes JUnit, sem substituí-los. JUnit roda no build e dá retorno rápido. Robot roda contra a aplicação real e verifica o comportamento que o usuário e os integradores enxergam.

**Jornadas (UX) e interface (UI).** As duas usam a biblioteca Browser:
- As **jornadas** percorrem os fluxos da [UX/UI, seção 8](UX-UI.md#8-fluxos-de-interação) de ponta a ponta.
- Os testes de **interface** verificam telas isoladas: estados da [seção 6.10](UX-UI.md#610-estados-comuns), mensagens da [seção 9](UX-UI.md#9-mensagens-de-erro) e ações ocultas ou desabilitadas por permissão.

## 3. Arquitetura da automação (LKDF)

### 3.1 Camadas

Dependência estritamente unidirecional: `TEST` → `SCENARIO` → `FLOW` → `POM`. Nenhuma camada pula a de baixo nem importa a de cima.

| Camada | Responsabilidade | Não pode conter |
|---|---|---|
| **POM** (`src/pom/`) | API: chamada HTTP crua (`GET On Session`, `POST On Session`…). UI: ação sobre a página (`Click`, `Fill Text`, `Get Text`) usando locators do `resource`. | Asserção, regra de negócio, decisão condicional. |
| **FLOW** (`src/flow/`) | Monta o payload, escolhe o caminho pelo status esperado e faz as asserções: status, schema, conteúdo. | URL ou endpoint cru, chamada HTTP direta, seletor de UI. |
| **SCENARIO** (`src/scenario/`) | Roteiro declarativo da jornada; só repassa ao FLOW. | Dado fixo, asserção. |
| **TEST** (`test/`) | Casos de teste (`*.robot`); injeta os dados de entrada (persona, status esperado). | Lógica condicional, asserção direta. |

`src/resource/` reúne agregadores e dados e é importado pelas camadas, nunca o contrário:

| Pasta | Conteúdo |
|---|---|
| `config/driven`, `config/schema` | Agregadores únicos. |
| `data_driven/<modulo>` | Dicionários de casos negativos. |
| `schema/<modulo>` | Schemas de resposta. |
| `locators/<tela>` | Seletores de UI (só em `ui/`). |

O exemplo de UI do repositório LKDF (`adopet_ui`) tem asserções e ramificação dentro da POM. **Esse desvio não deve ser repetido aqui**: na POM de UI, `Get Text` devolve o valor e quem faz a asserção é o FLOW.

Abrir e fechar o navegador também passa pelo FLOW: os cenários chamam `Navegador - Abrir`, `Navegador - Abrir Celular` e `Navegador - Fechar` (`ui/src/flow/common/browser_flow.resource`), e não o POM `aquaqe_ui_common`.

### 3.2 Estrutura de pastas

Uma pasta por módulo do backend ([SAD, seção 5.1](SAD.md#51-módulos-do-mvp)) e uma subpasta por operação. A estrutura real em 2026-11-24:

```text
test/robot/
├── requirements.txt                    versões fixadas (robotframework, requests, browser)
├── run_tests.sh / run_tests.ps1        escrevem em ./results/; aceitam --dryrun
├── ci_run.sh / flaky.py                aceitação no CI e registro de instáveis (ADR-18)
├── tools/                              PROP-003: inventory.py, lkdf_lint.py, lkdf_exceptions.txt, tests/
├── api/
│   ├── src/{pom,flow,scenario}/<modulo>/<operacao>/
│   │      módulos: actuator, audit, board, identity, knowledge, organization, workflow, workitem; pom/common
│   │      (sessão HTTP e token por persona)
│   ├── src/resource/{config,data_driven}/
│   └── test/<modulo>/<operacao>/*.robot
└── ui/
    ├── src/{pom,flow,scenario}/<tela>/  telas: admin, login, navigation, portfolio, wiki; pom/common e
    │                                    flow/common (navegador)
    ├── src/resource/{data_driven,locators}/<tela>/
    └── test/<jornada>/*.robot
```

### 3.3 Exemplo: transição bloqueada por gate (SSD, seção 4.2)

```robotframework
# test/workflow/transition_artifact/transition_artifact.robot
CT-003 - PRD-RF-022: Validate POST Transition - HTTP 422 GATE_BLOCKED (campo obrigatório vazio)
    [Tags]    POST    TransitionArtifact    HTTP422    PRD-RF-022    F02
    WORKFLOW - TRANSITION - POST    PRODUCT_MANAGER    422

# src/flow/workflow/transition_artifact/transition_artifact_flow.resource
POST Transition - Status Code 422 - Gate Blocked
    [Arguments]    ${persona}
    ${feature}=    Seed A Feature Without Owner    ${persona}
    ${response}=    POST Transition Artifact    ${persona}    ${feature}    READY
    Should Be Equal As Integers    ${response.status_code}    422
    Validate Error Code    ${response.json()}    GATE_BLOCKED
    Validate Failed Rule    ${response.json()}    REQUIRED_FIELD    ownerId
```

### 3.4 Conformidade verificada (guardrail LKDF)

As regras das camadas não dependem só da revisão: o `test/robot/tools/lkdf_lint.py` as verifica a cada push, pelo parser do Robot Framework, sem rodar as suítes ([PROP-003](propostas/PROP-003-evolucao-da-qualidade-e-automacao-de-testes.md), Fase 1).

| Regra | O que bloqueia |
|---|---|
| LKDF-QA-001 / 007 | Import ou chamada para cima, ou pulando camada (TEST→SCENARIO→FLOW→POM; RESOURCE em qualquer uma; FLOW→FLOW e POM→POM permitidos); ciclo de imports. |
| LKDF-QA-002 | Asserção (`Should …`) ou `IF`/`FOR` no TEST. |
| LKDF-QA-003 | Asserção ou `IF`/`FOR` no SCENARIO; SCENARIO chamando POM. |
| LKDF-QA-004 | FLOW usando keyword técnica (Browser, RequestsLibrary) ou passando seletor literal (`css=`, `xpath=`). O FLOW só usa BuiltIn, Collections, String, DateTime e keywords do projeto. |
| LKDF-QA-005 | Asserção na POM. |
| LKDF-QA-008 | API importando recurso de UI, ou o contrário. |
| LKDF-QA-010 | Caminho absoluto de máquina local. |

**Avisos (não bloqueiam):** caso sem `[Documentation]`, `[Tags]` ou identificador de rastreabilidade; suíte sem `Metadata`; variável de senha ou token com valor literal (LKDF-QA-009 e 010).

**Exceções.** Ficam em `test/robot/tools/lkdf_exceptions.txt`: regra, arquivo, keyword, motivo, responsável e data de revisão. Exceção vencida deixa de valer. Hoje há uma: o token inválido de propósito do caso 401 de `identity`.

**Executar localmente:**

```bash
python test/robot/tools/lkdf_lint.py                          # conformidade (sai com 1 se houver erro)
python -m unittest discover -s test/robot/tools/tests         # testes do próprio validador
python test/robot/tools/inventory.py                          # inventário em test/robot/results/
```

## 4. Convenções

Herdadas do Mais Saúde Pública, onde já foram validadas na prática:

- **Variáveis de POM prefixadas pelo módulo** (`${WORKITEM_ENDPOINT}`, `${GOVERNANCE_ENDPOINT}`). Variáveis declaradas em `*** Variables ***` de um resource são **globais ao processo**. Um `${ENDPOINT}` genérico faz testes de um módulo chamarem a URL de outro, sem erro aparente.
- **Nome do caso:** `CT-NNN - <ID do requisito>: Validate <MÉTODO> <Operação> - HTTP <código> <MOTIVO>`, numerado por suíte.
- **Tags:** método HTTP, operação, `HTTP<código>`, mais as tags de rastreabilidade da [seção 5](#5-rastreabilidade).
- **Metadata da suíte:** título, descrição, responsável, versão, datas, projeto.
- **`[Documentation]`** em todo keyword e todo caso.
- **Schemas** no formato `{required, types, enums}`, validados por `Validate Object Schema` e `Validate Array Schema` em `flow/common`. O schema de erro segue o [SSD, seção 1.6](SSD.md#16-erros).
- **Dados de seed com sufixo aleatório**, para que execuções repetidas não colidam com restrições de unicidade.
- **Verificar antes de afirmar.** Antes de escrever um teste negativo, confirmar contra a aplicação real que o código é alcançável. Não confiar só na documentação OpenAPI.
- **Todo endpoint novo exige cobertura Robot** na mesma entrega, não como tarefa posterior. Mínimo:
  - sucesso;
  - `400 VALIDATION_FAILED`;
  - `404 NOT_FOUND`;
  - `403 FORBIDDEN` com uma persona sem permissão.

  No Mais Saúde Pública essa regra não foi seguida no início e domínios inteiros ficaram sem cobertura.
- **404 também significa "fora do escopo".** O SSD devolve `404 NOT_FOUND` para recurso inexistente **ou** invisível ao usuário ([SSD, seção 1.6](SSD.md#16-erros)), e `403` só quando o usuário vê o recurso mas não pode agir. Os testes de isolamento esperam 404, não 403.

## 5. Rastreabilidade

Cada caso de teste é ligado ao que ele verifica de duas formas.

**Pelo nome do caso:** o identificador do requisito principal (`PRD-RF-022`). Quando o trabalho passar a ser registrado em Jira ou na própria plataforma, o identificador da história passa a ser usado, como no Mais Saúde Pública.

**Pelas tags:**

| Tag | Exemplo | Origem |
|---|---|---|
| Requisito | `PRD-RF-024` | [PRD](PRD.md) |
| Frente de evolução | `F01` | [Proposta de evolução](EVOLUTION-PROPOSAL.md) |
| Achado | `REV-01` | [Revisão 01](REVIEW-01.md) |
| Segurança | `ASVS-V4` | OWASP ASVS, capítulo verificado (REV-13) |
| Tipo | `smoke`, `regression`, `security` | Seleção de execução |

A **matriz de rastreabilidade** (requisito → suítes → casos → último resultado) é gerada a partir das tags do `output.xml` de cada execução. Ela alimenta o entregável da F06. Um requisito do MVP sem nenhum caso marcado aparece como lacuna.

## 6. Autenticação nos testes

- Um **realm Keycloak de teste**, importado no ambiente, com um usuário por papel do [SSD, seção 2](SSD.md#2-papéis-e-permissões): `PLATFORM_ADMIN`, `BUSINESS_OWNER`, `PRODUCT_MANAGER`, `TECH_LEAD`, `UX_DESIGNER`, `QA_ENGINEER`, `SQUAD_MEMBER`, `VIEWER`.
- Usuários adicionais por **relação organizacional**: papel na unidade do artefato, em unidade ancestral, em outro ramo com vínculo, em outro ramo sem vínculo; mais um usuário desativado (F07).
- A POM `common` obtém o token por OIDC e abre **uma sessão por persona**. O FLOW escolhe a persona; o TEST só a informa.
- As credenciais vêm de variáveis de ambiente, nunca do repositório.
- MFA fica desligado no realm de teste. O fluxo com MFA é verificado em um caso de UI à parte (PRD-RF-037).

## 7. Suítes prioritárias do MVP

| Suíte | Verifica | Base | Tags |
|---|---|---|---|
| **Autorização e isolamento** | Data-driven sobre a matriz papel × relação organizacional × operação, positivos e negativos, em detalhe, listagem, busca, contexto herdado e anexos. Regras, matriz por relação e cenários C1 a C4 no [SSD, seção 2.1](SSD.md#21-escopo-entre-unidades-rev-01-rev-02-rev-04). | [SSD, seção 2](SSD.md#2-papéis-e-permissões) | `REV-01`, `REV-02`, `F01`, `F08a`, `security` |
| **Gates** | Hoje: JUnit `WorkflowIntegrationTests` (transições, histórico, `409`, `422`, `403`, `404` e `GATE_BLOCKED` com uma guarda de teste) e Robot `test/robot/api/test/workflow` (transições possíveis, executar com histórico, `409` e `422`). Os seis tipos de regra: `REQUIRED_FIELD`, `DOCUMENT_SECTION_FILLED`, `DOCUMENT_APPROVED`, `MIN_CHILDREN`, `BUSINESS_CASE_VALIDATED`, `ROLE_APPROVAL`. Retorno `GATE_BLOCKED` com `failedRules`; transição liberada depois de corrigir. | [SSD, seção 5](SSD.md#5-regras-de-gate), [4.2](SSD.md#42-transição-com-gate) | `PRD-RF-022` a `024`, `F02` |
| **Aprovações** | Aprovação e rejeição por papel; `APPROVER_ROLE_REQUIRED`; histórico com substituídas; segregação de funções, quando a F02 for aprovada. | [SSD, seção 4.3](SSD.md#43-aprovação-formal-de-um-gate) | `PRD-RF-024`, `F02`, `REV-09` |
| **Documentos** | Salvar nova versão, `VERSION_CONFLICT`, `DOCUMENT_NOT_EDITABLE`, aprovar e reabrir, seções obrigatórias do template. Hoje (#63): JUnit `ArtifactDocumentsIntegrationTests` (os cinco nascem com a Iniciativa e a Feature, Sustentação sem documentos, salvar, conflito, `DOCUMENT_INCOMPLETE`, `DOCUMENT_NOT_EDITABLE`, aprovar por papel, reabrir com comentário, leitura sem edição, eventos), Vitest `artifact-documents.spec.ts`, Robot API `test/robot/api/test/knowledge/documents` e Robot UI `portfolio.robot` CT-005. | [SSD, seções 4.4](SSD.md#44-salvar-documento-com-conflito) e [6](SSD.md#6-formato-dos-documentos) | `PRD-RF-018` a `020`, `REV-05` |
| **Edição do artefato** | Editar título, descrição, estimativa e pessoas na página do artefato, `canEdit`, busca de pessoas e `VERSION_CONFLICT`. Hoje: JUnit `ArtifactEditIntegrationTests` (`canEdit` de quem edita e do leitor, pessoas da linhagem e da subárvore sem outro portfólio, filtro `q` com `%` literal, 403 do leitor, 404 de quem não lê, PATCH de título e responsável, versão velha), Vitest `work.spec.ts`, Robot API `artifacts.robot` CT-008 e CT-009 e Robot UI `portfolio.robot` CT-006. | [SSD, seção 3.4](SSD.md) | `PRD-RF-010`, `PRD-RF-014` |
| **Hierarquia e artefatos** | `INVALID_PARENT_TYPE`, `ORG_UNITS_NOT_LINKED`, `HAS_CHILDREN`, contexto herdado. Hoje: JUnit `WorkitemIntegrationTests` (cenário C2 do SSD 2.1, regras 1 a 7, inclusive o isolamento) e Robot `test/robot/api/test/workitem` (criação, 403, 422, 404, workspace e arquivamento). | [SSD, seção 3.4](SSD.md#34-workitem) | `PRD-RF-010`, `017`, `031` |
| **Auditoria** | Registro presente depois de cada operação relevante, dentro do SLA definido no REV-03; `GET /audit-events/verification` sem inconsistência; reentrega de evento não duplica registro; acesso restrito a `PLATFORM_ADMIN`. Hoje: JUnit `AuditIntegrationTests` (registros encadeados, correlação, adulteração detectada, reentrega sem duplicar, transição bloqueada registrada, 403) e Robot API `test/robot/api/test/audit`. | [SSD, seção 3.10](SSD.md#310-audit-e-busca) | `PRD-RF-026`, `REV-03`, `F04` |
| **Configuração** | Publicação de versão de workflow e de template; `CONFIG_IN_USE`; comportamento dos artefatos existentes conforme a decisão D-01. | [SSD, seção 3.3](SSD.md#33-configuration) | `PRD-RF-011`, `REV-06`, `F05` |
| **Identidade** | Login, seleção de workspace, `WORKSPACE_REQUIRED`, `UNAUTHENTICATED`, usuário desativado sem acesso. | [SSD, seção 4.1](SSD.md#41-login-e-seleção-de-workspace) | `PRD-RF-036` a `038`, `F07` |
| **Jornadas (UI)** | Os cinco fluxos da [UX/UI, seção 8](UX-UI.md#8-fluxos-de-interação): mover cartão e ser bloqueado; pedir e dar aprovação; editar, aprovar e reabrir documento; conflito de edição; criar Feature a partir da Iniciativa. | UX/UI 8.1 a 8.5 | `smoke`, PRD-RF de cada fluxo |
| **Interface (UI)** | Estados comuns, mensagens de erro, ações ocultas ou desabilitadas por papel, painel de contexto herdado com ancestral sem acesso. | UX/UI [6.9](UX-UI.md#69-painel-de-contexto-herdado), [6.10](UX-UI.md#610-estados-comuns), [9](UX-UI.md#9-mensagens-de-erro) | `regression` |
| **Estrutura organizacional** | API (`test/robot/api/test/organization`): níveis, criação, nível do pai, 404, vínculos (cenário C2) e 403 para quem não gere configuração. UI (`test/robot/ui/test/admin`): criar, renomear, Cadeia compartilhada e o item Admin oculto para quem não é `PLATFORM_ADMIN`. | [SSD, seções 3.2 e 2.1](SSD.md#32-organization), UX/UI [7.10](UX-UI.md#710-administração) | `ADR-14`, `REV-01`, `REV-04` |
| **Conhecimento (Wiki)** | JUnit `KnowledgeIntegrationTests` (página e subpágina, conflito, histórico, ciclo do regimento, regra 9, 403 e 404, mover sem ciclo, arquivar, eventos), Vitest `knowledge.spec.ts` (conversão do conteúdo antigo para o editor contínuo, Markdown leve sem HTML, árvore de páginas); editor contínuo (bug #67): JUnit `editorContinuoGravaRichtext` e Robot UI (negrito pela barra); reorganização da árvore (PROP-002): JUnit `reorganizarAWikiPreservaPaginaEHistorico`, Vitest das ações e do arrastar, Robot API e UI `CT-004` e Robot UI `CT-005` (arrastar com o mouse), menu ⋯ (#81): JUnit `fazerUmaCopiaFicaLogoDepoisComOConteudoAtualSemSubpaginas`, Vitest do link e da cópia, Robot API `CT-005` (cópia) e Robot UI `CT-006` (renomear, copiar e arquivar), rascunho automático (#82): Vitest `draft-store.spec.ts` e Robot UI `CT-007` (rascunho guardado, aviso ao sair, recuperação e limpeza ao salvar), Robot API `test/robot/api/test/knowledge` e Robot UI `test/robot/ui/test/wiki`. | [Proposta 07](KNOWLEDGE-PROPOSAL.md), [SSD 3.7.1](SSD.md#371-páginas-de-conhecimento-por-unidade-proposta-07-fase-a), UX/UI [7.12](UX-UI.md#712-wiki-da-unidade) | `KNOW-006` a `KNOW-020`, `REV-05` |
| **Gates por transição (#78)** | JUnit `GateIntegrationTests`:<br>• só quem gere configuração salva o gate;<br>• regra pendente (`BUSINESS_CASE_VALIDATED`), seção inexistente, documento em fluxo sem documentos e campo inexistente dão 400;<br>• `GATE_BLOCKED` com as cinco regras, categoria, `params` e `gateName`;<br>• painel com `canDecide` por papel;<br>• `APPROVER_ROLE_REQUIRED`, `APPROVAL_NOT_APPLICABLE` e rejeição sem motivo;<br>• histórico de aprovações;<br>• reabrir o PRD volta a bloquear.<br>Vitest `gates.spec.ts`: resumo de cada regra, erro apontando a regra e chamadas da API.<br>Robot API `test/robot/api/test/governance/gates`: `CT-001` bloqueia, aprova e libera; `CT-002` regra indisponível. Os dois removem o gate no fim.<br>Robot UI `portfolio.robot` `CT-007`: Admin › Gates cria o gate, os Próximos passos bloqueiam o Mover até a aprovação, e o gate é removido no fim. | [SSD 3.6 e 5](SSD.md#5-regras-de-gate), UX/UI [6.4](UX-UI.md#64-painel-de-gates) e 7.10 | `PRD-RF-022` a `PRD-RF-024` |
| **Editor: tarefas, cor, sumário e ícone (#86)** | JUnit `KnowledgeIntegrationTests.iconeTarefasECoresDoEditor` (ícone sem versão nova, ícone inválido dá 400, lista de tarefas e cores `#rrggbb` gravadas, cor com CSS recusada), Vitest `editor.spec.ts` (títulos do sumário em ordem e em todas as seções, cores das paletas e emojis aceitos pelo servidor), Robot API `pages.robot` `CT-007` e Robot UI `wiki.robot` `CT-011` (dois títulos geram o sumário, tarefa com caixa de marcar, emoji no texto e ícone ao lado do título). | [SSD 3.7.1 e 6](SSD.md#371-páginas-de-conhecimento-por-unidade-proposta-07-fase-a), UX/UI [7.12](UX-UI.md#712-wiki-da-unidade) | `KNOW-020` |
| **Comentários, menções e favoritos (#85)** | JUnit `CommentsIntegrationTests` (comentário com trecho e menções, menção fora da página dá 400, resposta de um nível, quem não lê não comenta, notificações de menção e de resposta sem avisar o autor, marcar como lida e todas, exclusão só pelo autor e "excluído" com respostas; favoritos com 404 para quem não lê), Vitest `comments.spec.ts` (termo da @menção, marcação das menções sem HTML, menção desfeita ao apagar o nome, frase e contador do sino), Robot API `pages.robot` `CT-006` e Robot UI `wiki.robot` `CT-010` (selecionar o parágrafo, "Comentar trecho", @menção pela lista e estrela nos Favoritos da Home). | [SSD 3.7.1](SSD.md#371-páginas-de-conhecimento-por-unidade-proposta-07-fase-a), UX/UI [7.12](UX-UI.md#712-wiki-da-unidade) | `KNOW-024`, `KNOW-025` |
| **Busca (#84)** | JUnit `SearchIntegrationTests` (artefato pelo título e pela descrição sem acento, página pelo texto do editor contínuo, documento pela versão atual, quem não lê não acha, `kind`, 400 e curingas como texto), Vitest `search.spec.ts` (marcação dos termos sem acento e sem HTML, rótulo e destino de cada tipo), Robot API `test/robot/api/test/busca` (`CT-001` e `CT-002`) e Robot UI `wiki.robot` `CT-009` (achar a página pela caixa do topo, sem acento e em maiúsculas). | [SSD 3.10](SSD.md#310-audit-e-busca), UX/UI [7.9](UX-UI.md#79-resultados-de-busca) | `KNOW-014` |
| **Home: recentes e novidades (#83)** | JUnit `ActivityIntegrationTests` (ordem das visitas, título atual, 404 para quem não lê, 400 para tipo inválido; novidades por unidade lida, uma por item, `own`), Vitest `activity.spec.ts` (texto, destino e verbo dos itens), Robot API `test/robot/api/test/inicio` (`CT-001` recentes, `CT-002` novidades) e Robot UI `wiki.robot` `CT-008` (a página criada aparece nos recentes e nas novidades e o link a reabre). | [SSD 3.1](SSD.md#31-identity-e-access), UX/UI [7.2](UX-UI.md#72-home--meu-trabalho) | `HOME-001`, `HOME-002` |
| **Navegação organizacional** | Vitest `work.spec.ts` (árvore do menu com ancestrais sem papel), JUnit `OrganizationIntegrationTests` (cardinalidade 2 × 2 × 2 e "atende a" sem duplicar) e Robot UI `test/robot/ui/test/navigation` (escolher a Squad pela busca e abrir `/squad`, com as abas da página da Squad (Wiki, Regimento, Quadro, Backlog, Sprints) e o Backlog por elas; recolher um ramo e a expansão ficar lembrada; gaveta no celular). Menu contextual (bug #66): Vitest `context-nav.spec.ts`. | [Proposta 06, Revisão 2](NAVIGATION-PROPOSAL.md#6-revisão-2-proposal-07-estrutura-organizacional-como-navegação), UX/UI [4](UX-UI.md#4-estrutura-da-aplicação) | `ORG-NAV-001` a `ORG-NAV-016` |
| **Quadros e Portfolio** | JUnit `BoardIntegrationTests` (workspaces, colunas padrão, configuração e suas validações, WIP `UNDER`/`AT`/`OVER`, `BLOCK` recusando a transição, bloqueio e filtro, cartão muda de coluna com a transição, quadro vazio para quem não lê, 403 e 400), Robot API `test/robot/api/test/board` (configurar, 403, WIP impedindo) e Robot UI `test/robot/ui/test/portfolio`. Tudo nasce na Squad (bug #66): no Quadro da Squad, criar e mover Iniciativa; configurar WIP; bloquear e desbloquear; em Features, criar Feature sob a Iniciativa e movê-la; em Histórias, ciclo completo de sprint. Sprints e ordenação: JUnit `SprintIntegrationTests` e Robot API `test/robot/api/test/board/sprints`. | [SSD 3.9](SSD.md#39-board), UX/UI [7.3](UX-UI.md#73-portfolio-management-ui-04) | `PRD-RF-001`, `PRD-RF-011` |
| **Segurança de aplicação** | Itens verificáveis do OWASP ASVS nível 2 por HTTP: cabeçalhos, sessão, controle de acesso, validação de entrada, tratamento de erro sem vazamento. | REV-13 | `security`, `ASVS-*` |

## 8. Requisitos para testabilidade

O que a implementação precisa oferecer para que os testes acima sejam possíveis:

| Requisito | Para quê | Onde registrar |
|---|---|---|
| Atributo `data-testid` estável nos componentes da [UX/UI, seção 6](UX-UI.md#6-componentes-compartilhados) e nas ações das telas. | Locators de UI resistentes a mudança de layout e de texto. | UX/UI, nova convenção. |
| Identificador de correlação em toda resposta de erro. | Ligar a falha do teste ao log e à auditoria. | Já previsto na UX/UI, seção 6.10; confirmar no SSD, seção 1.6. |
| Instalação com dados de teste reproduzíveis: realm Keycloak, árvore organizacional com dois ramos e um vínculo, workflows e templates padrão. | Isolar a suíte de autorização e permitir execução repetida. | Este plano; SSD, seção 8. |
| Métrica de atraso da outbox exposta. | Verificar o SLA da auditoria sem esperas fixas. | REV-03, F04. |

## 9. Ambiente e execução

- **Local:** `docker compose` com PostgreSQL, Keycloak (com o realm de teste) e a aplicação; o MinIO entra junto com os anexos. As URLs vêm de variáveis de ambiente (`AQUA_HOST_URL`, `AQUA_AUTH_URL`, `AQUA_UI_URL`), sem porta fixa no código.
- **Dependências:** `robotframework`, `robotframework-requests` e `robotframework-browser`, com versões fixadas em `requirements.txt`. A biblioteca Browser exige `rfbrowser init` para instalar o Playwright; no CI, usar imagem com os navegadores já instalados.
- **Execução:** `run_tests.sh` / `run_tests.ps1`, com saída em `results/`. `--dryrun` faz só a checagem de sintaxe e imports. `--include smoke` seleciona por tag.
- **CI** ([`pipeline.yml`](../.github/workflows/pipeline.yml), no modelo do Mais Saúde Pública):
  1. JUnit, integração e Spring Modulith em todo push e PR.
  2. Robot `--dryrun` em todo push e PR, seguido dos testes do validador, da **conformidade LKDF (bloqueia)** e do inventário, publicado como artefato `robot-inventory` ([seção 3.4](#34-conformidade-verificada-guardrail-lkdf)).
  3. Suíte Robot completa (API e UI) nos PRs de promoção para a `main`, por [`ci_run.sh`](../test/robot/ci_run.sh). O job builda a imagem real, sobe os containers efêmeros e roda a suíte contra eles. O merge fica bloqueado se a suíte falhar.
  4. `results/` publicado como artifact, inclusive quando a suíte falha, com os logs dos containers.
- **Retentativas** (SAD, ADR-18). Cada tipo de falha tem um tratamento:
  | Falha | Tratamento |
  |---|---|
  | Rede ou serviço externo (download do Maven, imagens, Chromium, subida da pilha) | Até 3 tentativas com espera crescente. A imagem da API usa a imagem oficial do Maven, sem baixar o Maven pelo wrapper. |
  | Teste que falha uma vez | `ci_run.sh` reexecuta só os testes que falharam (`--rerunfailed`) e junta os resultados. Se passar na reexecução, o teste é **instável**: o pipeline segue, mas abre (ou comenta) uma issue com a label `teste instável`, que precisa ser corrigida. |
  | Job de aceitação inteiro | O [`retry-acceptance.yml`](../.github/workflows/retry-acceptance.yml) reexecuta o job até 2 vezes (3 tentativas), só quando ele é o único job com falha, e comenta no PR. |
  | Backend, frontend ou dry-run | Não há retentativa: é falha de código. |
- **Teste instável não é aceito como estado normal.** A issue aberta automaticamente entra no backlog com prioridade do time; se o mesmo teste voltar a oscilar, a issue recebe um comentário com o link da execução.

## 10. Critérios de entrada e saída

**Entrada** para automatizar um módulo:
- Contrato do módulo estável no SSD, sem itens "(a validar)" no que será testado.
- Decisões pendentes que afetam o módulo resolvidas: D01 para autorização, D05 para workflow, D04 para auditoria.
- Ambiente com dados de teste reproduzíveis disponível.

**Saída** de uma entrega do MVP:
- Todo requisito do MVP com pelo menos um caso Robot marcado por tag.
- Todo endpoint com sucesso, 400, 403 e 404 cobertos.
- Suíte de autorização e isolamento 100% aprovada; nenhum defeito de segurança aberto.
- Cadeia de auditoria verificada sem inconsistência depois da suíte completa.
- As cinco jornadas da UX/UI aprovadas.
- Nenhum defeito crítico ou alto aberto; os demais com decisão registrada.

## 11. Pendências

- Metas numéricas de desempenho e volume, e a ferramenta de carga, que fica fora do Robot.
- SLA de atraso da auditoria (REV-03) e decisão D-03 sobre falha persistente.
- ~~Matriz de autorização completa (REV-01)~~: decidida em 2026-11-20 ([SSD, seção 2.1](SSD.md#21-escopo-entre-unidades-rev-01-rev-02-rev-04)).
- Convenção `data-testid`, a incorporar na UX/UI.
- Capítulos do ASVS nível 2 aplicáveis e quais são verificáveis por Robot (REV-13).
- Gestão de defeitos: onde registrar e como ligar ao caso de teste, enquanto a própria plataforma não oferece gestão de testes (Fase 2).
- ~~Versões exatas das bibliotecas~~: fixadas em `test/robot/requirements.txt`.
- Próximas fases da [PROP-003](propostas/PROP-003-evolucao-da-qualidade-e-automacao-de-testes.md):
  - matriz de autorização e isolamento (Fase 2);
  - cobertura por domínio e rastreabilidade requisito → caso → execução (Fase 3);
  - indicadores de confiabilidade (Fase 4).

<!-- aqeap-qa-db5e074a82 -->
