# PRD — AQuA-QE Enterprise Agility Platform

| | |
|---|---|
| **Versão** | 4.0 (consolidada) |
| **Data** | 2026-11-14 |
| **Status** | Rascunho para revisão |
| **Substitui** | PRD V1, PRD V2, PRD V2.1 e PRD V3 (em [pdr/](pdr/)) |

Este documento é a fonte única de requisitos de produto. Os quatro PRDs anteriores continuam na pasta apenas como histórico; em caso de divergência, vale este. As especificações técnicas V2.0 continuam válidas e são referenciadas por domínio na [seção 7](#7-mapa-de-domínios). As versões V1 das especificações estão obsoletas.

Onde os documentos anteriores se contradiziam, a escolha feita está registrada na [seção 12](#12-decisões-de-consolidação) com um ID `DEC-xx`. Itens marcados **(a validar)** são propostas desta consolidação, não decisões herdadas.

---

## 1. Visão

A AQuA-QE é uma plataforma corporativa unificada de gestão de portfólio, fluxo de valor, governança e documentação viva, inspirada no modelo de Flight Levels. Ela conecta a estratégia de negócio à execução operacional com rastreabilidade de ponta a ponta.

Três princípios orientam o produto:

1. **O Workspace do Artefato é o centro da experiência.** Na página de uma Iniciativa ou Feature se encontram planejamento, documentação técnica, execução e governança. A documentação não é um módulo à parte: faz parte do ciclo de vida do artefato.
2. **Governança por gates, não por processo paralelo.** A passagem de status é controlada por validações e aprovações configuráveis (DoR/DoD), avaliadas pelo próprio sistema.
3. **Dependências e estratégia visíveis.** Cada dependência entre times é ligada aos Key Results que ela pode afetar, para que um atraso tático seja percebido antes de comprometer a meta.

## 2. Problema

- Requisitos, arquitetura, design e testes ficam espalhados em ferramentas diferentes e perdem o vínculo com o item de trabalho.
- Em estruturas com vários portfólios, o atraso de uma entrega em uma squad paralisa a Feature de outra sem que a liderança perceba a tempo.
- O progresso de entrega é confundido com resultado de negócio.
- Critérios de qualidade e conformidade dependem de disciplina manual.

## 3. Nome do produto

O nome oficial é **AQuA-QE Enterprise Agility Platform**; a forma curta é **AQuA-QE**. "Enterprise Agility Platform" sozinho, usado nos documentos anteriores, não deve mais ser usado como nome.

## 4. Glossário

| Termo | Definição |
|---|---|
| **Flight Level (FL)** | Nível de gestão: FL3 estratégico (portfólios), FL2 tático (cadeias), FL1 operacional (squads). |
| **Portfólio** | Agrupamento estratégico de Iniciativas, pertencente a uma Unidade de Negócio. |
| **Cadeia** | Cadeia de valor: agrupamento tático de squads que entrega Features. |
| **Squad** | Time operacional que executa Histórias e demais itens de FL1. |
| **Iniciativa** | Entregável estratégico (FL3), com duração entre 3 meses e 1 ano. |
| **Feature** | Quebra tática de uma Iniciativa (FL2), com duração de até 3 meses. |
| **Feature de Sustentação** | Item mensal de FL2 que reserva capacidade para estabilidade, segurança e atualizações. |
| **Upstream / Downstream** | Fases de descoberta e de entrega de um fluxo, em qualquer nível. |
| **Validation** | Verificação automática de uma condição (ex.: campo preenchido). |
| **Approval** | Aprovação formal por um papel autorizado. |
| **Gate** | Conjunto de validações e aprovações que controla uma transição de status. |
| **DoR / DoD** | Critérios de entrada (Definition of Ready) e de saída (Definition of Done) de um status. |
| **RN** | Regra de Negócio; a versão oficial fica na Feature e é consumida por referência pelas Histórias. |
| **CA** | Critério de Aceitação; próprio de cada História. |
| **PRD / SAD / SSD** | Documentos de requisitos de produto, de arquitetura e de especificação de baixo nível. |
| **RTM** | Matriz de rastreabilidade de requisitos. |
| **OKR / KR** | Objetivo e Resultado-Chave. |
| **Delivery Progress** | Percentual de trabalho concluído ligado a um KR (calculado). |
| **Business Outcome Progress** | Evolução real do resultado de negócio de um KR (medido). |
| **Deployed / Stabilized / Value Realized** | Estados pós-entrega: implantado, estabilizado após observação, valor de negócio validado. |
| **Bug** | Desvio encontrado antes da produção. |
| **Problema de Produção** | Incidente detectado em ambiente produtivo. |

## 5. Personas

| Persona | Responsabilidade principal |
|---|---|
| **BO / SO** (Business / Strategic Owner) e alta gestão | Aprova iniciativas, acompanha portfólio, valor entregue e OKRs. |
| **PM / PO** | Gerencia backlog, prioriza, escreve o PRD do artefato. |
| **LTi / Arquiteto** | Responde por viabilidade técnica, SAD, SSD e gates de arquitetura e segurança. |
| **UX/UI Designer** | Anexa protótipos, fluxos e diretrizes de acessibilidade ao artefato. |
| **QA / QE** | Planeja testes, mantém cenários e a rastreabilidade de cobertura. |
| **Membro de squad** (Dev, QA, UX) | Executa o trabalho de FL1. |
| **Operação / SRE** | Trata incidentes de produção e acompanha estabilidade. *(só aparecia nas especificações)* |
| **Administrador da plataforma** | Configura workflows, templates, gates, papéis e integrações. *(só aparecia nas especificações)* |

## 6. Hierarquia de artefatos

| Nível | Tipo | Regra |
|---|---|---|
| **FL3 — Estratégico** | Iniciativa | 3 meses a 1 ano. Liga-se a KRs. |
| **FL2 — Tático** | Feature | Até 3 meses. Mantém as RNs oficiais. |
| | Feature de Sustentação | Ciclo mensal. |
| **FL1 — Operacional** | História | Possui CAs próprios; referencia RNs da Feature. |
| | Spike | Estudo técnico ou de negócio de curto prazo. |
| | Débito Técnico | Custo de decisão postergada, planejado e priorizado. |
| | Bug | Desvio antes da produção. |
| | Problema de Produção | Incidente com severidade, impacto, causa raiz e vínculo com hotfix. |
| | Sustentação, Task, Subtask | Trabalho de manutenção e tarefas de apoio. |

Os tipos e rótulos podem ser renomeados e estendidos pelo Customization Engine, preservando a estrutura de três níveis. O detalhamento está na [Taxonomia de Issues V2.0][spec-it].

## 7. Mapa de domínios

Numeração única, que substitui referências anteriores como "Domínio 10" (DEC-06).

| ID | Domínio | Especificação de referência |
|---|---|---|
| D01 | Portfolio & Strategy Management (inclui OKRs) | *sem especificação* |
| D02 | Tactical & Delivery Management (FL2) | [Taxonomia de Issues V2.0][spec-it] (parcial) |
| D03 | Squad & Delivery Management (FL1) | [Taxonomia de Issues V2.0][spec-it] (parcial) |
| D04 | Living Documentation | *sem especificação*; templates em [Customization V2.0][spec-ce] |
| D05 | Governance & Compliance | *sem especificação*; gates em [Customization V2.0][spec-ce] |
| D06 | Traceability & Dependency Management | RTM em [Gestão de Testes V2.0][spec-qt]; dependências *sem especificação* |
| D07 | Quality & Traceability Management | [Gestão de Testes V2.0][spec-qt] |
| D08 | Engineering & Release Management (inclui Run e Production Health) | [DevOps V2.0][spec-dv], [Taxonomia de Issues V2.0][spec-it] |
| D09 | Enterprise Agility Analytics (inclui Dashboards & Reporting) | [Analytics V2.0][spec-an], [Dashboards V2.0][spec-db] |
| D10 | Customization Engine | [Customization V2.0][spec-ce] |
| D11 | Security, Identity & Reliability *(transversal)* | [Segurança V2.0][spec-sc] |
| D12 | Deployment & Hosting *(transversal)* | [Hospedagem e Deploy V2.0][spec-dh] |

## 8. Épicos

| ID | Épico | Domínio | Origem |
|---|---|---|---|
| EP01 | Portfolio & Strategy Management | D01 | V2.1 |
| EP02 | Value Stream & Tactical Planning | D02 | V2.1 |
| EP03 | Squad & Delivery Management | D03 | V2.1 |
| EP04 | Living Documentation | D04 | V2.1 |
| EP05 | Governance & Compliance | D05 | V2.1 |
| EP06 | Traceability & Dependency Management | D06 | V2.1 |
| EP07 | Development & Release Management | D08 | V2.1 |
| EP08 | Enterprise Intelligence | D09 | V2.1 |
| EP09 | Quality & Test Management | D07 | novo — existia só nas especificações |
| EP10 | Customization & Configuration | D10 | novo — existia só nas especificações |

D11 e D12 são transversais e não têm épico próprio; seus requisitos estão nas especificações.

## 9. Navegação principal

1. **Executive Overview** — portfólio, valor, riscos e OKRs.
2. **Portfolio Management** (FL3) — iniciativas, ideias, business cases.
3. **Tactical Planning** (FL2) — Features, sustentação, capacidade.
4. **Squad Workspace** (FL1) — histórias, bugs, sprints.
5. **Living Documentation** — central de PRD, SAD, SSD, UX/UI e QA.
6. **Quality** — repositório de testes, execuções, RTM. *(acrescentado: existia nas especificações e no protótipo, não no PRD V2.1)*
7. **Governance & Compliance** — gates, aprovações, auditoria.
8. **Analytics & Intelligence** — métricas, dashboards, relatórios.
9. **Administration** — usuários, papéis, integrações e Configuration Center.

O protótipo [home.png](prototype/home.png) mostra um menu diferente (Dashboard, Portfolios, Initiatives, Analytics, Quality, Governance, Settings) e precisa ser atualizado para esta lista.

## 10. Requisitos funcionais

Os IDs `PRD-RF-xxx` substituem os `RF01…` dos PRDs anteriores, que se repetiam com significados diferentes. A [seção 15](#15-rastreabilidade-dos-ids-antigos) traz o mapeamento. Os requisitos detalhados de cada domínio (`RF-QT`, `RF-AN`, `RF-SC` etc.) permanecem nas especificações e não são repetidos aqui.

A coluna **Fase** segue a proposta da [seção 13](#13-faseamento-proposto-a-validar).

### EP01 — Portfolio & Strategy Management

| ID | Requisito | Origem | Fase |
|---|---|---|---|
| PRD-RF-001 | O sistema deve gerir Iniciativas em um fluxo FL3 com fases de Upstream (Ideia, Backlog, Estudo de Viabilidade, Em Refinamento) e Downstream (Em Construção, Concluído). | V1 Épico 1 | MVP |
| PRD-RF-002 | O sistema deve oferecer um funil de ideias com avaliação, votação e conversão em Iniciativa. | V1 Épico 1; V2.1 UI-05 | 2 |
| PRD-RF-003 | Cada Iniciativa deve ter um business case registrado e validável. | V1 Épico 1; V2.1 UI-06 | MVP |
| PRD-RF-004 | O sistema deve consolidar vários portfólios e unidades de negócio em uma visão executiva, com capacidade e orçamento agregados. | V3 Épico 1 | 2 |
| PRD-RF-005 | O sistema deve permitir atribuir KRs a Iniciativas e desdobrá-los em Features. | V3 Épico 3 | 2 |
| PRD-RF-006 | Para cada KR, o sistema deve calcular o **Delivery Progress** a partir da conclusão das Features e Iniciativas associadas. | V3 RF03, alterado por DEC-01 | 2 |
| PRD-RF-007 | Para cada KR, o sistema deve registrar o **Business Outcome Progress** como valor medido, independente do Delivery Progress. | Analytics V2.0; DEC-01 | 2 |
| PRD-RF-008 | O sistema deve calcular um índice de saúde do OKR com base no avanço das entregas e nos bloqueios de dependência. | V3 Épico 3 | 2 |
| PRD-RF-009 | O sistema deve emitir alertas preditivos de desvio de KR causados por atraso de dependências. | V3 Épico 3 | 3 |

### EP02 — Value Stream & Tactical Planning

| ID | Requisito | Origem | Fase |
|---|---|---|---|
| PRD-RF-010 | O sistema deve manter o vínculo hierárquico Iniciativa → Feature → História, com vínculos obrigatórios e opcionais e regras de validação estrutural configuráveis (ex.: Iniciativa com mais de uma Feature). | V1 RF01; V2 RF01 | MVP |
| PRD-RF-011 | Cada nível (FL3, FL2, FL1) deve ter quadros configuráveis divididos em Upstream e Downstream. | V1 RF03 | MVP |
| PRD-RF-012 | O sistema deve suportar Features e Features de Sustentação como tipos de FL2. | V1 §3; DEC-08 | MVP |
| PRD-RF-013 | O sistema deve permitir planejar a capacidade mensal por squad, confrontando demanda e disponibilidade. | V1 Épico 2; V2.1 UI-09 | 2 |
| PRD-RF-014 | Features devem aceitar estimativa por t-shirt size. | V1 Épico 2 | MVP |

### EP03 — Squad & Delivery Management

| ID | Requisito | Origem | Fase |
|---|---|---|---|
| PRD-RF-015 | O sistema deve oferecer quadros operacionais por squad, com suporte a sprints e a fluxo contínuo. | V1 Épico 3 | MVP |
| PRD-RF-016 | O sistema deve disponibilizar os tipos de FL1 da [seção 6](#6-hierarquia-de-artefatos). | V1 §3 | MVP |
| PRD-RF-017 | A tela de um item de FL1 deve exibir o contexto resumido da Feature e da Iniciativa de origem, sem exigir navegação entre níveis. | V2 RF03; V2.1 UI-16 | MVP |

### EP04 — Living Documentation

| ID | Requisito | Origem | Fase |
|---|---|---|---|
| PRD-RF-018 | Cada Iniciativa e Feature deve ter abas nativas e padronizadas de PRD, SAD, SSD, UX/UI e Planejamento de Testes. | V2 RF02 | MVP |
| PRD-RF-019 | O Workspace do Artefato deve exibir o status de cada documento (Pending, In Progress, Approved) ao lado do status dos gates. | V2.1 §4 | MVP |
| PRD-RF-020 | Os documentos devem ser versionados, com histórico consultável no próprio artefato. | V2.1 EP04 | MVP |
| PRD-RF-021 | O sistema deve oferecer uma central que lista os documentos de todos os artefatos a que o usuário tem acesso. | V2.1 UI-10 | 2 |

### EP05 — Governance & Compliance

| ID | Requisito | Origem | Fase |
|---|---|---|---|
| PRD-RF-022 | O sistema deve impedir a transição de status quando campos obrigatórios da saída daquele status não estiverem preenchidos. | V1 RF02 | MVP |
| PRD-RF-023 | O sistema deve impedir a transição quando seções obrigatórias da documentação estiverem vazias ou sem a aprovação exigida. | V2 RF04 | MVP |
| PRD-RF-024 | O sistema deve suportar gates de Arquitetura, Segurança, FinOps e QA, com aprovação formal por papel. | V1 Épico 4; V2 Épico 4 | MVP (Arquitetura e QA); 2 (demais) |
| PRD-RF-025 | O sistema deve oferecer um painel de gates, aprovações e pendências de documentação. | V2.1 UI-17 | 2 |
| PRD-RF-026 | Transições, aprovações e alterações em documentos devem ser registradas em trilha de auditoria, conforme a [especificação de Segurança][spec-sc]. | V2.1 EP05 | MVP |

### EP06 — Traceability & Dependency Management

| ID | Requisito | Origem | Fase |
|---|---|---|---|
| PRD-RF-027 | Um PO ou LTi deve poder sinalizar uma dependência em um artefato, vinculando-o ao artefato fornecedor em outra squad ou portfólio. | V3 RF01; V1 RF04 | 2 |
| PRD-RF-028 | Features de portfólios diferentes devem poder ser associadas à mesma Iniciativa. | V1 RF04 | 2 |
| PRD-RF-029 | Quando um artefato fornecedor atrasar, o sistema deve notificar os responsáveis pelos artefatos impactados e destacar o bloqueio nos quadros dos dois times. | V3 RF02 | 2 |
| PRD-RF-030 | O sistema deve oferecer um mapa global de dependências, com criticidade e prazo-limite por dependência. | V3 Épico 2 | 2 |
| PRD-RF-031 | O sistema deve manter rastreabilidade da Iniciativa até a Subtask; a matriz está detalhada na [especificação de Gestão de Testes][spec-qt]. | V1 Épico 5 | MVP (vínculos); 2 (matriz) |

### EP07 — Development & Release Management

| ID | Requisito | Origem | Fase |
|---|---|---|---|
| PRD-RF-032 | O sistema deve integrar-se a repositórios Git e pipelines de CI/CD para associar commits, PRs e builds aos artefatos. | V1 Épico 3; V2.1 EP07 | 2 |
| PRD-RF-033 | Na etapa final do fluxo de uma Feature, o sistema deve registrar plano de rollout, monitoramento e ativação por feature flag. | V1 RF05 | 2 |

O ciclo pós-deploy (estados Deployed, Stabilized e Value Realized; raias de sustentação; regressão contínua) está na [especificação de DevOps][spec-dv].

### EP08 — Enterprise Intelligence

| ID | Requisito | Origem | Fase |
|---|---|---|---|
| PRD-RF-034 | O sistema deve fornecer métricas de fluxo e painéis executivos nativos, conforme as especificações de [Analytics][spec-an] e [Dashboards][spec-db]. | V1 Épico 5; V2.1 EP08 | 2 (métricas e painéis padrão); 3 (construtor) |
| PRD-RF-035 | O sistema deve oferecer análises assistidas por IA. | V2.1 EP08 | 3 |

### EP09 e EP10

Sem requisitos próprios neste PRD. Valem os requisitos `RF-QT01–18` da [Gestão de Testes][spec-qt] e `RF-CE01–11` do [Customization Engine][spec-ce], com a ressalva de DEC-03.

### Acesso e identidade (D11)

| ID | Requisito | Origem | Fase |
|---|---|---|---|
| PRD-RF-036 | O login deve suportar SSO corporativo e, como alternativa, e-mail e senha com recuperação de senha. | V2.1 UI-02 | MVP |
| PRD-RF-037 | O login deve aplicar autenticação multifator conforme política configurável. | Segurança V2.0 RF-SC05; DEC-09 | MVP |
| PRD-RF-038 | Após autenticar, o usuário com acesso a mais de um workspace deve poder escolher em qual entrar. | V2.1 UI-02; DEC-09 | MVP |

## 11. Catálogo de telas

Os IDs originais foram mantidos. A numeração tem três esquemas (`UI`, `QT`, `DB`) e um intervalo vazio; unificá-la é uma pendência.

| ID | Tela | Domínio |
|---|---|---|
| UI-01 | Landing Page | — |
| UI-02 | Login | D11 |
| UI-03 | Executive Overview | D01 |
| UI-04 | Portfolio Management | D01 |
| UI-05 | Ideas & Opportunities | D01 |
| UI-06 | Initiative Workspace | D01 |
| UI-07 | Tactical Board | D02 |
| UI-08 | Feature Workspace | D02 |
| UI-09 | Capacity Planning | D02 |
| UI-10 | Living Documentation Hub | D04 |
| UI-11 | PRD Editor | D04 |
| UI-12 | Architecture & Specification (SAD e SSD) | D04 |
| UI-13 | UX/UI Workspace | D04 |
| UI-14 | QA Planning Workspace | D04 |
| UI-15 | Squad Workspace | D03 |
| UI-16 | Work Item Details | D03 |
| UI-17 | Governance Center | D05 |
| UI-18 | Traceability Matrix & Analytics | D06 |
| UI-19 a UI-24 | *não definidas em nenhum documento* | — |
| UI-25 | Configuration Center | D10 |
| UI-26 | Workflow Designer | D10 |
| UI-27 | Environment Manager | D10 |
| UI-28 | Template Builder | D10 |
| UI-29 | Gate & Rules Designer | D10 |
| UI-30 | Metadata & Fields Manager | D10 |
| UI-31 | Issue Type Designer | D10 |
| UI-32 | Configuration Governance | D10 |
| UI-33 | Analytics Overview | D09 |
| UI-34 | Flow Metrics | D09 |
| UI-35 | Bottleneck Analysis | D09 |
| UI-36 | Governance Analytics | D09 |
| UI-37 | Strategic Performance | D09 |
| UI-38 | Quality Analytics | D09 |
| UI-39 | Predictability Analytics | D09 |
| QT-01 | Quality Overview | D07 |
| QT-02 | Test Repository | D07 |
| QT-03 | Test Case Workspace | D07 |
| QT-04 | Test Planning | D07 |
| QT-05 | Test Sets | D07 |
| QT-06 | Test Execution | D07 |
| QT-07 | Test Runs | D07 |
| QT-08 | Requirements Traceability (RTM) | D07 |
| QT-09 | Defect & Quality Analytics | D07 |
| DB-01 | Dashboard Gallery | D09 |
| DB-02 | Dashboard Workspace | D09 |
| DB-03 | Dashboard Builder | D09 |
| DB-04 | Widget Library | D09 |
| DB-05 | Widget Configuration | D09 |
| DB-06 | Dashboard Sharing | D09 |
| DB-07 | Report Center | D09 |
| DB-08 | Scheduled Reports | D09 |
| DB-09 | Dashboard Administration | D09 |

**Sobreposições a resolver:**
- UI-18 (matriz de rastreabilidade e analytics) cobre o mesmo que QT-08 (RTM) e UI-33 (Analytics Overview).
- UI-14 (QA Planning Workspace) e QT-04 (Test Planning) descrevem o planejamento de testes em dois lugares.
- UI-38 (Quality Analytics) e QT-09 (Defect & Quality Analytics) têm o mesmo conteúdo.

Protótipos existentes: [UI-01](prototype/landing_page.png), [UI-02](prototype/login.png), [UI-03](prototype/home.png).

## 12. Decisões de consolidação

| ID | Contradição | Resolução | Ajuste necessário em |
|---|---|---|---|
| DEC-01 | O PRD V3 fazia o percentual de Features concluídas atualizar o KR; o Analytics V2.0 separa entrega de resultado de negócio. | Cada KR tem dois indicadores: Delivery Progress (calculado) e Business Outcome Progress (medido). O cálculo em cascata alimenta só o primeiro. | — (PRD-RF-006 e 007) |
| DEC-02 | Vários documentos prometem "tempo real"; a arquitetura de Analytics é assíncrona. | Indicadores agregados são "quase em tempo real", sempre com o horário da última atualização. A latência-alvo é um RNF pendente. | Dashboards V2.0 (RF-DB03), Gestão de Testes V2.0 (RF-QT02) |
| DEC-03 | A Gestão de Testes V2.0 mantém "vínculo unívoco" (RF-QT01) e exige muitos-para-muitos (RF-QT04). | Vale muitos-para-muitos. | Gestão de Testes V2.0 (reescrever RF-QT01) |
| DEC-04 | O Analytics V2.0 usa o Audit Log como fonte de eventos; a Segurança V2.0 o define como subsistema independente e de consulta restrita. | Um fluxo de eventos de domínio alimenta, separadamente, o Analytics e a trilha de auditoria. A trilha não é fonte analítica. **(a validar — decisão de arquitetura)** | Analytics V2.0 (diagrama da seção 3) |
| DEC-05 | O protótipo de login afirma "Em conformidade com SOC 2"; a Segurança V2.0 não assume certificação. | A plataforma suporta controles de conformidade e não alega certificação. O texto sai da tela. | [login.md](prototype/login.md), [login.png](prototype/login.png) |
| DEC-06 | Customization e Analytics eram ambos "Domínio 10". | Numeração D01–D12 da seção 7. | Customization V2.0, Analytics V2.0 |
| DEC-07 | A Taxonomia V2.0 chama o tipo de FL3 de "Épico / Iniciativa"; os PRDs, de "Iniciativa". | O tipo padrão é Iniciativa. "Épico" designa épicos de produto (seção 8) e pode ser usado como rótulo customizado. | Taxonomia V2.0 |
| DEC-08 | A Feature de Sustentação não aparece na hierarquia da Taxonomia V2.0. | Mantida como tipo de FL2. | Taxonomia V2.0 |
| DEC-09 | O blueprint de login não tem MFA nem seleção de workspace. | Ambos são requisitos da UI-02 (PRD-RF-037 e 038). | [login.md](prototype/login.md) |
| DEC-10 | O DevOps V1 criava Bug automaticamente a cada falha de regressão; o V2.0 classifica a falha antes. | A criação é configurável e ocorre depois da classificação da falha. | — (já está no DevOps V2.0) |

## 13. Faseamento proposto (a validar)

Os documentos anteriores não definiam MVP. A proposta abaixo prioriza o que sustenta o conceito central — hierarquia, gates e documentação no artefato — e adia o que depende de volume de dados ou de integrações.

| Fase | Entrega | Conteúdo |
|---|---|---|
| **MVP** | Um portfólio consegue planejar e executar com governança. | Hierarquia FL3–FL1 e quadros; Workspace de Iniciativa e de Feature com as cinco abas de documentação e versionamento; gates por campo, por documento e por aprovação (Arquitetura e QA); login com SSO e MFA; RBAC; trilha de auditoria. |
| **Fase 2** | Vários portfólios, qualidade e estratégia. | Dependências cruzadas e alertas; OKRs com os dois indicadores; capacity planning; Quality & Traceability; integração Git e CI/CD; métricas de fluxo e painéis padrão; Governance Center; ABAC e níveis de visibilidade entre portfólios. |
| **Fase 3** | Flexibilidade e escala corporativa. | Dashboard Builder e relatórios agendados; Customization Engine completo (herança, publicação, rollback); instalação Air-Gapped; previsões e IA assistida. |

## 14. Pendências e lacunas

**Domínios e capacidades sem especificação**
- OKRs: modelo, fórmula do índice de saúde, origem do valor medido.
- Dependências: tipos, ciclo de vida, cálculo de caminho crítico.
- Capacity Planning (UI-09).
- Governance Center (UI-17).
- Editor de documentação viva: formato, versionamento, edição concorrente.
- IA: nenhuma definição de casos de uso, dados usados ou provedores, apesar de citada em três documentos.

**Base técnica**
- Stack de tecnologia e modelo de dados do MVP: definidos no [SAD](SAD.md).
- Contratos de API do MVP: especificados no [SSD](SSD.md).
- Integrações: provedores de identidade, Git, CI/CD, observabilidade, importação de dados de ferramentas existentes.

**Requisitos não funcionais sem valor**

Os 16 RNFs das especificações de Segurança e de Hospedagem são qualitativos. Precisam de número: RPO e RTO por criticidade, meta de disponibilidade, latência de atualização dos indicadores (DEC-02), tempos de resposta, volume de artefatos e de usuários simultâneos.

**Transversais não tratados**
- Notificações, busca, internacionalização, acessibilidade.
- Modelo de multi-tenancy: a Hospedagem V2.0 cita apenas instância dedicada.

**Documentação**
- Critérios de aceitação para os requisitos deste PRD.
- Requisitos agrupados em faixas nas especificações (ex.: "RF-DH06 a RF-DH08") devem ser descritos um a um.
- Unificar a numeração de telas e definir ou eliminar UI-19 a UI-24.
- As telas do MVP estão especificadas em [UX-UI.md](UX-UI.md), que passa a ser a referência no lugar dos protótipos.
- Os blueprints em [prototype/](prototype/) são transcrições de conversa e citam imagens inexistentes (`image_0.png`, `image_4.png`); [home.md](prototype/home.md) descreve só o componente de perfil.
- A landing page usa depoimento, percentual e logotipos de clientes fictícios, e a alegação "primeira plataforma"; devem ser substituídos por conteúdo real antes de publicação.

## 15. Rastreabilidade dos IDs antigos

| ID antigo | Assunto | ID novo |
|---|---|---|
| V1 RF01 | Hierarquia dinâmica de demandas | PRD-RF-010 |
| V1 RF02 | Campos obrigatórios por transição | PRD-RF-022 |
| V1 RF03 | Upstream e Downstream por nível | PRD-RF-011 |
| V1 RF04 | Dependências e portfólios cruzados | PRD-RF-027, PRD-RF-028 |
| V1 RF05 | Lançamento e feature toggle | PRD-RF-033 |
| V2 RF01 | Hierarquia dinâmica de demandas | PRD-RF-010 |
| V2 RF02 | Documentação integrada | PRD-RF-018 |
| V2 RF03 | Herança de contexto operacional | PRD-RF-017 |
| V2 RF04 | Travas de gate por documentação | PRD-RF-023 |
| V3 RF01 | Mapeamento explícito de dependências | PRD-RF-027 |
| V3 RF02 | Alerta de caminho crítico | PRD-RF-029 |
| V3 RF03 | Cascade de progresso para OKRs | PRD-RF-006 (alterado por DEC-01) |

O PRD V2.1 não tinha requisitos numerados; seus épicos e telas estão nas seções 8 e 11.

[spec-it]: <pdr/Especificação de Taxonomia de Issues e Gestão de Produção V2.0.docx>
[spec-ce]: <pdr/Especificação do Motor de Customização e Templates V2.0.docx>
[spec-qt]: <pdr/Especificação de Gestão de Testes e Matriz de Rastreabilidade V2.0.docx>
[spec-dv]: <pdr/Especificação de DevOps e Operação Pós-Produção V2.0.docx>
[spec-an]: <pdr/Especificação do Módulo de Analytics e Métricas Nativas V2.0.docx>
[spec-db]: <pdr/Especificação de Dashboards e Painéis Padrão e Dinâmicos V2.0.docx>
[spec-sc]: <pdr/Especificação de Segurança, Confiabilidade e Governança de Dados V2.0.docx>
[spec-dh]: <pdr/Especificação de Arquitetura de Hospedagem e Deploy V2.0.docx>
