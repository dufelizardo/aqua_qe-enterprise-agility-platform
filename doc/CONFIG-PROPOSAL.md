# Proposta 05: Configuration Center, modelos de trabalho e configuração por contexto

| | |
|---|---|
| **Versão** | 1.0 |
| **Data** | 2026-11-20 |
| **Status** | PROPOSTO, sem código nesta etapa. Três decisões tomadas (seção 3); as demais estão abertas. |
| **Série** | 01: [Evolução](EVOLUTION-PROPOSAL.md) · 02: [Experiência corporativa](EXPERIENCE-PROPOSAL.md) · 03: [Administração](ADMIN-PROPOSAL.md) · 04: [Quadros](BOARDS-PROPOSAL.md) · 05: esta |
| **Documentos-base** | [PRD v4.0](PRD.md), [SAD v1.0](SAD.md), [SSD v1.0](SSD.md), [UX/UI v1.0](UX-UI.md) |

A proposta organiza a configuração do trabalho em três níveis: organização, contexto de trabalho e pessoa. Os temas são:
- **modelos de trabalho** (Kanban, Scrum, Híbrido e personalizados);
- **templates de quadro**;
- **herança** com parâmetros bloqueados, personalizáveis ou independentes;
- **versionamento e publicação**;
- seis papéis de configuração.

O texto original foi recebido em 2026-11-20 e está resumido na seção 1. As seções seguintes o confrontam com o que já existe e registram as decisões.

---

## 1. Proposta original (resumo)

- **Separação de conceitos:** modelo de trabalho (como o contexto se organiza), workflow (estados e transições), template de quadro (configuração inicial), quadro (apresentação e operação), contexto de trabalho e preferências do usuário.
- **Níveis:**
  - a organização define modelos, templates, workflows reutilizáveis, WIP padrão e políticas;
  - o contexto escolhe o modelo e o workflow, cria quadros e configura colunas, WIP, swimlanes e cartões;
  - a pessoa salva visões, filtros e campos dos cartões.
- **Herança:** herdado e bloqueado, herdado e personalizável, ou independente. Atualizações de modelo com diferença entre versões, avaliação de compatibilidade e adoção controlada, **sem sobrescrever em silêncio**.
- **Versionamento:** estados Draft, Published, Deprecated e Archived, com histórico e publicação auditável.
- **Papéis:** Organization, Configuration, Workspace e Board Administrator, Board Member e Viewer.
- **Requisitos:** CFG-001 a CFG-020.
- **Decisões pendentes no original:** DEC-01 a DEC-12.

O princípio que resume a proposta: **"a organização define os padrões; o contexto configura a operação; o usuário personaliza a experiência"**.

## 2. Confronto com o que já existe

| Tema | O que já existe | Avaliação |
|---|---|---|
| Configuration Center | PRD: Administration › Configuration Center (UI-25) e os designers UI-26 a 32 (domínio D10). [SAD 5.2](SAD.md#52-módulos-das-fases-seguintes): `configuration` no MVP com tipos, workflows e templates padrão. | **Já previsto.** A estrutura proposta é a das UI-25 a 32; não há um segundo centro. |
| Separação modelo × workflow × quadro | Já é a arquitetura: workflow no `configuration`, quadro no `board` ([SSD 3.9](SSD.md#39-board)) e as regras nos guardas de transição. | **Já implementado.** |
| Princípio dos três níveis | [UX/UI 7.11](UX-UI.md#711-níveis-de-configuração) (Proposta 03). | **Já adotado.** |
| "Workspace" como contexto de trabalho | O contexto do AQuA-QE é a **unidade organizacional** (Squad, Cadeia, Portfólio): quadros por unidade e nível, e workflow por unidade (`workflow.org_unit_id`, [SSD 9](SSD.md#9-esquema-físico)). | "Configuração do workspace" passa a **configuração da unidade**. Sem entidade nova ([EXP-09](EXPERIENCE-PROPOSAL.md#4-decisões-tomadas); [Proposta 04](BOARDS-PROPOSAL.md#2-confronto-com-o-que-já-existe)). |
| Modelo de trabalho como entidade | Implícito: quadro configurável (colunas, WIP e fases) + sprint opcional por squad (PRD-RF-015; SSD 3.9: "squads em fluxo contínuo simplesmente não criam sprints"). | Decidido: implícito no MVP (MDL-01). |
| Templates de quadro (CFG-006) | O quadro novo nasce das colunas do workflow do nível. | **Novo.** Decidido: aplicado por cópia (MDL-07). |
| Herança (CFG-010, 011) | RF-CE04 a 10: herança, publicação e rollback, **Fase 3** ([SAD 5.2](SAD.md#52-módulos-das-fases-seguintes); faseamento do PRD). | **Já previsto para a Fase 3.** Os três comportamentos detalham o RF-CE e ficam como insumo. |
| Versionamento e publicação (CFG-012, 013, 015) | SSD 3.3: versões de workflow e de template. [REV-06](REVIEW-01.md#rev-06--publicação-de-nova-versão-de-workflow) aberto e divergência D-01 da [proposta 01](EVOLUTION-PROPOSAL.md): o SSD diz que os artefatos **passam** à nova versão; a F05 diz que **ficam** na original. | **Com decisão aberta.** "Não sobrescrever em silêncio" reforça a F05/D-01; nota registrada no REV-06. |
| Papéis de configuração | [ADM-02](ADMIN-PROPOSAL.md#4-decisões-tomadas): um único `PLATFORM_ADMIN` com escopo por unidade; "gerir quadros" para ADMIN, PM e TL (SSD 2). | Sem papéis novos: Organization e Configuration Admin = `PLATFORM_ADMIN`; Workspace e Board Admin = gerir quadros na unidade; Member e Viewer = papéis de negócio. |
| Vários quadros por contexto (CFG-019) | A API já aceita vários quadros por unidade e nível; a tela mostra o primeiro. | Escolher o quadro na tela fica para depois. |
| Preferências pessoais (CFG-020) | REV-10. | Já previsto. |
| Responsável padrão configurável | Issue #43 (decidido configurável em 2026-11-20). | É configuração organizacional por tipo e unidade; entra no mesmo desenho. |
| Fluxos próprios por equipe (Sustentação, QA) | Workflows padrão (SSD 8.2), só da raiz e só leitura. | Dependem do **Workflow Designer (UI-26)**, que edita o workflow por unidade. É o que destrava "cada equipe com seu fluxo". **Implementado em 2026-11-27 (#49):** workflow próprio por unidade, herdado pelas de baixo, com versões e migração explícita ([SSD 3.3](SSD.md#33-configuration)). |

**Ajustes de forma**
- **IDs:** DEC-01 a DEC-12 passam a **MDL-01 a MDL-12**. CFG-001 a CFG-020 mantêm o número.
- **"Project":** o original já desaconselha criar a entidade, em linha com [EXP-06](EXPERIENCE-PROPOSAL.md#4-decisões-tomadas).

## 3. Decisões tomadas (2026-11-20)

| ID | Tema | Decisão |
|---|---|---|
| MDL-01 | Modelo de trabalho como entidade | **Implícito no MVP.** Kanban = quadro sem sprint; Scrum = o mesmo quadro com sprint (tela Squad). A entidade fica para o Customization Engine (Fase 3), se ainda fizer falta. |
| MDL-07 | Atualização de modelos e templates | Templates de quadro são aplicados **por cópia** ao criar o quadro; mudar o template não altera quadros existentes. A herança viva fica para a Fase 3. |
| MDL-08 | Modelos disponíveis no início | Kanban e Scrum, pelo quadro configurável e pela sprint opcional. Híbrido = quadro com WIP numa squad que usa sprint. |

**Ordem do backlog:** primeiro as telas Tactical e Squad (MVP), depois o Workflow Designer (UI-26) e os templates de quadro.

## 4. Decisões abertas

| ID | Tema | Recomendação |
|---|---|---|
| MDL-02 | Que contextos usam cada modelo | Qualquer unidade de FL1 pode usar sprint; nos demais níveis, fluxo contínuo. |
| MDL-03 | Contexto com vários modelos | Não. O modelo é o quadro mais o uso de sprint da unidade. |
| MDL-04 | Vários quadros por contexto | Sim (a API já aceita); falta a escolha na tela. |
| MDL-05 | O que se personaliza localmente | Colunas, WIP e nome do quadro (já hoje); o workflow por unidade, com o Workflow Designer. |
| MDL-06 | Como implementar a herança | Fase 3 (RF-CE04 a 10), usando os três comportamentos do original como insumo. |
| MDL-09 | Representação do híbrido | Coberta pela MDL-08; regras próprias só se aparecer necessidade. |
| MDL-10 | Configurações incompatíveis | Mesma regra do quadro: validar antes de salvar e avisar quando um status fica sem coluna. |
| MDL-11 | Modelos × Flight Levels | Um quadro é por nível (SSD 3.9); o template guarda o nível a que se aplica. |
| MDL-12 | Quem publica modelos corporativos | `PLATFORM_ADMIN` na raiz (ADM-02). |

## 5. Requisitos CFG: situação

| ID | Requisito | Situação |
|---|---|---|
| CFG-001, 002 | Modelos de trabalho e Kanban | Implícitos (quadro configurável) |
| CFG-003, 004 | Scrum e híbrido | Scrum com a tela Squad (MVP); híbrido = MDL-08 |
| CFG-005 | Modelos personalizados | Fase 3 |
| CFG-006 | Templates de quadro | Decidido (cópia); backlog depois das telas Tactical e Squad |
| CFG-007, 008, 009 | Associar ao contexto, configurar e personalizar | Quadro por unidade e nível, configurável (já implementado) |
| CFG-010, 011 | Herdado bloqueado ou personalizável | Fase 3 |
| CFG-012 a 015, 018 | Versionar, publicar, consultar uso, diferenças e compatibilidade | REV-06 / D-01 e Fase 3 |
| CFG-016 | Permissões de configuração | Gerir quadros e `PLATFORM_ADMIN` com escopo (já implementado) |
| CFG-017 | Registrar alterações administrativas | Com os módulos `events` e `audit` (REV-03) |
| CFG-019 | Vários quadros por contexto | API pronta; tela depois |
| CFG-020 | Preferências pessoais | REV-10 |

<!-- aqeap-configpr-8308dfac0a -->
