# Proposta 07: conhecimento organizacional, Wiki e documentação viva

| | |
|---|---|
| **Versão** | 1.0 |
| **Data** | 2026-11-20 |
| **Status** | PROPOSTO. As decisões da seção 4 foram tomadas e a **Fase A** foi implementada; as fases B a D seguem em avaliação. O MVP do PRD não muda. |
| **Série** | 01: [Evolução](EVOLUTION-PROPOSAL.md) · 02: [Experiência](EXPERIENCE-PROPOSAL.md) · 03: [Administração](ADMIN-PROPOSAL.md) · 04: [Quadros](BOARDS-PROPOSAL.md) · 05: [Configuração](CONFIG-PROPOSAL.md) · 06: [Navegação](NAVIGATION-PROPOSAL.md) · 07: esta |
| **Documentos-base** | [PRD v4.0](PRD.md), [SAD v1.0](SAD.md), [SSD v1.0](SSD.md), [UX/UI v1.0](UX-UI.md) |
| **Propostas complementares** | [PROP-001 — gestão do conhecimento (análise de lacunas)](propostas/PROP-001-gestao-do-conhecimento-e-documentacao-organizacional.md) · [PROP-002 — organização flexível da Wiki](propostas/PROP-002-organizacao-flexivel-da-wiki.md) |

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
| Editor | Lacuna do PRD 14; REV-05 pedia contrato independente do editor. | **Decidido**: editor sobre o contrato do SSD 6; desde o bug #67, de texto contínuo (Tiptap). |
| Templates (SSD 8.4) | "A validar", sem código. | Tabela `configuration.doc_template` com os cinco tipos do artefato, `PAGE` e `REGIMENTO`. |
| Acesso (SSD 2.1) | A regra 3 mostra só o resumo dos ancestrais, nunca o conteúdo de documentos. | **Regra 9 nova**: o conhecimento **publicado** desce. Só acrescenta leituras, como a regra 8 permite. |
| Anexos e imagens | ADR-10 (S3/MinIO) decidido; MinIO ainda fora do `compose.yaml`. | Fora desta etapa. |
| Busca (`GET /search`) | **Implementada (#84, 2026-11-26)**: artefatos, documentos do artefato e páginas, com as regras de leitura. | Knowledge Center fica na Fase C, junto com a busca. |
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
| KNOW-D08 | Faseamento | Motor comum + Fase A agora; documentos do artefato logo depois; o MVP vigente não muda. **Documentos do artefato entregues em 2026-11-25 (#63)**, no mesmo motor (SSD 3.7). |
| REV-05 | Editor | Primeiro, editor de blocos próprio. **Revisto no bug #67:** editor de texto contínuo (Tiptap/ProseMirror), ver [seção 9](#9-revisão-bug-67-editor-de-texto-contínuo). |

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

### 5.1 Sequência após a análise do Confluence (2026-11-25)

O usuário comparou o Wiki com o Confluence (home, árvore do espaço, página e edição). As lacunas viraram seis entregas, nesta ordem, que adiantam partes das fases B a D:

| Ordem | Entrega | Issue | Situação |
|---|---|---|---|
| 1 | Menu ⋯ completo: renomear, copiar link, fazer uma cópia, organizar e arquivar | #81 | Concluída (validada em dev, 2026-11-25). |
| 2 | Rascunho automático e aviso ao sair sem salvar (Wiki e documentos) | #82 | Concluída (validada em dev, 2026-11-25): rascunho neste navegador. |
| 3 | Home: Recentes e Novidades | #83 | Concluída (validada em dev, 2026-11-26): módulo `activity`, `GET/POST /me/recent`, `GET /me/news`; UX/UI 7.2. |
| 4 | Busca em páginas, documentos e artefatos (`GET /search`, Fase C) | #84 | Concluída (validada em dev, 2026-11-26): módulo `search`, `GET /search`; UX/UI 7.9. |
| 5 | Comentários com @menção, notificações e favoritos (Fase D) | #85 | Concluída (validada em dev, 2026-11-26): comentários e "Comentar trecho" no `livingdocs`, módulo `notification` e favoritos em `activity`; UX/UI 7.12. |
| 6 | Editor: lista de tarefas, cor, sumário e emoji | #86 | Concluída (validada em dev, 2026-11-26): tarefas, cor e destaque; sumário automático; emoji no texto e ícone da página; UX/UI 7.12. |

Fora da lista por decisão: "remover de vez" (aqui se arquiva, por causa da auditoria), "Automatizar" e "Ver visualizações".

## 6. Requisitos: situação

| Requisito | Situação |
|---|---|
| KNOW-001 a 005 espaços por unidade e institucional | **Implementado** (qualquer nível, sem nome fixo). |
| KNOW-006 páginas e subpáginas | **Implementado**: criar, renomear, arquivar e restaurar; reorganizar a árvore (subir, descer, promover, rebaixar, mover para…, arrastar e soltar) desde a [PROP-002](propostas/PROP-002-organizacao-flexivel-da-wiki.md); menu ⋯ com renomear, copiar link e fazer uma cópia (#81). |
| KNOW-007 versões e histórico | **Implementado**: versão por gravação, histórico e leitura de versão antiga. |
| KNOW-008 e 009 templates e regimento | **Parcial**: templates `PAGE` e `REGIMENTO`; a tela de configuração dos templates fica para a Fase B. |
| KNOW-010 proprietário | **Implementado** (a unidade). |
| KNOW-011 propriedade × visibilidade × aplicabilidade × edição × aprovação | **Parcial**: propriedade, visibilidade (regra 9), edição e aprovação; aplicabilidade na Fase C. |
| KNOW-012 permissões | **Implementado**; a API valida tudo, com 404 e 403 conforme a regra 7. |
| KNOW-013 acesso pelo contexto | **Implementado**: item Wiki nas visões da unidade. |
| KNOW-014 busca | **Implementado (#84)**: `GET /search` e a tela de resultados (UX/UI 7.9). |
| KNOW-015, 016, 022, 023, 029 | Fase C. |
| KNOW-017 auditoria | **Implementado**: `knowledge_page.created`, `.updated`, `.status_changed`, `.moved`, `.archived` e `.restored`. |
| KNOW-018 publicar e arquivar | **Implementado**. |
| KNOW-019 revisão periódica | Fase B. |
| KNOW-020 editor rico | **Implementado**: texto contínuo com Tiptap (bug #67); lista de tarefas, cor, destaque, emoji, sumário automático e ícone da página (#86). Sem imagens (KNOW-021). |
| KNOW-021 anexos e imagens | Com o MinIO. |
| KNOW-024, 025 | **Implementado (#85)**: comentários com respostas, "Comentar trecho" (citação do texto selecionado), @menção, notificações (menção e resposta) e favoritos. O comentário ancorado no texto, que acompanha as edições, fica para depois. |
| KNOW-026 comparar versões | Parcial: abrir qualquer versão; comparação lado a lado depois. |
| KNOW-027 e 028 responsáveis e política por tipo | Parcial: aprovador por template; o resto na Fase B. |
| KNOW-030 conhecimento aplicável sem duplicar | **Revisto em 2026-11-25**: por decisão do usuário, cada unidade tem um Wiki exclusivo, como o regimento, e o Wiki de uma unidade não mostra mais as páginas publicadas das unidades acima. Aplicabilidade explícita fica para a Fase C, se voltar a ser pedida. |

## 7. Testes

| Camada | O que cobre |
|---|---|
| JUnit `KnowledgeIntegrationTests` | Página livre e subpágina; conflito de versão; histórico; regimento (seções obrigatórias, aprovador, aprovação e publicação separadas); regra 9 (o membro da Squad lê o publicado e recebe 404 no rascunho e 403 no histórico); 404 para outro ramo; `VIEWER` sem edição; mover sem ciclo; arquivar com filhos (409); blocos desconhecidos; eventos na outbox. |
| Vitest `knowledge.spec.ts` | Conversão do conteúdo antigo para o editor contínuo, sem perda; Markdown leve sem HTML; árvore de páginas. |
| Robot API `test/robot/api/test/knowledge` | Página, subpágina, versão e conflito; ciclo do regimento com o BO; 403 para `VIEWER`. |
| Robot UI `test/robot/ui/test/wiki` | Criar e editar em blocos, histórico; regimento recusado sem as seções obrigatórias; PM sem "Aprovar". |

O cenário de interface de quem só tem papel embaixo depende da atribuição de papel por unidade (Administração › Usuários); até lá, a regra 9 é coberta no JUnit.

## 8. Revisão (bug #66): regimento independente do Wiki

Em 2026-11-21 o usuário definiu que **Wiki e Regimento são independentes**: cada unidade tem o próprio Wiki, com as páginas livres, e o próprio Regimento, outra aba da página da unidade ([Proposta 06, seção 7](NAVIGATION-PROPOSAL.md#7-revisão-3-bug-66-menu-contextual-por-unidade)).

- O regimento é **um por unidade**, criado por `POST /org-units/{id}/regimento` (`REGIMENTO_EXISTS` se já houver um ativo) e lido por `GET /org-units/{id}/regimento` (SSD 3.7.1).
- O Wiki (`/org-units/{id}/pages`) não lista nem cria regimentos; o regimento não pode ser pai de página nem ser movido para o Wiki.
- O motor continua o mesmo: versões, ciclo governado, regra 9 e auditoria valem para os dois.
- Tela própria: UX/UI 7.13.

## 9. Revisão (bug #67): editor de texto contínuo

O primeiro editor obrigava a montar o documento bloco a bloco (escolher o tipo, preencher campos e reordenar). O bug #67 (2026-11-21) pediu um editor como o do Confluence, do Word ou do Bloco de Notas: abrir, clicar e escrever.

**O que mudou**
- **Editor:** área de texto contínua com **Tiptap 3** (ProseMirror), escolhido por ser maduro, de código aberto e por guardar o conteúdo num formato estruturado (JSON), o que mantém a verificação das seções obrigatórias e a comparação entre versões.
- **Barra e atalhos:**
  - estilo (texto, título, subtítulo);
  - negrito, itálico, sublinhado, código no texto e link;
  - listas com marcadores e numeradas, citação, bloco de código e tabela;
  - desfazer e refazer.
- **Contrato (SSD 6):** cada seção vira um bloco `richtext` com o documento do editor. O servidor continua validando a estrutura e contando como preenchida a seção que tem texto.
- **Conteúdo antigo:** os blocos do editor anterior continuam aceitos e são convertidos para o editor ao abrir; ao salvar, passam a `richtext`. Versões antigas seguem legíveis. Não houve migração no banco, porque as versões são imutáveis.
- **Segurança:** o esquema do ProseMirror só aceita os nós e marcas conhecidos, e o link recusa `javascript:`; a leitura usa o mesmo editor, sem edição.

**Preservado:**
- o motor comum `livingdocs`;
- os espaços por unidade;
- páginas e subpáginas;
- o acesso (regras 1 a 7 e 9);
- versões e histórico;
- o bloqueio otimista;
- o regimento governado (seções obrigatórias, revisão, aprovação e publicação);
- a auditoria.

**Testes**
- JUnit `editorContinuoGravaRichtext`: grava e devolve o documento; recusa `richtext` sem `doc`; seção obrigatória preenchida pelo editor libera a aprovação.
- Vitest: conversão de cada tipo antigo, ida e volta sem perda, seção vazia.
- Robot UI: clicar na área, ligar o negrito pela barra, digitar e salvar; a leitura mostra o texto em negrito.

## 10. Revisão (PROP-002): organização flexível da Wiki

A [PROP-002](propostas/PROP-002-organizacao-flexivel-da-wiki.md) (2026-11-21) pediu que a unidade organize a árvore do Wiki como quiser: ordem manual, mover, promover e rebaixar, sem perder identidade, histórico ou links. Decidido em 2026-11-24: **alternativa A**. Páginas-pai funcionam como agrupador; pastas sem conteúdo ficam para depois, se a necessidade aparecer.

- **API:** `POST /pages/{id}/rank` (SSD 3.7.1), no mesmo padrão da ordenação dos artefatos: posição entre as irmãs vizinhas e, opcionalmente, um novo pai. A página e as subpáginas mantêm o id, o conteúdo e as versões.
- **Tela:** menu ⋯ em cada página da árvore (subir, descer, promover, rebaixar, mover para…) e arrastar e soltar, que põe a página onde foi solta, inclusive em outro nível (UX/UI 7.12).
- **Auditoria:** `knowledge_page.moved`, com pai e posição antes e depois.
- **Decisões da seção 14 da PROP-002:**
  - agrupador = página-pai;
  - a ordem manual é única e compartilhada pela unidade;
  - quem organiza é quem edita (`EDIT_KNOWLEDGE`);
  - sem limite de profundidade;
  - a árvore do Wiki fica só na tela do Wiki, não no menu global;
  - ordenações alternativas e pastas ficam para depois.
- **Testes:**
  - JUnit `reorganizarAWikiPreservaPaginaEHistorico`;
  - Vitest das ações e dos destinos;
  - Robot API `CT-004`;
  - Robot UI `CT-004`.
