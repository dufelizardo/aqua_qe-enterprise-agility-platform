# Proposta 06: navegação organizacional dinâmica e workspaces contextuais

| | |
|---|---|
| **Versão** | 1.2 |
| **Data** | 2026-11-20 |
| **Status** | PROPOSTO. A Fase 1 mínima (nomes de nível da configuração, trilha, seletor em árvore) foi implementada; a **Revisão 2** ([seção 6](#6-revisão-2-proposal-07-estrutura-organizacional-como-navegação)) levou a árvore para o menu lateral e também foi implementada. A **Revisão 3** ([seção 7](#7-revisão-3-bug-66-menu-contextual-por-unidade), bug #66) tornou o menu contextual à unidade. O restante segue em avaliação. |
| **Série** | 01: [Evolução](EVOLUTION-PROPOSAL.md) · 02: [Experiência](EXPERIENCE-PROPOSAL.md) · 03: [Administração](ADMIN-PROPOSAL.md) · 04: [Quadros](BOARDS-PROPOSAL.md) · 05: [Configuração](CONFIG-PROPOSAL.md) · 06: esta · 07: [Conhecimento](KNOWLEDGE-PROPOSAL.md) |
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
| Menu por árvore organizacional (NAV-001, 002, 015) | Menu por Flight Level ([UX/UI 4](UX-UI.md#4-estrutura-da-aplicação); [EXP-09](EXPERIENCE-PROPOSAL.md#4-decisões-tomadas)). | Primeiro o meio-termo (árvore só no seletor); **revisado na Revisão 2**: a árvore fica no menu lateral, e os Flight Levels viram as visões da unidade escolhida. |
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
| NAV-D1 | Organização no menu | ~~Meio-termo: menu por Flight Level e árvore no seletor de workspace.~~ **Revisada em 2026-11-20 (Revisão 2, ORG-D01 e ORG-D02):** a **árvore organizacional fica no menu lateral**, e os itens por Flight Level, com o nome de cada nível vindo da configuração, passam a ser as **visões da unidade escolhida**. Um único serviço de contexto. |
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

## 6. Revisão 2 (PROPOSAL-07): estrutura organizacional como navegação

A PROPOSAL-07, recebida em 2026-11-20, pede a árvore organizacional **no menu lateral**, sincronizada com o seletor de workspace, preservando os Flight Levels e as permissões. Na prática, ela revisa a NAV-D1 depois de usar a tela; por isso entra aqui como revisão, e não como documento novo. Os IDs do original (ORG-NAV-001 a 018, ORG-D01 a D06) são mantidos.

### 6.1 Decisões (2026-11-20)

| ID | Tema | Decisão |
|---|---|---|
| ORG-D01 e ORG-D02 | Árvore no menu | **Aceitas.** O menu lateral tem, de cima para baixo: Home; **Organização** (a árvore); **Visões da unidade** (um item por Flight Level, com o nome do nível na configuração); Admin. A árvore e o resto da aplicação usam o mesmo serviço de contexto (`WorkspaceService`), então nunca divergem. O seletor do cabeçalho sai; o cabeçalho mostra só a unidade ativa. *Revisto na [Revisão 3](#7-revisão-3-bug-66-menu-contextual-por-unidade): a árvore tem só unidades, e as funções são abas da página da unidade.* |
| ORG-D03 | Ancestrais sem papel | **Aceita.** Quem só tem papel embaixo vê os ancestrais em cinza, só para se localizar: não são clicáveis e não dão acesso aos dados (regra 1 do [SSD 2.1](SSD.md#21-escopo-entre-unidades-rev-01-rev-02-rev-04)). |
| ORG-D04 | Unidades que são workspace | Toda unidade ativa no escopo do usuário pode ser escolhida. Nada muda em relação ao seletor anterior. |
| ORG-D05 | Visão organizacional consolidada | **Fase 2**, com o analytics (ORG-NAV-017 e 018). |
| ORG-D06 | Escopo agora | Árvore no menu, ancestrais em cinza, busca, gaveta no celular e os testes da seção 11 do original. |

**Seleção:** escolher uma unidade na árvore troca o workspace. Se a unidade tem Flight Level, a aplicação abre a tela desse nível (uma Squad abre `/squad`); se não tem (Empresa, Unidade de Negócio), a tela atual recarrega no novo contexto.

### 6.2 Requisitos: situação

| Requisito | Situação |
|---|---|
| ORG-NAV-001 árvore no menu | **Implementado** (`layout/org-tree.ts`). |
| ORG-NAV-002 e 013 árvore dinâmica, níveis personalizados | **Implementado**: montada de `GET /org-units` e `GET /me/workspaces`, sem nome de nível no código. |
| ORG-NAV-003 expandir e recolher | **Implementado**, com botão por ramo e setas direita e esquerda. |
| ORG-NAV-004 seleção direta | **Implementado.** |
| ORG-NAV-005 e 006 contexto único | **Implementado**: um só serviço; o cabeçalho e a trilha mostram a mesma unidade. |
| ORG-NAV-007 Flight Level da unidade | **Implementado**: a unidade leva à tela do seu Flight Level; a trilha mostra o nível e o Flight Level. |
| ORG-NAV-008 trilha | Já existia (Fase 1). |
| ORG-NAV-009 permissões | **Implementado**: só unidades com papel e as abaixo delas; os ancestrais aparecem sem ação. A API valida tudo. |
| ORG-NAV-010 várias unidades por nível | **Testado**: 2 Portfólios × 2 Cadeias × 2 Squads (JUnit). |
| ORG-NAV-011 "atende a" sem duplicar | **Testado**: a Cadeia que atende a outro Portfólio continua filha só do seu (JUnit). |
| ORG-NAV-012 unidade ativa destacada | **Implementado** (mesmo destaque do item ativo do menu). |
| ORG-NAV-014 responsividade | **Implementado**: no celular o menu vira gaveta, aberta pelo botão ☰. |
| ORG-NAV-015 estado de expansão | **Implementado**: lembrado no navegador; sem nada lembrado, abre o caminho até a unidade ativa. |
| ORG-NAV-016 busca de unidades | **Implementado**: filtra por nome e mantém o caminho até cada resultado. |
| ORG-NAV-017 e 018 visão organizacional com indicadores | Fase 2 (ORG-D05). |

### 6.3 Testes

| Camada | O que cobre |
|---|---|
| Vitest (`work.spec.ts`) | Montagem da árvore: administrador na raiz vê tudo escolhível; membro só de uma Squad vê Empresa, Portfólio e Cadeia como ancestrais não escolhíveis. |
| JUnit (`OrganizationIntegrationTests`) | Cardinalidade 2 × 2 × 2: filhos de cada unidade, caminho de uma Squad e "atende a" sem duplicar a hierarquia. |
| Robot UI (`ui/test/navigation/org_tree.robot`) | Escolher a Squad pela busca leva a `/squad` com a unidade no cabeçalho; recolher um ramo fica lembrado depois de recarregar; gaveta no celular. |

O cenário de interface do membro só de uma Squad depende de atribuir papel por unidade, que ainda não tem tela nem API (Administração › Usuários, backlog). Até lá, os ancestrais em cinza são cobertos pelo Vitest.

## 7. Revisão 3 (bug #66): menu contextual por unidade

O bug #66 (2026-11-20) apontou que, depois da Revisão 2, a árvore trocava a unidade, mas o menu continuava com os mesmos três itens de Flight Level. Na prática, a árvore era só um seletor de workspace. O princípio do bug é aceito: **a organização determina o contexto, o contexto orienta a navegação e as permissões determinam o que está disponível.**

### 7.1 Desenho decidido pelo usuário (2026-11-21)

Três versões anteriores foram recusadas no teste do usuário:
- itens de visão numa seção separada, abaixo da árvore;
- itens "Portfólios, Cadeias e Squads abaixo" dentro da árvore;
- Wiki e Regimento como itens da árvore.

O desenho final é o do usuário: **a árvore tem só as unidades; as funções de cada unidade são abas dentro da página dela**.

```text
ORGANIZAÇÃO                              PÁGINA DA UNIDADE ESCOLHIDA
▾ Empresa                                Empresa › Varejo › Tecnologia › Canais › Squad Mobile   Squad · FL1
  ▾ Unidade de Negócio Varejo            Squad Mobile
    ▾ Portfólio Tecnologia               [ Wiki | Regimento | Quadro | Backlog | Sprints ]
      ▾ Cadeia Canais
          Squad Mobile   ← escolhida
          Squad Internet Banking
      ▸ Cadeia Plataforma
    ▸ Portfólio Operações
  ▸ Unidade de Negócio Atacado
```

A árvore mostra todos os níveis da configuração que tiverem unidades (Empresa, Unidade de Negócio, Portfólio, Cadeia, Squad); uma organização sem Unidade de Negócio simplesmente não a tem na árvore.

| Unidade | Abas da página |
|---|---|
| Empresa, Unidade de Negócio, Portfólio, Cadeia | Wiki · Regimento |
| Squad (FL1) | Wiki · Regimento · Quadro · Backlog · Sprints |

| Tema | Decisão |
|---|---|
| Abas por unidade | Toda unidade: **Wiki** e **Regimento**. Unidade de FL1 (Squad): também **Quadro**, **Backlog** e **Sprints**. São abas no cabeçalho da página da unidade, não itens da árvore. Decide o Flight Level da configuração, não o nome do nível. Nada de itens "abaixo" nem de níveis acima. |
| Wiki × Regimento | **Independentes.** O Wiki tem as páginas livres da unidade; o Regimento é um documento governado por unidade, com tela e endpoints próprios ([Proposta 07](KNOWLEDGE-PROPOSAL.md#8-revisão-bug-66-regimento-independente-do-wiki)). |
| Onde nasce o trabalho | **Tudo nasce na Squad.** Iniciativas, Features e Histórias são criadas na Squad. O Quadro da Squad tem o seletor **Iniciativas \| Features \| Histórias**. Portfólio e Cadeia não têm mais quadro no menu. |
| Árvore | Só unidades. Clicar numa unidade escolhe o workspace e abre a página dela. |
| Troca de unidade | A aba atual continua se existir na nova unidade (inclusive a aba da Squad); senão, abre a primeira (Wiki). Da Home, abre o Wiki da unidade. |
| O que não existe | Estratégia, investimentos, dependências, qualidade e indicadores não aparecem até existirem (UX/UI 4). |

### 7.2 Regra de domínio alterada

O SSD 3.4 exigia que o artefato ficasse numa unidade do mesmo Flight Level (`INVALID_ORG_UNIT_TYPE`). Agora a **unidade de FL1 aceita artefatos de qualquer Flight Level**. Nas demais unidades, a regra antiga continua. A hierarquia de pais e as regras de acesso não mudam; quem cria uma Iniciativa na Squad continua precisando do papel de FL3 (matriz do SSD 2).

### 7.3 Implementação

- `core/context-nav.ts`: `contextViews` (as funções pelo Flight Level da unidade), `landingFor` (para onde ir ao escolher uma unidade) e `NavigationContext`.
- `layout/unit-header.ts` (novo): cabeçalho da página da unidade, com trilha, nome, nível e as abas (links com endereço, `aria-current` na ativa). Usado pelo Wiki, pelo Regimento e pela página da Squad.
- `layout/org-tree.ts`:
  - só unidades;
  - escolher uma unidade abre o caminho até ela e a página dela;
  - correção de corrida: uma escolha de unidade não sobrepõe um clique feito logo em seguida.
- `pages/wiki`: o mesmo componente atende `/wiki` e `/regimento` (modo pela rota); o Wiki não lista nem cria regimentos.
- `pages/level-board`: o título e as abas seguem a rota; o seletor do Quadro da Squad escolhe o nível do quadro e da criação, sempre na Squad.
- As abas da Squad têm endereço (`?tab=backlog`, `?tab=sprints`).
- Backend:
  - `ArtifactService` aceita qualquer Flight Level na Squad;
  - `PageService` ganhou `GET`/`POST /org-units/{id}/regimento`, com um regimento ativo por unidade (`REGIMENTO_EXISTS`).
- **Testes:**
  - JUnit: `tudoNasceNaSquad` e regimento independente;
  - Vitest `context-nav.spec.ts`;
  - Robot API (`REGIMENTO_EXISTS`);
  - Robot UI:
    - abas da página da Squad na ordem do desenho;
    - Iniciativa, Feature e Task nascendo na Squad;
    - Regimento por unidade.
