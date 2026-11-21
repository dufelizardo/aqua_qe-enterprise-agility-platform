# Proposta 07: conhecimento organizacional, Wiki e documentação viva

| | |
|---|---|
| **Versão** | 1.0 |
| **Data** | 2026-11-20 |
| **Status** | PROPOSTO. As decisões da seção 4 foram tomadas e a **Fase A** foi implementada; as fases B a D seguem em avaliação. O MVP do PRD não muda. |
| **Série** | 01: [Evolução](EVOLUTION-PROPOSAL.md) · 02: [Experiência](EXPERIENCE-PROPOSAL.md) · 03: [Administração](ADMIN-PROPOSAL.md) · 04: [Quadros](BOARDS-PROPOSAL.md) · 05: [Configuração](CONFIG-PROPOSAL.md) · 06: [Navegação](NAVIGATION-PROPOSAL.md) · 07: esta |
| **Documentos-base** | [PRD v4.0](PRD.md), [SAD v1.0](SAD.md), [SSD v1.0](SSD.md), [UX/UI v1.0](UX-UI.md) |

A proposta, recebida como PROPOSAL-08 em 2026-11-20, amplia o domínio D04 (Living Documentation). Além dos documentos das Iniciativas e Features, a plataforma passa a guardar o **conhecimento da organização**: regimentos, políticas, processos, acordos e padrões. Cada unidade organizacional ganha seu espaço, com páginas e subpáginas, versões, um ciclo de publicação, e leitura do conhecimento publicado pelas unidades acima.

O princípio é aceito: **um só motor documental**, e não um Wiki isolado nem um segundo mecanismo de versões, busca ou auditoria.

---

## 1. O que a proposta acertou

O [SAD](SAD.md#1-direcionadores) já diz que a plataforma substitui, num só produto, "o conjunto de gestão de trabalho, **wiki de documentação** e gestão de testes". Os documentos-base, porém, só especificavam documentos presos a artefatos (SSD 3.7). Faltavam:
- espaços por unidade;
- hierarquia de páginas;
- regimentos;
- o conhecimento das unidades de cima chegando às de baixo.

A proposta preenche uma lacuna da visão, sem mudar o rumo do produto.

Também acertou ao separar **proprietário, visibilidade, aplicabilidade, edição e aprovação** (seção 9.1 do original) e ao exigir que nada seja duplicado: o documento fica na unidade dona e é lido de onde for preciso.

## 2. Confronto com o que já existe

| Tema | O que já existia | Situação |
|---|---|---|
| Motor de documentos (`livingdocs`) | Especificado (SSD 3.7, 6 e 8.4; SAD 7.6), **sem código**. | **Implementado** com dono genérico: artefato ou unidade. Seções e blocos, versões imutáveis, bloqueio otimista e templates servem aos dois. |
| Documentos de Iniciativa e Feature | MVP (PRD-RF-018 a 020). | Usarão o mesmo motor; próxima entrega (issue própria). |
| Editor | Lacuna do PRD 14; REV-05 pedia contrato independente do editor. | **Decidido**: editor de blocos próprio sobre o contrato do SSD 6. |
| Templates (SSD 8.4) | "A validar", sem código. | Tabela `configuration.doc_template` com os cinco tipos do artefato, `PAGE` e `REGIMENTO`. |
| Acesso (SSD 2.1) | A regra 3 mostra só o resumo dos ancestrais, nunca o conteúdo de documentos. | **Regra 9 nova**: o conhecimento **publicado** desce. Só acrescenta leituras, como a regra 8 permite. |
| Anexos e imagens | ADR-10 (S3/MinIO) decidido; MinIO ainda fora do `compose.yaml`. | Fora desta etapa. |
| Busca (`GET /search`) | Especificada, sem código. | Knowledge Center fica na Fase C, junto com a busca. |
| Auditoria | Pronta (outbox + trilha encadeada). | Eventos `knowledge_page.*` na trilha. |
| Navegação | Árvore no menu e "Visões da unidade" ([Proposta 06](NAVIGATION-PROPOSAL.md#6-revisão-2-proposal-07-estrutura-organizacional-como-navegação)). | Item **Wiki** nas visões da unidade (KNOW-013). |
| Central de documentos (PRD-RF-021) | Fase 2. | Converge com o Knowledge Center (Fase C). |

**Ajustes de forma:**
- As decisões KNOW-D01 a D08 e os requisitos KNOW-001 a 030 mantêm a numeração do original.
- Os estados do original (Draft, In Review, Approved, Published, Archived) viram `DRAFT`, `IN_REVIEW`, `APPROVED` e `PUBLISHED`. Arquivar é uma marca à parte, que não apaga o estado.
- "Espaço de conhecimento" não é uma entidade: o espaço é a própria unidade (KNOW-D01). Isso evita uma hierarquia paralela à organizacional.

## 3. Modelo adotado

- **Documento** (`livingdocs.document`) com `owner_type` `ARTIFACT` ou `ORG_UNIT`.
  - Página de conhecimento: `owner_type = ORG_UNIT`, com título, página pai e posição.
  - Documento de artefato: `owner_type = ARTIFACT`, um por tipo.
- **Versão** (`livingdocs.document_version`): imutável (um gatilho recusa alteração e exclusão), com seções, resumo, autor e data.
- **Template** (`configuration.doc_template`): seções, papel aprovador e `governed`.
  - `PAGE`: uma seção livre, não governado.
  - `REGIMENTO`: governado, com as 16 seções da seção 6 do original.
- **Hierarquia de páginas** dentro da unidade: o pai é uma página da mesma unidade, sem ciclos. Uma página não muda de unidade (seção 7.2 do original).

## 4. Decisões tomadas (2026-11-20)

| ID | Tema | Decisão |
|---|---|---|
| KNOW-D01 / D02 | Espaços | Um espaço por unidade, que é a própria unidade. A raiz é o espaço institucional. |
| KNOW-D03 | Regimentos | Template `REGIMENTO` governado. Templates distintos por nível ficam para a Fase B. |
| KNOW-D04 | Propriedade × leitura | O dono é a unidade. **Regra 9 do SSD 2.1**: páginas **publicadas** são lidas por quem tem papel na unidade ou em qualquer unidade abaixo. Rascunhos seguem as regras 1 a 7. A aplicabilidade explícita fica para a Fase C. |
| KNOW-D05 | Publicação | **Livre e governado.** Página comum publica ao salvar. Regimento: Rascunho → Em revisão → Aprovado → Publicado, com aprovação e publicação separadas. Editar um publicado abre rascunho novo, e quem lê pela regra 9 continua vendo a versão publicada. |
| KNOW-D06 | Arquitetura | Um só motor no `livingdocs`, com dono genérico. |
| KNOW-D07 | Knowledge Center | Fase C, com `GET /search`. |
| KNOW-D08 | Faseamento | Motor comum + Fase A agora; documentos do artefato logo depois; o MVP vigente não muda. |
| REV-05 | Editor | Editor de blocos próprio: parágrafo e destaque com Markdown leve, título, lista, tabela, citação e código. O texto nunca vira HTML: a tela monta os elementos. |

**Padrões assumidos, a validar:**
- Quem edita páginas: todo papel na unidade, menos `VIEWER` (`EDIT_KNOWLEDGE`).
- Seções obrigatórias do regimento: Identificação, Propósito, Escopo de atuação, Responsabilidades e Governança.
- Aprovador do regimento: `BUSINESS_OWNER` (e o `PLATFORM_ADMIN`), configurável no template.

## 5. Faseamento

| Fase | Escopo | Situação |
|---|---|---|
| A | Espaço por unidade, páginas e subpáginas, editor de blocos, versões e histórico, arquivamento, regimento governado, leitura do publicado pelas unidades abaixo, auditoria. | **Implementada** (2026-11-20). |
| — | Os cinco documentos de Iniciativa e Feature no mesmo motor, com os gates por documento (MVP). | Próxima entrega. |
| B | Templates de regimento por nível, revisão periódica, responsáveis e revisores, política de aprovação por tipo (KNOW-019, 027, 028). | Em avaliação. |
| C | Knowledge Center, busca, referências entre páginas e artefatos, aplicabilidade, conteúdos relacionados (KNOW-014, 015, 016, 022, 023, 029, 030 completo). | Em avaliação. |
| D | Comentários, menções, notificações e favoritos (KNOW-024, 025). | Em avaliação. |
| — | Imagens e anexos (KNOW-021), com o MinIO. Comparação lado a lado (KNOW-026). | Em avaliação. |

## 6. Requisitos: situação

| Requisito | Situação |
|---|---|
| KNOW-001 a 005 espaços por unidade e institucional | **Implementado** (qualquer nível, sem nome fixo). |
| KNOW-006 páginas e subpáginas | **Implementado**: criar, renomear, mover sem ciclo, arquivar e restaurar. |
| KNOW-007 versões e histórico | **Implementado**: versão por gravação, histórico e leitura de versão antiga. |
| KNOW-008 e 009 templates e regimento | **Parcial**: templates `PAGE` e `REGIMENTO`; a tela de configuração dos templates fica para a Fase B. |
| KNOW-010 proprietário | **Implementado** (a unidade). |
| KNOW-011 propriedade × visibilidade × aplicabilidade × edição × aprovação | **Parcial**: propriedade, visibilidade (regra 9), edição e aprovação; aplicabilidade na Fase C. |
| KNOW-012 permissões | **Implementado**; a API valida tudo, com 404 e 403 conforme a regra 7. |
| KNOW-013 acesso pelo contexto | **Implementado**: item Wiki nas visões da unidade. |
| KNOW-014, 015, 016, 022, 023, 029 | Fase C. |
| KNOW-017 auditoria | **Implementado**: `knowledge_page.created`, `.updated`, `.status_changed`, `.moved`, `.archived` e `.restored`. |
| KNOW-018 publicar e arquivar | **Implementado**. |
| KNOW-019 revisão periódica | Fase B. |
| KNOW-020 editor de blocos | **Implementado** (sem imagens). |
| KNOW-021 anexos e imagens | Com o MinIO. |
| KNOW-024, 025 | Fase D. |
| KNOW-026 comparar versões | Parcial: abrir qualquer versão; comparação lado a lado depois. |
| KNOW-027 e 028 responsáveis e política por tipo | Parcial: aprovador por template; o resto na Fase B. |
| KNOW-030 conhecimento aplicável sem duplicar | **Parcial**: o Wiki mostra as páginas publicadas das unidades acima; aplicabilidade explícita na Fase C. |

## 7. Testes

| Camada | O que cobre |
|---|---|
| JUnit `KnowledgeIntegrationTests` | Página livre e subpágina; conflito de versão; histórico; regimento (seções obrigatórias, aprovador, aprovação e publicação separadas); regra 9 (o membro da Squad lê o publicado e recebe 404 no rascunho e 403 no histórico); 404 para outro ramo; `VIEWER` sem edição; mover sem ciclo; arquivar com filhos (409); blocos desconhecidos; eventos na outbox. |
| Vitest `knowledge.spec.ts` | Markdown leve sem HTML (links perigosos ficam como texto) e árvore de páginas. |
| Robot API `test/robot/api/test/knowledge` | Página, subpágina, versão e conflito; ciclo do regimento com o BO; 403 para `VIEWER`. |
| Robot UI `test/robot/ui/test/wiki` | Criar e editar em blocos, histórico; regimento recusado sem as seções obrigatórias; PM sem "Aprovar". |

O cenário de interface de quem só tem papel embaixo depende da atribuição de papel por unidade (Administração › Usuários); até lá, a regra 9 é coberta no JUnit.

## 8. Revisão (bug #66): regimento independente do Wiki

Em 2026-11-21 o usuário definiu que **Wiki e Regimento são independentes**: cada unidade tem o próprio Wiki, com as páginas livres, e o próprio Regimento, outra aba da página da unidade ([Proposta 06, seção 7](NAVIGATION-PROPOSAL.md#7-revisão-3-bug-66-menu-contextual-por-unidade)).

- O regimento é **um por unidade**, criado por `POST /org-units/{id}/regimento` (`REGIMENTO_EXISTS` se já houver um ativo) e lido por `GET /org-units/{id}/regimento` (SSD 3.7.1).
- O Wiki (`/org-units/{id}/pages`) não lista nem cria regimentos; o regimento não pode ser pai de página nem ser movido para o Wiki.
- O motor continua o mesmo: versões, ciclo governado, regra 9 e auditoria valem para os dois.
- Tela própria: UX/UI 7.13.
