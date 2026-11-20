# Proposta 03: administração corporativa e configuração da plataforma

| | |
|---|---|
| **Versão** | 1.0 |
| **Data** | 2026-11-20 |
| **Status** | PROPOSTO, fora do MVP. Decisões tomadas na seção 4; as demais estão abertas. |
| **Série** | 01: [Proposta de evolução](EVOLUTION-PROPOSAL.md) · 02: [Proposta de experiência corporativa](EXPERIENCE-PROPOSAL.md) · 03: esta |
| **Documentos-base** | [PRD v4.0](PRD.md), [SAD v1.0](SAD.md), [SSD v1.0](SSD.md), [UX/UI v1.0](UX-UI.md), [Revisão 01](REVIEW-01.md) |
| **Inspiração** | Área administrativa do Atlassian, usada só como referência. A arquitetura e o modelo de domínio do AQuA-QE determinam a implementação. |

A proposta pede uma área de **administração corporativa** organizada por responsabilidade: organização, pessoas e acesso, equipes, módulos e integrações, segurança e governança, e área comercial. O texto original foi recebido em 2026-11-20 e está resumido na seção 2. A seção 3 o confronta com o que já existe, e as seções 4 e 5 registram as decisões tomadas e as que continuam abertas.

Ela não altera o PRD e não muda o roadmap.

---

## 1. Princípio adotado: três níveis de configuração

A proposta separa três tipos de configuração, cada um com responsável, escopo e autorização próprios. O princípio foi **adotado** e está registrado na [UX/UI, seção 7.11](UX-UI.md#711-níveis-de-configuração):

- **Pessoal:** preferências de cada usuário.
- **Organizacional:** estrutura, pessoas e regras da organização, administradas por quem tem o papel na unidade.
- **Plataforma:** módulos, integrações, segurança global e parâmetros da instalação.

A área comercial, o quarto contexto da proposta, fica fora (ADM-08).

## 2. Proposta original (resumo)

| Seção | Conteúdo | Prioridade no original |
|---|---|---|
| **3.1 Organização** | Dados da organização, unidades, hierarquia, relação com equipes, responsáveis, políticas, colaboração e visibilidade. | Crítica |
| **3.2 Pessoas e acesso** | Usuários, grupos, papéis, permissões, solicitações de acesso, ativar e desativar usuários, associação a unidades e equipes, consulta de acessos, histórico de permissões. Integração com Keycloak e IdPs. | Crítica |
| **3.3 Equipes** | Tipos de equipe, políticas de participação (aberta, fechada, administrada), membros, responsáveis, campos personalizados, notificações. | Alta |
| **3.4 Módulos e integrações** | Consultar, ativar e desativar módulos conforme licença; configurar módulos; gerenciar integrações, conexões e credenciais. O original adverte que nem todo módulo pode ser ligado em tempo de execução. | Crítica |
| **3.5 Segurança e governança** | Autenticação, senha, MFA, IdPs, sessão, políticas de acesso, auditoria administrativa, histórico, retenção, privacidade e eventos de segurança. | Crítica |
| **3.6 Comercial** | Plano, assinaturas, licenças, consumo e faturamento. Depende do modelo comercial. | Condicional |
| **5. Navegação** | Engrenagem no cabeçalho abrindo a Administração, com as seções acima. | — |
| **6. Papéis administrativos** | Organization, User Access, Team, Product, Security, Platform e Billing Administrator. Os nomes eram conceituais no original. | — |
| **7. Requisitos não funcionais** | RBAC, segregação, auditoria das operações administrativas, proteção de credenciais, validação de dependências, confirmação em operações críticas, compatibilidade SaaS e on-premises, ambientes isolados. | — |

## 3. Confronto com o que já existe

| Seção | O que já existe | Avaliação |
|---|---|---|
| 3.1 Organização | **Implementada:** `/admin/organizacao` ([UX/UI 7.10](UX-UI.md#710-administração)), endpoints do [SSD 3.2](SSD.md#32-organization), [ADR-14](SAD.md#10-decisões-de-arquitetura) e cenários do [SSD 2.1](SSD.md#21-escopo-entre-unidades-rev-01-rev-02-rev-04). | **Já coberta.** "Dados da organização" é a unidade raiz. A visibilidade é o SSD 2.1 hoje, mais Private, Shared e Enterprise na Fase 2. A relação unidade × equipe não existe, por decisão (EXP-02). |
| 3.2 Pessoas e acesso | [SSD 3.1](SSD.md#31-identity-e-access): `GET /users`, `GET /roles`, `GET /org-units/{id}/role-assignments`, `POST /role-assignments` e `DELETE /role-assignments/{id}`; a seção "Usuários e papéis" da UX/UI 7.10. A conta tem `active`. | Atribuir, consultar e revogar papéis **já está especificado para o MVP**; falta implementar. **Grupos**, se surgirem, vêm do Keycloak ou do LDAP federado, nunca de um cadastro paralelo. **Solicitação de acesso** é nova e fica pós-MVP. O **histórico de permissões** sai da trilha de auditoria (eventos `role_assignment.*`). |
| 3.3 Equipes | [EXP-02](EXPERIENCE-PROPOSAL.md#4-decisões-tomadas): time = unidade organizacional. | Contradiz decisão tomada. Os tipos de equipe são os níveis organizacionais, e os membros são as atribuições de papel. A seção e o "Team Administrator" saem (ADM-03). |
| 3.4 Módulos e integrações | [ADR-01](SAD.md#10-decisões-de-arquitetura): monólito modular, um único artefato. O módulo `integration` da Fase 2 tem cofre de credenciais ([SAD 5.2](SAD.md#52-módulos-das-fases-seguintes); RF-SC13). | Ligar e desligar módulos em tempo de execução **não**: os módulos são fronteiras de código, não plugins. Cabe um **feature flag por instalação**, definido no deploy, que esconde o que a instalação não usa (ADM-05). Integrações seguem na Fase 2. |
| 3.5 Segurança e governança | [ADR-08](SAD.md#10-decisões-de-arquitetura): autenticação, MFA, senha e sessão ficam no Keycloak, e a aplicação só fala OIDC. Auditoria: PRD-RF-026, módulo `audit` (MVP), seção Auditoria da UX/UI 7.10. Privacidade e retenção: [REV-14](REVIEW-01.md#rev-14--privacidade-e-dados-pessoais). | Políticas de autenticação **não entram** no produto: a Administração leva ao console do Keycloak. A auditoria administrativa já é MVP, mas o catálogo de eventos não tinha eventos da estrutura organizacional (seção 6). |
| 3.6 Comercial | [ADR-13](SAD.md#10-decisões-de-arquitetura): uma instalação por cliente; a cobrança acontece fora do produto. | Fora do produto (ADM-08). |
| 5. Engrenagem | [UX/UI 4](UX-UI.md#4-estrutura-da-aplicação): item Admin no menu lateral, só para `PLATFORM_ADMIN`. Já implementado. | Mantido no menu lateral (ADM-01). |
| 6. Sete papéis | Um único `PLATFORM_ADMIN` ("gerir configuração e papéis", [SSD 2](SSD.md#2-papéis-e-permissões)) **com escopo por unidade**: quem o tem numa unidade administra só a subárvore dela (regra 1 do SSD 2.1). | O administrador delegado de uma parte da organização **já existe**, pelo escopo. Team Admin e Billing Admin saem. Security Admin é o administrador do realm no Keycloak (ADM-02). |
| 7. Não funcionais | RBAC ([SAD 7.2](SAD.md#72-autorização)), auditoria (7.5), segregação de funções (pendência do SSD 10; F02 da proposta 01), segredos (7.10). | Coerentes. Trazem dois pontos novos: **confirmação** em operação crítica e **validação de dependências** antes de mudança estrutural (seção 6). |

**Ajustes de forma:** as decisões do original eram DEC-01 a DEC-09, que colidem com as DEC do PRD. Aqui são **ADM-01 a ADM-09**.

## 4. Decisões tomadas

| ID | Tema | Decisão (2026-11-20) |
|---|---|---|
| ADM-01 | Entrada da Administração | Continua no item **Admin** do menu lateral (UX/UI 4). Sem engrenagem no cabeçalho. |
| ADM-02 | Papéis administrativos | Um único `PLATFORM_ADMIN`, **com escopo por unidade**. A segurança da conta e do login é do administrador do realm no Keycloak. Separar "gerir papéis" de "gerir configuração" fica para quando a segregação de funções (F02) for decidida. |
| ADM-03 | Unidade × equipe | Resolvida pela EXP-02: time = unidade organizacional. |
| ADM-05 | Ativar e desativar módulos | Sem ligar e desligar em tempo de execução. Feature flag por instalação, definido no deploy, quando houver módulos opcionais. Decorre da ADR-01. |
| ADM-08 | Licenças e faturamento | Fora do produto, enquanto valer a ADR-13. |

## 5. Decisões abertas

| ID | Tema | Recomendação |
|---|---|---|
| ADM-04 | O que é configuração organizacional e o que é da plataforma | Usar a tabela da [UX/UI 7.11](UX-UI.md#711-níveis-de-configuração) como regra e revisá-la a cada módulo novo. |
| ADM-06 | Gestão de integrações | Com o módulo `integration` (Fase 2): credenciais no cofre, cifradas com chave externa ao banco, com rotação e registro de uso (SAD 7.10). |
| ADM-07 | Eventos administrativos auditados | Os do [SSD 7.2](SSD.md#72-catálogo): `role_assignment.*`, `config.*` e os novos `org_unit.*`, a validar. |
| ADM-09 | SaaS × on-premises | Cada instalação é dedicada (ADR-13), então a diferença está só em quem opera, não no produto. Registrar quando o SaaS dedicado for especificado. |

## 6. O que a análise revelou no produto atual

1. **Operações administrativas sem evento.** O catálogo do SSD 7.2 cobria papéis e configuração, mas não a estrutura organizacional. Foram incluídos `org_unit.created`, `org_unit.updated` e `org_unit.link_added` / `org_unit.link_removed`, marcados "a validar". Hoje nenhuma operação administrativa é auditada ainda, porque os módulos `events` e `audit` não existem; isso entra com eles (REV-03).
2. **Desativar unidade sem confirmação nem verificação de dependências.** Hoje não há artefatos, mas, com o `workitem`, uma unidade com itens ativos não deveria ser desativada sem aviso. Ficou registrado como pendência no [SSD 3.2](SSD.md#32-organization) e como débito técnico na tela (confirmação).
3. **Três níveis de configuração:** princípio adotado (seção 1).

<!-- aqeap-adminpro-ef9e4c4a0a -->
