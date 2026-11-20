# Proposta 04: workspaces, quadros e visualizações de gestão do trabalho

| | |
|---|---|
| **Versão** | 1.0 |
| **Data** | 2026-11-20 |
| **Status** | PROPOSTO. A Etapa 1 do Kanban (colunas e WIP configuráveis, marca de bloqueado) foi decidida e implementada; o restante segue em avaliação. |
| **Série** | 01: [Evolução](EVOLUTION-PROPOSAL.md) · 02: [Experiência corporativa](EXPERIENCE-PROPOSAL.md) · 03: [Administração](ADMIN-PROPOSAL.md) · 04: esta |
| **Documentos-base** | [PRD v4.0](PRD.md), [SAD v1.0](SAD.md), [SSD v1.0](SSD.md), [UX/UI v1.0](UX-UI.md) |
| **Inspiração** | As abas de um projeto no Jira, usadas só como referência. O AQuA-QE não reproduz a navegação dele. |

A proposta pede várias visualizações sobre os mesmos itens: resumo, backlog, Kanban, Scrum, lista, cronograma, calendário e relatórios. Também pede um Kanban completo, com colunas configuráveis, limite de WIP, bloqueios, swimlanes e métricas de fluxo. Foi recebida em 2026-11-20 como resposta às perguntas sobre o quadro de Portfolio. Este documento a confronta com o que já existe e registra o que foi decidido.

---

## 1. Princípios aceitos

- **Um único modelo de dados.** Toda visualização lê os mesmos artefatos e respeita as mesmas permissões. É o que o produto já faz: o quadro e a lista do Portfolio usam o mesmo `GET /artifacts` e a mesma regra de leitura ([SSD 2.1](SSD.md#21-escopo-entre-unidades-rev-01-rev-02-rev-04)).
- **O status é a fonte de verdade.** A coluna é só a representação visual de um ou mais status ([SSD 3.9](SSD.md#39-board)).
- **Mover cartão é executar a transição.** Arrastar não contorna o workflow nem os gates. Já implementado ([SSD 3.5](SSD.md#35-workflow)).
- **Kanban e Scrum** usam o mesmo modelo de itens, e a sprint é opcional (PRD-RF-015).

## 2. Confronto com o que já existe

| Tema | O que já existe | Situação |
|---|---|---|
| Quadros configuráveis em FL3, FL2 e FL1 (KAN-001 a 003) | PRD-RF-011 (MVP): quadros configuráveis, divididos em Upstream e Downstream. | **Implementado em 2026-11-20**: `PATCH /boards/{id}` e "Configurar quadro". |
| Mover cartão, validar transição, histórico (KAN-004, 005, 010) | SSD 3.5. | Implementado. |
| **Limite de WIP (KAN-006)** | Não existia. | **Novo, implementado**: por coluna, com política de avisar ou impedir. |
| **Bloqueio (KAN-009)** | Não existia. | **Novo, implementado**: marca e motivo no item, etiqueta no cartão, filtro. Responsável pela resolução e dependências ficam com o D06 (Fase 2). |
| Filtros (KAN-008) | Texto, "só bloqueados" e, na API, tipo e responsável. | Parcial; filtros salvos (KAN-013) ficam depois. |
| Lista (7.3) | Implementada no Portfolio. | Edição rápida, seleção múltipla e exportação ficam depois. |
| Scrum, sprints (7.2) | PRD-RF-015 (MVP) e os endpoints de sprint do SSD 3.9. | Já é MVP, com a tela Squad. Burndown e capacidade: Fase 2 (PRD-RF-013). |
| Backlog (6.1) | Coluna Backlog do fluxo e ordenação por `rank` (SSD 3.4). | Coberto pelo quadro; uma visão "Backlog" dedicada fica depois. |
| Swimlanes, cartões personalizáveis, compartilhamento (KAN-011, 012, 014) | Nada. | Novos, depois do MVP. |
| Métricas de fluxo, Aging WIP (KAN-016, 017; seção 11) | D09, Fase 2. | Já previstos na Fase 2. |
| Políticas de entrada e saída (KAN-019) | Gates (SSD 5). | Já previstas como gates. |
| Cronograma e calendário (6.2, 6.3) | Nada no PRD. | Novos, depois do MVP. |
| Metas (6.4) | OKRs, D01, Fase 2 ([EXP-04](EXPERIENCE-PROPOSAL.md#5-decisões-abertas)). | Sem estrutura nova de metas. |
| Qualidade e Test Board (8) | D07, Fase 2. | Já previstos. |
| Engenharia, releases e versões (9, 12.2) | D08, Fase 2. | Já previstos. |
| Documentos e anexos (10.1, 10.2) | D04 no MVP; REV-08 (listar anexos). | Já previstos. |
| Formulários e caixa de entrada (10.3, 10.5) | Notificações só por e-mail (SSD 10). | Novos, depois do MVP. |
| Itens arquivados (12.1) | Arquivar e listar arquivados já existem. | Restaurar é novo e fica depois. |
| Configuração pessoal, compartilhada e organizacional (13) | Níveis de configuração ([UX/UI 7.11](UX-UI.md#711-níveis-de-configuração)). | A configuração do quadro é compartilhada e gerida por quem gere quadros. Visões pessoais ficam depois. |

**Ajustes de forma**
- **"Workspace" já tem dois significados no produto:** a unidade escolhida (UX/UI 7.1) e o workspace de um artefato (UI-06). O "workspace" da proposta, um hub com abas de Planejamento, Execução, Qualidade etc., seria um terceiro. Não foi adotado: a navegação continua por Flight Levels ([EXP-09](EXPERIENCE-PROPOSAL.md#4-decisões-tomadas)), e cada tela de nível ganha as visualizações que fizerem sentido, como o quadro e a lista no Portfolio.
- **Identificadores:** as decisões DEC-01 a DEC-12 do original passam a **BRD-01 a BRD-12**. Os requisitos KAN-001 a KAN-020 mantêm o número.

## 3. Decisões tomadas (2026-11-20)

| ID | Tema | Decisão |
|---|---|---|
| BRD-01 | Kanban como capacidade transversal | Sim: o mesmo quadro configurável serve aos três níveis (PRD-RF-011). |
| BRD-02 | Quadros em FL3, FL2 e FL1 | Sim (SSD 3.9). |
| BRD-03 | Quadro com itens de vários contextos | Um quadro mostra o nível na subárvore da unidade, incluindo os itens de outros ramos que descendem de itens da unidade (SSD 2.1, regra 2). |
| BRD-04 | Associação entre status e colunas | Cada coluna agrupa um ou mais status do nível; cada status fica em no máximo uma coluna; status sem coluna gera aviso. |
| BRD-05 | Limite de WIP impede movimentos? | Configurável por coluna: **avisar** (padrão) ou **impedir a entrada**. Com "impedir", a transição é recusada se a coluna de destino estiver no limite em **qualquer** quadro que contenha o item; o motivo informa qual quadro. |
| BRD-08 | Scrum no MVP? | Sim, como já previa o PRD-RF-015 (tela Squad). |

Também decidido: as colunas e o limite de WIP são **configuráveis** por quadro, porque cada instituição tem mais ou menos etapas. O marcador Upstream/Downstream sobre as colunas foi aprovado.

## 4. Decisões abertas

| ID | Tema | Recomendação |
|---|---|---|
| BRD-06 | Configurações pessoais × compartilhadas | Seguir a UX/UI 7.11: quadro compartilhado; filtros e visões pessoais pelas preferências (REV-10). |
| BRD-07 | Visões personalizadas | Depois do MVP, sobre filtros salvos (KAN-013). |
| BRD-09 | Métricas de fluxo iniciais | Com o D09 (Fase 2): WIP, lead time, cycle time, vazão e Aging WIP, a partir do histórico de status que já é gravado. |
| BRD-10 | Test Board × quadros operacionais | Com o D07 (Fase 2). |
| BRD-11 | Visualizações por tipo de workspace | Por tela de Flight Level: Portfolio, Tactical e Squad, com quadro e lista; Squad com sprint. |
| BRD-12 | Atualização consistente entre visualizações | Hoje cada tela recarrega depois de agir. Atualização ao vivo entre usuários fica para depois do MVP. |

## 5. Requisitos KAN: situação

| ID | Requisito | Situação |
|---|---|---|
| KAN-001 a 003 | Criar, configurar colunas e associar status | Implementado |
| KAN-004, 005, 010 | Mover, validar transição, histórico | Implementado |
| KAN-006 | Limite de WIP | Implementado |
| KAN-007, 012 | Cartões configuráveis e personalizados | Depois do MVP |
| KAN-008 | Filtros | Parcial (texto, bloqueados, tipo e responsável na API) |
| KAN-009 | Bloqueios | Implementado (marca e motivo) |
| KAN-011 | Swimlanes | Depois do MVP |
| KAN-013, 014 | Salvar e compartilhar visões | Depois do MVP |
| KAN-015 | Dependências | D06, Fase 2 |
| KAN-016, 017 | Métricas e Aging WIP | D09, Fase 2 |
| KAN-018 | Quadros com escopos diferentes | Por unidade e nível (BRD-03) |
| KAN-019 | Políticas de entrada e saída | Gates (SSD 5) |
| KAN-020 | Atualização consistente | Recarga depois de cada ação (BRD-12) |

<!-- aqeap-boardspr-78c7b31583 -->
