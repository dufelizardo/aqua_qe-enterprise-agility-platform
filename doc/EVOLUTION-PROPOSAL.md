# Proposta de evolução — AQuA-QE Enterprise Agility Platform

| | |
|---|---|
| **Versão** | 1.0 |
| **Data** | 2026-11-20 |
| **Status** | PROPOSTO — não aprovado para implementação |
| **Documentos-base** | [PRD v4.0](PRD.md), [SAD v1.0](SAD.md), [SSD v1.0](SSD.md), [UX/UI v1.0](UX-UI.md), [Revisão 01](REVIEW-01.md) |

Este documento consolida as melhorias identificadas nas rodadas de revisão arquitetural e as organiza em frentes de trabalho (F01 a F08c). É uma proposta de evolução e não altera automaticamente o PRD, o SAD, o SSD ou a UX/UI. Ele orienta a revisão desses documentos com rastreabilidade e controle de decisões. Os achados que motivam cada frente estão registrados com evidência na [Revisão 01](REVIEW-01.md).

---

## 1. Objetivo

Fortalecer a consistência entre produto, arquitetura, segurança, governança, experiência do usuário e contratos técnicos, reduzindo ambiguidades que levariam a interpretações diferentes durante a implementação.

A proposta não redefine o produto, não substitui a arquitetura e não introduz tecnologias sem necessidade. Ela busca:

- Resolver as lacunas arquiteturais e documentais identificadas.
- Tornar as regras de autorização e governança implementáveis e verificáveis.
- Garantir integridade e rastreabilidade dos registros.
- Alinhar os contratos de API às jornadas da UX/UI.
- Preservar a arquitetura modular e os princípios já estabelecidos.
- Estabelecer critérios objetivos para validar a prontidão da plataforma.

## 2. Princípios de evolução

1. **Preservação da visão de produto.** A plataforma continua sendo uma solução corporativa integrada de gestão de portfólio, fluxo de valor, governança e documentação viva.
2. **Evolução incremental.** As mudanças entram sem reescrever domínios que já estão coerentes.
3. **Monólito modular.** Nada de microsserviços, broker, CQRS ou Event Sourcing sem justificativa arquitetural demonstrável.
4. **Segurança efetiva.** Classificações, permissões e controles exibidos na interface correspondem ao comportamento real do sistema.
5. **Rastreabilidade.** Decisões, artefatos, aprovações, eventos e alterações mantêm relações verificáveis.
6. **Histórico imutável.** Mudanças de estado ou de política não apagam o contexto que justificou decisões anteriores.
7. **Separação entre proposta e decisão.** Nenhuma recomendação deste documento é requisito aprovado até passar pela validação formal.
8. **Coerência documental.** PRD, SAD, SSD e UX/UI permanecem alinhados, sem especificações concorrentes.

## 3. Status e rastreabilidade

### 3.1 Status das propostas

| Status | Significado |
|---|---|
| `PROPOSTO` | Recomendação documentada, ainda não aprovada. |
| `EM_REVISAO` | Em avaliação pelos responsáveis. |
| `APROVADO` | Decisão formalmente aceita. |
| `REJEITADO` | Decisão formalmente recusada, com justificativa. |
| `INCORPORADO` | Alteração registrada nos documentos oficiais. |
| `VALIDADO` | Especificação ou implementação verificada pelos critérios de aceite. |

Uma proposta incorporada aos documentos não está, por isso, implementada nem validada.

### 3.2 Identificação

As frentes usam os identificadores F01 a F08, com as subfrentes F08a, F08b e F08c. Os identificadores ficam estáveis durante todo o ciclo de revisão. A relação **achado (REV) → frente (F) → documento atualizado → validação ([QA](QA.md))** deve ser preservada.

## 4. Frentes de evolução

### F01 — Propriedade, participação e autorização

`PROPOSTO` · Prioridade crítica · Fundação · MVP · Achados: [REV-01](REVIEW-01.md#rev-01--regras-de-autorização-entre-unidades), [REV-04](REVIEW-01.md#rev-04--modelo-organizacional-adr-14)

**Problema.** As regras de acesso por unidade organizacional, hierarquia de artefatos e vínculos de atendimento podem permitir leitura indevida entre unidades. Propriedade do artefato, participação de uma unidade na execução e visibilidade do conteúdo não estão separadas.

**Proposta.** Três conceitos independentes:

1. **Propriedade:** a unidade responsável pelo artefato. É definida na criação, herdada do pai quando aplicável e alterada só por transferência formal.
2. **Participação:** unidades ou atores formalmente envolvidos na execução. É explícita, revogável e auditável.
3. **Visibilidade:** quem pode consultar ou operar o artefato, considerando propriedade, participação, papel e classificação.

Modelo conceitual mínimo (nomes e tipos a validar no SSD):

```text
artifact_participation
- id, artifact_id, org_unit_id, role, permission_scope,
  include_descendants, granted_by, granted_at, expires_at, status
```

**Regras.**
- A participação não concede automaticamente todas as permissões e não altera a propriedade.
- A propagação para descendentes precisa ser autorizada explicitamente e para na fronteira de propriedade: um descendente de outra unidade não herda a participação.
- A transferência de propriedade reavalia participações, permissões e visibilidade.
- O acesso por participação respeita escopo, prazo e estado; participações expiradas ou revogadas não concedem nada.
- Ser aprovador designado de um gate não dá acesso irrestrito ao artefato, só às informações necessárias à aprovação.

**Explicação de acesso.** Operação equivalente a `GET /artifacts/{artifactId}/access-explain`, que explica as permissões efetivas do usuário autenticado e a origem de cada concessão. A consulta das permissões de terceiros exige privilégio administrativo e é auditada.

**Critérios de aceite.**
- Matriz de autorização cobrindo unidade proprietária, participante, descendente, papel, operação e classificação.
- Testes positivos e negativos automatizados ([QA, seção 7](QA.md#7-suítes-prioritárias-do-mvp)).
- Nenhum acesso cruzado indevido entre unidades proprietárias.
- Transferências preservam o histórico e reavaliam as permissões.

**Documentos afetados:** PRD, SAD, SSD, UX/UI.

### F02 — Independência e segregação de aprovações

`PROPOSTO` · Prioridade crítica · Governança · MVP · Origem: [SSD, seção 10](SSD.md#10-pendências) ("Segregação de funções")

**Problema.** No MVP, quem edita um documento pode ter o mesmo papel de quem o aprova ([SSD, seção 8.4](SSD.md#84-templates-de-documento-a-validar)). Uma enumeração simples de políticas não representa restrições que podem coexistir.

**Proposta.** Política de aprovação composta, configurável por tipo de artefato, documento ou gate, com restrições independentes e combináveis:
- se o autor pode aprovar;
- quantidade mínima de aprovadores e exigência de aprovadores distintos;
- separação entre executor da transição e aprovador;
- papéis exigidos e regras de segregação de funções;
- condições para exceções formalmente autorizadas.

Cada decisão fica ligada à versão exata do documento e à política aplicada:

```text
approval_record
- document_version_id, content_hash, approval_policy_version_id,
  gate_rule_id, approver_id, decision, timestamp, comment
```

**Regras.**
- Por padrão, o autor não aprova a própria versão.
- Uma nova versão não apaga aprovações anteriores; as incompatíveis são marcadas como superadas (o SSD já prevê o marcador `superseded` em `approval`).
- Mudanças de política não alteram decisões passadas.
- Uma exceção tem justificativa, responsável, validade e escopo, e fica ligada obrigatoriamente ao artefato e ao `gate_rule_id`. Ela não libera a regra para outros artefatos ou gates.

**Critérios de aceite.**
- Não é possível aprovar a própria versão quando a política proíbe.
- Não é possível reutilizar uma aprovação dada a outra versão.
- Exceções são rastreáveis e limitadas ao escopo autorizado.
- A interface mostra quem pode aprovar e por quê (ver F06).

**Documentos afetados:** PRD, SAD, SSD, UX/UI.

### F03 — Classificação da informação com proteção efetiva

`PROPOSTO` · Prioridade crítica · Segurança · MVP · Origem: [SSD, seção 6.1](SSD.md#61-estrutura) e Especificação de Segurança V2.0 (RF-SC11, RF-SC12)

**Problema.** Uma classificação exibida como controle de segurança precisa corresponder a uma política efetiva. Hoje o SSD diz que, no MVP, a classificação "é apenas registrada".

**Proposta.** Manter as quatro classificações no modelo e habilitar no MVP só as que têm política determinística:

| Classificação | Seleção no MVP | Política |
|---|---|---|
| `PUBLIC` | Indisponível | Política de publicação a definir (Fase 2). |
| `INTERNAL` | Disponível | RBAC organizacional, propriedade e participação. |
| `CONFIDENTIAL` | Indisponível | Autorização explícita a definir (Fase 2). |
| `RESTRICTED` | Disponível | Concessão específica e auditoria reforçada. |

**Regras.**
- `RESTRICTED` exige concessão explícita e auditável; a participação sozinha não dá leitura.
- A classificação de um documento pode ser mais restritiva que a do artefato, nunca menos.
- A política vale para visualização, anexos e downloads, busca, notificações, exportações, APIs, histórico e comparação de versões, pré-visualizações e compartilhamentos.
- A auditoria de leitura `RESTRICTED` registra a ocorrência e o contexto, sem copiar o conteúdo.
- Rebaixar uma classificação passa por governança.

**Critérios de aceite.** Cada classificação selecionável produz comportamento verificável. Não há caminho alternativo (busca, notificação, anexo, exportação) que exponha conteúdo protegido.

**Documentos afetados:** PRD, SAD, SSD, UX/UI. Ver a [divergência D-02](#9-divergências-com-os-documentos-atuais).

### F04 — Outbox, auditoria e reconciliação

`PROPOSTO` · Prioridade alta · Integridade · MVP · Achado: [REV-03](REVIEW-01.md#rev-03--garantias-da-auditoria-assíncrona)

**Problema.** A outbox transacional é adequada (ADR-06), mas faltam regras operacionais de ordenação, reprocessamento, integridade da cadeia de auditoria e recuperação de falhas.

**Proposta.** Manter a outbox no monólito, sem broker no MVP, e enriquecer o envelope ([SSD, seção 7.1](SSD.md#71-envelope)) com:

```text
event_id, aggregate_id, aggregate_type, aggregate_seq,
occurred_at, schema_version, event_type, payload
```

**Processamento.**
- Consumidor de auditoria serializado no MVP, preservando a cadeia global de hash (SAD, seção 7.5).
- `aggregate_seq` serve para verificar a ordem por agregado; não substitui a ordem global da cadeia.
- Nenhuma paralelização antes de um gargalo demonstrado e de uma estratégia compatível com a integridade global.
- Idempotência por `event_id` (já prevista no SSD); tratamento definido para eventos duplicados, atrasados ou fora de ordem; nova tentativa controlada.

**Reconciliação.** Comparar os eventos produzidos (registro transacional de controle) com os processados, sem inferir a sequência a partir do estado atual do agregado. Detectar lacunas, divergências e pendências, alertar e reprocessar com segurança.

**Eventos com falha persistente.** Uma estrutura persistente (DLQ) recebe os eventos que esgotam as tentativas **dos consumidores que não sejam a auditoria**. Para o consumidor de auditoria, ver a [divergência D-03](#9-divergências-com-os-documentos-atuais). O reprocessamento exige permissão administrativa específica, preserva o evento original, registra responsável, motivo e horário, é idempotente e não altera o histórico em silêncio.

**Ancoragem.** Exportação periódica de evidências da cadeia de auditoria para armazenamento protegido. Armazenamento compatível com S3 não garante imutabilidade sozinho: são necessários controles contra sobrescrita e exclusão e credenciais segregadas. Em on-premise, alternativa equivalente, como arquivo assinado em ambiente protegido.

**Observabilidade e metas iniciais (a validar com testes de carga):** p95 do processamento de auditoria abaixo de 5 s e p99 abaixo de 30 s. As metas não substituem dimensionamento.

**Critérios de aceite.**
- Eventos persistidos atomicamente com a transação de negócio.
- Cadeia de hash verificável; duplicatas não geram registros duplicados.
- Lacunas de sequência detectáveis.
- Falhas recuperáveis de forma auditável, sem alterar a cadeia.

**Documentos afetados:** SAD, SSD, PRD.

### F05 — Versionamento e compatibilidade de workflows

`PROPOSTO` · Prioridade alta · Governança · MVP · Achado: [REV-06](REVIEW-01.md#rev-06--publicação-de-nova-versão-de-workflow)

**Problema.** Mudanças em workflows afetam artefatos existentes quando status, transições ou gates são removidos ou alterados. Migrar automaticamente pode comprometer o histórico e as decisões de governança.

**Proposta.** Cada artefato referencia uma versão imutável do workflow (`artifact.workflow_version_id`). Publicar uma nova versão **não** migra os artefatos existentes. Ver a [divergência D-01](#9-divergências-com-os-documentos-atuais).

**Regras.**
- Versões publicadas são imutáveis; cada transição registra a versão vigente.
- Há validação de compatibilidade entre versões; status removidos exigem mapeamento explícito.
- Mudanças de gate consideram as aprovações já registradas.
- Migrações são explícitas, idempotentes e transacionais por artefato, e preservam o contexto da versão anterior.
- Rollback é feito publicando uma nova versão, nunca reativando uma versão histórica.

**Faseamento.**
- MVP: versionamento imutável, vínculo do artefato à versão, validação de compatibilidade, preservação do histórico.
- Depois: migração assistida em massa, `GET /workflows/{workflowId}/migration-preview?to={version}` com o impacto em status, transições, gates e aprovações, e estratégias de rollback operacional.

**Critérios de aceite.**
- Workflows publicados não mudam retroativamente.
- Artefatos existentes permanecem na versão original.
- Nenhuma migração é silenciosa.
- Reexecutar uma migração não gera inconsistência.

**Documentos afetados:** PRD, SAD, SSD, UX/UI.

### F06 — Contratos de colaboração, APIs e rastreabilidade com a UX/UI

`PROPOSTO` · Prioridade alta · Integração entre domínios · MVP · Achados: [REV-05](REVIEW-01.md#rev-05--contrato-do-documento-independente-do-editor), [REV-07](REVIEW-01.md#rev-07--consulta-de-usuários-para-quem-edita) a [REV-11](REVIEW-01.md#rev-11--exportação-de-documentos)

**Problema.** Há lacunas entre os contratos de API e as jornadas da UX/UI. Algumas operações exigem permissão administrativa demais; outras não estão especificadas ([UX/UI, seção 13](UX-UI.md#13-pendências)).

**Proposta.** Uma matriz de rastreabilidade como entregável obrigatório da frente:

| Elemento | Conteúdo |
|---|---|
| Requisito | Necessidade funcional (PRD-RF). |
| Jornada | Caso de uso e contexto. |
| UX/UI | Tela, componente e interação. |
| API | Endpoint e operação. |
| Segurança | Permissão exigida. |
| Aceite | Comportamento verificável e caso de teste ([QA](QA.md)). |

A matriz identifica conflitos, duplicidades e lacunas antes da definição final dos contratos.

**Contratos de colaboração a avaliar** (exemplos, a confrontar com o SSD):

```text
GET /users/search?q=&scope=
GET /artifacts/{artifactId}/attachments
GET /artifacts/{artifactId}/gates/{gateId}/eligible-approvers
```

Esses contratos respeitam o contexto organizacional, retornam só o necessário, paginam quando aplicável, excluem usuários inativos, respeitam permissões e classificações e informam o motivo da elegibilidade. A busca de usuários não pode depender de permissão administrativa ampla.

**Documentação viva.** O modelo estruturado do SSD (seções, blocos, templates, versões) continua sendo o formato canônico. Markdown pode ser formato de importação e exportação, representação textual e conteúdo de blocos permitidos, mas não substitui o modelo de persistência.

**Telas.** Avaliar os pares apontados como sobrepostos no [PRD, seção 11](PRD.md#11-catálogo-de-telas): **UI-18 e QT-08** (matriz de rastreabilidade) e **UI-38 e QT-09** (analytics de qualidade). A decisão de fundir ou não sai da análise das jornadas, não é presumida.

**Critérios de aceite.**
- Matriz preenchida para os casos de uso do escopo.
- Endpoints necessários documentados, com a permissão de cada operação.
- Usuários inativos não aparecem como elegíveis; anexos são listados respeitando autorização.
- Modelo estruturado de documentos permanece canônico.
- Divergências entre UX/UI e SSD têm resolução documentada.

**Documentos afetados:** PRD, SSD, UX/UI e SAD quando houver impacto arquitetural.

### F07 — Ciclo de vida da identidade federada

`PROPOSTO` · Prioridade alta · Segurança · MVP · Origem: RF-SC16 e RF-SC18; [SSD, seção 10](SSD.md#10-pendências) ("Eventos de autenticação")

**Problema.** Logout, desativação de conta, revogação de papel e expiração de token são eventos diferentes, e o estado no provedor de identidade precisa estar alinhado ao estado local.

**Proposta.** Mecanismo de sincronização entre o Keycloak e o estado local, cobrindo desativação de usuários, revogação de sessões, invalidação prática de tokens, revogação de papéis e concessões, sincronização de usuários federados e reconciliação periódica.

O Keycloak oferece o Event Listener SPI, mas não tem um webhook nativo universal para todas as operações administrativas; capturar esses eventos pode exigir um listener próprio.

**Regras.**
- Usuários inativos não autenticam nem executam operações protegidas; a autorização considera o estado local do usuário.
- Encerrar sessões e refresh tokens não invalida JWTs autocontidos já emitidos. A estratégia de revogação considera a validade dos tokens e a verificação de estado pelo sistema.
- A reconciliação periódica complementa a atualização tempestiva, não a substitui.
- A desativação não apaga autoria, aprovações nem auditoria.

**Faseamento.** O MVP traz bloqueio local de inativos, estratégia de revogação documentada, sincronização, reconciliação e auditoria. Uma meta de propagação abaixo de 1 minuto só pode ser adotada depois de definidos o mecanismo de eventos e a estratégia de validação de tokens; um job diário não a garante.

**Critérios de aceite.**
- Usuários desativados não executam operações protegidas e não aparecem nas buscas de responsáveis e aprovadores.
- Mudanças de estado são auditáveis.
- O histórico de autoria e aprovação permanece íntegro.

**Documentos afetados:** SAD, SSD, PRD.

### F08a — Contexto organizacional consistente

`PROPOSTO` · Fundação · MVP · Achados: [REV-01](REVIEW-01.md#rev-01--regras-de-autorização-entre-unidades), [REV-04](REVIEW-01.md#rev-04--modelo-organizacional-adr-14)

**Problema.** Todos os módulos precisam interpretar o contexto organizacional da mesma forma para não divergirem em autorização.

**Proposta.** Um contexto organizacional único, reutilizado por todos os domínios, com:
- usuário autenticado e estado da identidade;
- unidade organizacional, papéis e concessões;
- propriedade, participações e escopo de autorização;
- classificação da informação.

O SAD já centraliza a decisão de acesso no módulo `access` ([SAD, seção 7.2](SAD.md#72-autorização)); esta frente define o que entra nessa decisão e impede que um módulo a contorne.

**Critérios de aceite.**
- Modelo documentado e regras compartilhadas.
- Testes de consistência entre domínios.
- Mudanças organizacionais propagadas de forma controlada.

**Documentos afetados:** SAD, SSD, PRD.

### F08b — Retenção, arquivamento e ciclo de vida dos dados

`PROPOSTO` · Decisão de produto · MVP mínimo · Origem: RF-SC14; [SSD, seção 10](SSD.md#10-pendências) ("Limites operacionais")

**Problema.** Falta distinguir exclusão, arquivamento, desativação e retenção, o que é essencial para preservar auditoria, documentação, aprovações e rastreabilidade.

**Proposta.** Políticas de ciclo de vida por categoria:
- artefatos ativos e arquivados;
- documentos e versões, aprovações e decisões, anexos;
- identidades e papéis históricos;
- eventos e auditoria;
- dados temporários e operacionais.

**Regras.**
- Arquivar não é excluir; desativar um usuário não elimina sua autoria.
- Exclusões respeitam dependências e registros de auditoria e são controladas e auditáveis.
- A retenção considera obrigações contratuais, legais (LGPD) e regulatórias e é configurável por implantação quando aplicável.

**Documentos afetados:** PRD, SAD, SSD.

### F08c — Observabilidade e operação por domínio

`PROPOSTO` · Evolução incremental · MVP e fases seguintes · Achado relacionado: [REV-13](REVIEW-01.md#rev-13--baseline-de-segurança-por-framework) (função Detect)

**Problema.** A operação precisa de visibilidade suficiente para detectar falhas, gargalos e degradação.

**Proposta.** Métricas, logs e rastreamento sobre o que já está previsto (Spring Boot Actuator; [SAD, seção 7.9](SAD.md#79-observabilidade-e-operação)), sem infraestrutura nova sem justificativa. Capacidades:
- saúde dos módulos e tempo de resposta das operações críticas;
- falhas de autorização e erros de integração com o IdP;
- atraso e falhas da outbox e do processamento de auditoria;
- falhas de workflow;
- consumo de recursos.

**Regras.**
- Logs não expõem informação classificada nem dados sensíveis.
- Eventos de segurança são identificáveis.
- As métricas permitem diagnóstico por domínio.
- Todo alerta tem critério e responsável.

**Documentos afetados:** SAD, SSD.

## 5. Priorização e dependências

As frentes não seguem a ordem numérica; algumas são fundação de outras.

| Etapa | Frentes | Objetivo |
|---|---|---|
| 1 · Fundação | F01, F07, F08a | Autorização, identidade e contexto organizacional. Sem elas, acesso e colaboração ficam inconsistentes. |
| 2 · Governança e proteção | F02, F03, F08b | Independência das aprovações, classificação e ciclo de vida dos dados. |
| 3 · Integridade e evolução | F04, F05 | Outbox, auditoria, reconciliação e versionamento de workflows. |
| 4 · Contratos e experiência | F06 | Rastreabilidade entre requisitos, jornadas, UX/UI, APIs e permissões. |
| 5 · Prontidão operacional | F08c + validação transversal | Observabilidade, testes integrados, segurança e recuperação. |

Uma etapa não precisa terminar para a seguinte começar, desde que as dependências sejam respeitadas.

## 6. Documentos impactados

| Documento | Principais evoluções |
|---|---|
| PRD | Regras de negócio, governança, classificação, retenção e critérios funcionais. |
| SAD | Autorização, identidade, eventos, segurança, operação e novas ADRs. |
| SSD | Modelo de dados, APIs, políticas, workflows e comportamento técnico. |
| UX/UI | Jornadas, permissões, elegibilidade, classificação e interação com gates. |
| QA | Casos de teste derivados dos critérios de aceite de cada frente. |

As decisões de impacto arquitetural viram ADRs no [SAD, seção 10](SAD.md#10-decisões-de-arquitetura), por exemplo:
- modelo de autorização e fronteira de propriedade;
- processamento serializado da auditoria;
- revogação de identidade;
- versionamento de workflows;
- proteção de documentos classificados.

Cada ADR registra contexto, decisão, alternativas, consequências e status. Uma ADR descrita não é uma decisão aceita: o status precisa refletir a aprovação real.

## 7. Relação entre achados e frentes

| Achado | Assunto | Frente |
|---|---|---|
| [REV-01](REVIEW-01.md#rev-01--regras-de-autorização-entre-unidades) | Autorização entre unidades | F01, F08a |
| [REV-02](REVIEW-01.md#rev-02--contexto-herdado-vazio-entre-ramos) | Contexto herdado vazio entre ramos | **nenhuma** (seção 8) |
| [REV-03](REVIEW-01.md#rev-03--garantias-da-auditoria-assíncrona) | Garantias da auditoria assíncrona | F04 |
| [REV-04](REVIEW-01.md#rev-04--modelo-organizacional-adr-14) | Modelo organizacional (ADR-14) | F01, F08a |
| [REV-05](REVIEW-01.md#rev-05--contrato-do-documento-independente-do-editor) | Contrato do documento independente do editor | F06 |
| [REV-06](REVIEW-01.md#rev-06--publicação-de-nova-versão-de-workflow) | Nova versão de workflow | F05 |
| [REV-07](REVIEW-01.md#rev-07--consulta-de-usuários-para-quem-edita) | Consulta de usuários | F06, F07 |
| [REV-08](REVIEW-01.md#rev-08--listagem-de-anexos) | Listagem de anexos | F06, F03 |
| [REV-09](REVIEW-01.md#rev-09--consulta-de-quem-pode-aprovar) | Quem pode aprovar | F06, F02 |
| [REV-10](REVIEW-01.md#rev-10--preferências-do-usuário) | Preferências do usuário | F06 |
| [REV-11](REVIEW-01.md#rev-11--exportação-de-documentos) | Exportação de documentos | F06, F03 |
| [REV-12](REVIEW-01.md#rev-12--campos-customizados-usados-em-gates) | Campos customizados em gates | **nenhuma** (seção 8) |
| [REV-13](REVIEW-01.md#rev-13--baseline-de-segurança-por-framework) | Baseline de segurança | parcial: F08c (seção 8) |
| [Q-01](REVIEW-01.md#3-questões-em-aberto) | Nome AQuA-QE e "integração futura" | **nenhuma** (seção 8) |

Frentes que não nascem de um REV, mas de pendências já registradas nos documentos:

| Frente | Origem |
|---|---|
| F02 | SSD, seção 10, "Segregação de funções"; SSD, seção 8.4. |
| F03 | SSD, seção 6.1 (classificação só registrada no MVP); RF-SC11 e RF-SC12. |
| F07 | RF-SC16 e RF-SC18; SSD, seção 10, "Eventos de autenticação". |
| F08b | RF-SC14; SSD, seção 10, "Limites operacionais". |

## 8. Lacunas desta proposta

| Achado | O que falta | Sugestão |
|---|---|---|
| REV-02 | Nenhuma frente trata o contexto herdado quando a Iniciativa está em outro ramo. | Incluir em F01 a regra de leitura de resumo dos ancestrais (título, status, início da descrição e status dos documentos). |
| REV-12 | `customFields` livres citados em gates. | Incluir em F05 ou F06 a exigência de declarar o campo no tipo de artefato antes de usá-lo em gate ou filtro. |
| REV-13 | Baseline ASVS L2, SSDF e mapeamento ao NIST CSF 2.0. A F08c cobre só parte da função Detect. | Nova frente F09, ou incorporar em F08c com escopo ampliado. A verificação ASVS já está prevista no [QA](QA.md). |
| Q-01 | Esclarecer a "integração futura com AQuA-QE" citada na análise externa. | Decisão do responsável pelo produto. |

## 9. Divergências com os documentos atuais

Não são erros da proposta. São mudanças de direção que precisam de decisão explícita antes da incorporação.

| ID | Frente | Documento atual | Divergência |
|---|---|---|---|
| D-01 | F05 | [SSD, seção 3.3](SSD.md#33-configuration); [UX/UI, seção 7.10](UX-UI.md#710-administração) | Hoje os artefatos existentes **passam** à nova versão do workflow, preservando o status pela `key`. A F05 os mantém na versão original. Isso também muda a recomendação 1 do REV-06 ("gates avaliados com a versão vigente"). |
| D-02 | F03 | [SSD, seção 6.1](SSD.md#61-estrutura) | Hoje a classificação é só registrada no MVP e as políticas entram com o ABAC na Fase 2. A F03 traz `INTERNAL` e `RESTRICTED` efetivos para o MVP. É aumento de escopo do MVP (ver seção 11, cuidado 1). |
| D-03 | F04 | [SAD, seção 7.5](SAD.md#75-trilha-de-auditoria); REV-03 | Desviar um evento de auditoria para a DLQ e seguir em frente deixa lacuna na trilha e grava a cadeia de hash fora da ordem de ocorrência. Para o consumidor de auditoria, a recomendação do REV-03 é **parar e alertar**; a DLQ fica para os demais consumidores. |
| D-04 | F01 | ADR-14; [SSD, seção 3.2](SSD.md#32-organization) | A F01 introduz `artifact_participation`; a ADR-14 já tem vínculos de atendimento entre unidades. Falta definir se a participação substitui o vínculo, o complementa (vínculo entre unidades, participação por artefato) ou se um deriva do outro. |

## 10. Validação

A evolução documental só termina quando as propostas viram especificações verificáveis. A estratégia de testes, as ferramentas (Robot Framework no padrão LKDF para API e UI) e as suítes por frente estão no [Plano de QA](QA.md). Em resumo:

- **Consistência documental:** sem regras contraditórias entre PRD, SAD, SSD e UX/UI; propostas não aprovadas continuam marcadas como propostas.
- **Funcional:** jornadas, gates e aprovações, classificações, arquivamento e retenção com comportamento definido.
- **Segurança:** autorização positiva e negativa, isolamento entre unidades, documentos e anexos, usuários inativos, segregação de funções, tentativas por API, busca e exportação.
- **Integridade:** cadeia de auditoria verificável, eventos duplicados e fora de ordem, reprocessamento seguro, histórico de aprovação, versionamento de workflows.
- **Operacional:** recuperação após falhas, observabilidade, backup e restauração, atualização, condições de implantação SaaS, on-premise e air-gapped.

**Critérios de encerramento.**
1. Todas as frentes avaliadas e decididas.
2. Aprovadas incorporadas aos documentos oficiais; rejeitadas com justificativa.
3. Matriz de rastreabilidade atualizada.
4. Conflitos entre documentos resolvidos.
5. Critérios de aceite documentados.
6. Impacto nas fases avaliado e dependências registradas.
7. Testes e evidências definidos para os requisitos críticos.

A conclusão documental e a validação da implementação são acompanhadas separadamente.

## 11. Cuidados na incorporação

1. **Prioridade arquitetural não é prioridade de entrega.** Nem todo requisito proposto precisa entrar no MVP.
2. **Exemplos de entidades e endpoints não são contratos.** Precisam ser comparados ao modelo atual do SSD antes de adotados.
3. **Um achado só fecha com evidência.** A existência de uma proposta não encerra o REV correspondente; ele fecha quando houver resolução documental ou técnica que atenda ao critério de aceite.

## 12. Decisões pendentes

| ID | Decisão |
|---|---|
| D01 | Modelo definitivo de propriedade e participação (inclui D-04). |
| D02 | Composição e configuração das políticas de aprovação. |
| D03 | Tratamento de `PUBLIC` e `CONFIDENTIAL` e escopo de classificação no MVP (inclui D-02). |
| D04 | Cadeia global de auditoria, requisitos de desempenho e tratamento de falha persistente (inclui D-03). |
| D05 | Limites de versionamento e migração de workflows (inclui D-01). |
| D06 | Contratos finais de colaboração e matriz UX/UI. |
| D07 | Estratégia técnica de sincronização e revogação de identidade. |
| D08 | Políticas de retenção e arquivamento. |
| D09 | Metas operacionais e requisitos de observabilidade. |

Esses itens são resolvidos pelos responsáveis pelo produto e pela arquitetura antes de qualquer frente ser tratada como baseline.

## 13. Considerações finais

O principal risco não está na escolha de uma tecnologia. Está em módulos diferentes implementarem interpretações diferentes de propriedade, autorização, classificação, aprovação e histórico. As frentes deste documento buscam eliminar essas ambiguidades, preservando a arquitetura modular, a documentação viva, a estrutura organizacional configurável e a visão de produto.
