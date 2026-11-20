# Proposta 06: navegação organizacional dinâmica e workspaces contextuais

| | |
|---|---|
| **Versão** | 1.0 |
| **Data** | 2026-11-20 |
| **Status** | PROPOSTO. A Fase 1 mínima (nomes de nível da configuração, trilha, seletor em árvore) foi decidida e implementada; o restante segue em avaliação. |
| **Série** | 01: [Evolução](EVOLUTION-PROPOSAL.md) · 02: [Experiência](EXPERIENCE-PROPOSAL.md) · 03: [Administração](ADMIN-PROPOSAL.md) · 04: [Quadros](BOARDS-PROPOSAL.md) · 05: [Configuração](CONFIG-PROPOSAL.md) · 06: esta |
| **Documentos-base** | [PRD v4.0](PRD.md), [SAD v1.0](SAD.md) (ADR-14), [SSD v1.0](SSD.md), [UX/UI v1.0](UX-UI.md) |

A proposta substitui o menu fixo (Portfolio, Tactical, Squad) por uma navegação montada a partir da estrutura organizacional configurada. Também separa três dimensões: organização (a árvore), gestão (os Flight Levels) e capacidades (qualidade, governança, analytics). Por fim, apresenta um **workspace contextual** por unidade. O texto original foi recebido em 2026-11-20.

O princípio final é aceito: **a organização define a estrutura; os Flight Levels orientam a gestão; o workspace contextualiza o trabalho; a navegação conecta tudo.**

---

## 1. O que a proposta acertou

A interface contradizia a [ADR-14](SAD.md#10-decisões-de-arquitetura). Os níveis organizacionais são dados (nome, posição, Flight Level), mas o menu e o código do frontend usavam nomes fixos: "Portfolio", "Tactical", "Squad" e os rótulos "Portfólio", "Cadeia" e "Squad". Uma instalação que chame os níveis de "Produto", "Value Stream" e "Team" veria nomes errados.

A regra da seção 14.1 do original foi **adotada**: o frontend não usa nomes de unidades ou de níveis. Trabalha com identificadores, posições e a associação ao Flight Level.

## 2. Confronto com o que já existe

| Tema | O que já existe | Situação |
|---|---|---|
| Nomes configuráveis (NAV-003) | `GET /org-levels` (SSD 3.2). | **Implementado**: menu, título das telas e rótulo da unidade vêm do nível ligado ao Flight Level. |
| Menu por árvore organizacional (NAV-001, 002, 015) | Menu por Flight Level ([UX/UI 4](UX-UI.md#4-estrutura-da-aplicação); [EXP-09](EXPERIENCE-PROPOSAL.md#4-decisões-tomadas)). | **Meio-termo decidido (NAV-D1)**: o menu continua por Flight Level, com os nomes da configuração; a árvore vai para o seletor de workspace. |
| Selecionar unidade, contexto ativo, persistência (NAV-004, 005, 017) | Seleção de workspace ([UX/UI 7.1](UX-UI.md#71-seleção-de-workspace)), lembrada no navegador. | **Ampliado**: o seletor mostra a árvore das unidades com papel e das ativas abaixo delas, com o nível de cada uma. |
| Flight Level contextual (NAV-007) | — | **Implementado**: a trilha mostra o nível e o Flight Level da unidade ativa. |
| Trilha (NAV-008) | Prevista na UX/UI 4; não implementada. | **Implementado** para a unidade ativa, pelo `path` de `GET /org-units/{id}`. A parte dos ancestrais do artefato entra com a tela de detalhe do item. |
| Unidades relacionadas (NAV-010) | Vínculos "atende a" (SSD 3.2). | **Implementado**: "Atende a" aparece separado da trilha, sem virar ancestral. |
| Controle de acesso (NAV-009, 13.2) | Módulo `access`; o menu só esconde. | Já era assim. O seletor mostra as unidades em que o usuário tem papel e as abaixo delas (regra 1 do [SSD 2.1](SSD.md#21-escopo-entre-unidades-rev-01-rev-02-rev-04)). |
| Unidades desativadas (NAV-019) | — | **Implementado**: não aparecem como workspace. |
| Disponibilidade funcional (NAV-018) | UX/UI 4: módulos de fases futuras não aparecem, nem desabilitados. | Já era assim. |
| Workspace contextual (seção 7) | Uma tela por Flight Level, com o quadro da unidade (Propostas 04 e 05). | Composição por nível = MDL-05 e MDL-06 ([Proposta 05](CONFIG-PROPOSAL.md#4-decisões-abertas)). Fase 2. |
| Separação organização × capacidade × visualização (seção 8) | Propostas 04 e 05. | Já adotada. |
| Favoritos, recentes e preferências (NAV-011, 012, 014) | REV-10; UX/UI 13 (itens recentes). | Depois do MVP. |
| Pesquisa organizacional (NAV-013) | `GET /search` (SSD 3.10), MVP, ainda não implementado. | Com a busca do MVP. |
| Acessibilidade e teclado (NAV-020) | Itens de menu do Angular Material, foco visível. | Parcial; revisar com a auditoria de acessibilidade. |

**Ajustes de forma:** as decisões DEC-01 a DEC-08 do original passam a **NAV-D1 a NAV-D8**. Os requisitos NAV-001 a NAV-020 mantêm o número. O "Workspace" contextual da proposta é a **experiência da unidade escolhida**, e não uma entidade nova (ver [Proposta 05](CONFIG-PROPOSAL.md#2-confronto-com-o-que-já-existe)).

## 3. Decisões tomadas (2026-11-20)

| ID | Tema | Decisão |
|---|---|---|
| NAV-D1 | Organização no menu | **Meio-termo.** O menu continua por Flight Level (EXP-09), com o **nome de cada nível vindo da configuração**. A **árvore organizacional** fica no **seletor de workspace**, com trilha e "Atende a". |
| NAV-D8 | Escopo agora | Fase 1 mínima: nomes da configuração, trilha da unidade ativa, seletor em árvore com o nível, "Atende a" separado e unidades inativas fora. |

## 4. Decisões abertas

| ID | Tema | Recomendação |
|---|---|---|
| NAV-D2 | Grupos de capacidades no menu | Permanentes, aparecendo só quando o módulo existir (UX/UI 4). |
| NAV-D3 | Contexto inicial | A última unidade escolhida; sem escolha, a unidade com papel quando há uma só (já é assim). |
| NAV-D4 | Relações de atendimento | Contextuais, junto da trilha (já é assim). |
| NAV-D5 | Personalização | Começar por favoritos e recentes, com as preferências (REV-10). |
| NAV-D6 | Composição do workspace | Regras automáticas por nível primeiro; configuração explícita com o Customization Engine (MDL-05 e MDL-06). |
| NAV-D7 | Pesquisa global | Com a busca do MVP (SSD 3.10), incluindo unidades. |

## 5. Critérios de aceitação do original: situação

| Critério | Situação |
|---|---|
| 1. Nomes diferentes de Portfolio, Tactical e Squad | Atendido (teste unitário com "Produto", "Value Stream" e "Team"). |
| 2. Sem nomenclatura fixa | Atendido no menu, nos títulos e nos rótulos de unidade. |
| 3. Organização sem Unidade de Negócio | Atendido (cenário C3 do SSD 2.1; a árvore pula níveis). |
| 4. Atendimento não vira hierarquia | Atendido ("Atende a" separado da trilha). |
| 5. Só unidades permitidas | Atendido no seletor; a API valida tudo. |
| 6. Contexto correto ao selecionar | Atendido (a tela recarrega com a unidade escolhida). |
| 7. Flight Levels orientam a gestão | Mantido (uma tela por Flight Level). |
| 8. Funcionalidades existentes acessíveis | Mantido (os testes de interface continuam verdes). |
| 9. Personalização não altera o corporativo | Ainda não há personalização. |
| 10. Consistência em resoluções diferentes | Seletor e trilha quebram linha no celular; revisar com a auditoria de acessibilidade. |

<!-- aqeap-navigati-824e5ad33f -->
