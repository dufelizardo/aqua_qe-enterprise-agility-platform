# AQuA-QE Enterprise Agility Platform

[![Pipeline de Promoção](https://github.com/dufelizardo/aqua_qe-enterprise-agility-platform/actions/workflows/pipeline.yml/badge.svg?branch=developer)](https://github.com/dufelizardo/aqua_qe-enterprise-agility-platform/actions/workflows/pipeline.yml)
[![Status](https://img.shields.io/badge/status-esqueleto%20do%20MVP-orange)](#roteiro)
[![Licença](https://img.shields.io/github/license/dufelizardo/aqua_qe-enterprise-agility-platform)](LICENSE)
[![Documentação](https://img.shields.io/badge/docs-PRD%20%C2%B7%20SAD%20%C2%B7%20SSD%20%C2%B7%20UX%2FUI%20%C2%B7%20QA-1351b4?logo=markdown&logoColor=white)](#documentação)

![Java 25](https://img.shields.io/badge/Java-25-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot 4.1](https://img.shields.io/badge/Spring_Boot-4.1-6DB33F?logo=springboot&logoColor=white)
![Angular 22](https://img.shields.io/badge/Angular-22-DD0031?logo=angular&logoColor=white)
![PostgreSQL 17](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![Keycloak 26](https://img.shields.io/badge/Keycloak-26-4D4D4D?logo=keycloak&logoColor=white)
![Robot Framework](https://img.shields.io/badge/Robot_Framework-LKDF-000000?logo=robotframework&logoColor=white)
![visitors](https://visitor-badge.laobi.icu/badge?page_id=dufelizardo.visitor-aqua_qe-enterprise-agility-platform)
![GitHub followers](https://img.shields.io/github/followers/dufelizardo?style=social)

Plataforma corporativa que reúne, em um único produto, gestão de trabalho, documentação e gestão de testes — o que hoje costuma exigir Jira, Confluence e Xray — sem plugins e sem serviços contratados à parte.

> **Estado do projeto:** esqueleto do MVP. A especificação está em [doc/](doc/); o código tem a estrutura dos 12 módulos, o login pelo Keycloak e a esteira até os ambientes dev e prod, mas ainda nenhuma funcionalidade de negócio.

## O que é

A AQuA-QE organiza o trabalho em três níveis, seguindo o modelo de Flight Levels, e liga a estratégia à execução com rastreabilidade de ponta a ponta:

| Nível | Quem atua | Artefato |
|---|---|---|
| **FL3 — Estratégico** | Portfólios | Iniciativa (3 meses a 1 ano) |
| **FL2 — Tático** | Cadeias de valor | Feature e Feature de Sustentação (até 3 meses) |
| **FL1 — Operacional** | Squads | História, Bug, Spike, Débito Técnico, Task |

Três ideias diferenciam o produto:

- **Documentação viva no artefato.** PRD, arquitetura, especificação, UX/UI e plano de testes ficam em abas da própria Iniciativa ou Feature, versionados, e não em uma wiki separada.
- **Governança por gates.** O sistema impede a mudança de status enquanto campos, documentos ou aprovações exigidos não estiverem prontos, e mostra exatamente o que falta.
- **Contexto herdado.** Quem trabalha em uma História vê na mesma tela a Feature, a Iniciativa e as regras de negócio que a originaram.

## Documentação

Leia nesta ordem:

| Documento | Conteúdo |
|---|---|
| [PRD](doc/PRD.md) | Visão, glossário, personas, domínios, requisitos (`PRD-RF-001` a `038`), catálogo de telas e faseamento. |
| [SAD](doc/SAD.md) | Arquitetura: containers, módulos, modelo de dados, mecanismos transversais, implantação e decisões (ADRs). |
| [SSD](doc/SSD.md) | Especificação do MVP: contratos da API, regras de gate, formato dos documentos, eventos, configuração padrão e esquema físico. |
| [UX/UI](doc/UX-UI.md) | Tokens de design, navegação, componentes, telas do MVP e mensagens de erro. |
| [QA](doc/QA.md) | Plano de testes do MVP: níveis, Robot Framework no padrão LKDF para API e interface, rastreabilidade, ambiente e critérios de saída. |
| [Revisão 01](doc/REVIEW-01.md) | Achados da revisão dos quatro documentos, com gravidade, evidência, recomendação e ordem de trabalho. Não altera os documentos revisados. |
| [Operação](doc/OPERACAO.md) | Ambientes dev e prod, nomes na rede (mDNS), pipeline de promoção, configuração única do GitHub e do servidor, desenvolvimento local. |
| [Proposta de evolução](doc/EVOLUTION-PROPOSAL.md) | Frentes F01 a F08c que tratam os achados da revisão, com divergências e decisões pendentes. Status: proposto, não aprovado para implementação. |
| [Proposta de experiência corporativa](doc/EXPERIENCE-PROPOSAL.md) | Menu do usuário, perfil, metas, equipes, pessoas e navegação, confrontados com o que o produto já tem. Status: proposto, fora do MVP; decisões EXP-01 a EXP-12. |
| [Proposta de configuração e modelos de trabalho](doc/CONFIG-PROPOSAL.md) | Configuration Center, modelos Kanban e Scrum, templates de quadro e herança, confrontados com o que já existe. Status: proposto; decisões MDL-01 a MDL-12. |
| [Proposta de quadros e visualizações](doc/BOARDS-PROPOSAL.md) | Kanban configurável, WIP, bloqueio e visualizações do trabalho, confrontados com o que já existe. Status: Etapa 1 implementada; decisões BRD-01 a BRD-12. |
| [Proposta de administração corporativa](doc/ADMIN-PROPOSAL.md) | Áreas administrativas, papéis e níveis de configuração, confrontados com o que já existe. Status: proposto; decisões ADM-01 a ADM-09. |

Material de apoio:

- [doc/pdr/](doc/pdr/) — especificações técnicas por domínio (Analytics, Dashboards, DevOps, Testes, Segurança, Taxonomia, Customização, Hospedagem) e os PRDs originais, em `.docx`. Valem as versões **V2.0**; as demais são histórico. Os quatro PRDs dessa pasta foram substituídos pelo [PRD consolidado](doc/PRD.md).
- [doc/prototype/](doc/prototype/) — identidade visual e protótipos iniciais de landing page, login e home. A [especificação de UX/UI](doc/UX-UI.md) registra o que muda em relação a eles.

Todos os documentos estão com status **Rascunho para revisão**. Itens marcados "Proposto" ou "a validar" ainda dependem de confirmação.

## Arquitetura em resumo

| Aspecto | Definição | Status |
|---|---|---|
| Estilo | Monólito modular, com um módulo por domínio | Decidido |
| Backend | Java com Spring Boot | Decidido |
| Frontend | Angular com TypeScript | Decidido |
| Fronteiras de módulo | Spring Modulith, verificado no build | Decidido |
| Banco de dados | PostgreSQL, um schema por módulo | Decidido |
| Identidade | Keycloak empacotado, com federação ao provedor do cliente; login no navegador pelo backend (BFF) | Decidido |
| Arquivos | Armazenamento de objetos compatível com S3 | Decidido |
| Eventos | Outbox transacional; sem broker no MVP | Decidido |
| Distribuição | Containers e chart Helm para Kubernetes e OpenShift | Proposto |

A plataforma é desenhada para três modelos de instalação: SaaS dedicado, on-premise e ambiente sem conexão externa (air-gapped). Os detalhes e as justificativas estão no [SAD](doc/SAD.md).

## Como rodar

Pré-requisitos: Docker. Para desenvolver: Java 25, Node 24 e Python 3.13.

```bash
docker compose up -d --build        # PostgreSQL, Keycloak, API e frontend
```

Abra http://localhost:4200 e entre com um usuário de teste, por exemplo `robot.pm` / `Robot#Teste123`. Há um por papel do SSD: `robot.admin`, `robot.bo`, `robot.pm`, `robot.tl`, `robot.ux`, `robot.qa`, `robot.squad` e `robot.viewer`.

| Tarefa | Comando |
|---|---|
| Testes do backend (JUnit, Testcontainers, fronteiras de módulo) | `./mvnw verify` |
| Testes do frontend | `cd frontend && npx ng test --watch=false` |
| Aceitação Robot (API e interface) | `pip install -r test/robot/requirements.txt`, `rfbrowser init chromium` e `bash test/robot/run_tests.sh` |

Ambientes no servidor da rede local: dev em http://aquaqe-dev.local (branch `developer`) e prod em http://aquaqe.local (branch `main`). Configuração e promoção em [doc/OPERACAO.md](doc/OPERACAO.md).

## Roteiro

O faseamento é uma proposta ainda a validar ([PRD, seção 13](doc/PRD.md)).

| Fase | Entrega |
|---|---|
| **MVP** | Hierarquia FL3–FL1 e quadros; workspaces de Iniciativa e de Feature com documentação versionada; gates por campo, documento e aprovação; login com SSO e MFA; papéis por unidade organizacional; trilha de auditoria. |
| **Fase 2** | Dependências entre portfólios, OKRs, capacity planning, gestão de testes e rastreabilidade, integração com Git e CI/CD, métricas de fluxo e painéis padrão. |
| **Fase 3** | Construtor de dashboards, motor de customização completo, instalação air-gapped e análises assistidas por IA. |

## Próximos passos

- Decidir as frentes da [proposta de evolução](doc/EVOLUTION-PROPOSAL.md) (decisões D01 a D09) e incorporar as aprovadas aos documentos.
- Corrigir as lacunas do SSD apontadas na [especificação de UX/UI, seção 13](doc/UX-UI.md).
- Validar os itens "Proposto" e "a validar" dos quatro documentos.
- Fazer a configuração única do GitHub e do servidor ([OPERACAO.md, seções 4 e 5](doc/OPERACAO.md)) para publicar os ambientes.
- Decidir o modelo de autorização (REV-01, D01), que destrava os módulos `access`, `organization` e `workitem`.

## Estrutura do repositório

```text
.
├── src/                código da API (Spring Boot, um pacote por módulo do SAD)
├── frontend/           SPA Angular e nginx
├── test/robot/         aceitação Robot Framework (LKDF): api/ e ui/
├── k8s/                manifestos: apps/ (dev e prod), auth/ (Keycloak), components/mdns, argocd/
├── compose.yaml        ambiente local e de CI
├── .github/workflows/  pipeline, promoção automática com retentativas, publicação de imagens, vigilância de cópias
└── doc/
    ├── PRD.md          requisitos de produto
    ├── SAD.md          arquitetura
    ├── SSD.md          especificação do MVP
    ├── UX-UI.md        interface do MVP
    ├── QA.md           plano de testes do MVP
    ├── REVIEW-01.md    revisão dos documentos
    ├── OPERACAO.md     ambientes, mDNS, pipeline e servidor
    ├── EVOLUTION-PROPOSAL.md  proposta de evolução (não aprovada)
    ├── EXPERIENCE-PROPOSAL.md proposta de experiência corporativa (fora do MVP)
    ├── ADMIN-PROPOSAL.md      proposta de administração corporativa
    ├── BOARDS-PROPOSAL.md     proposta de quadros e visualizações (Kanban)
    ├── CONFIG-PROPOSAL.md     proposta de configuração e modelos de trabalho
    ├── pdr/            especificações por domínio e PRDs originais (.docx)
    └── prototype/      identidade visual e protótipos iniciais
```

## Licença

Distribuído sob a licença MIT. Veja [LICENSE](LICENSE).

<!-- aqeap-readme-ab3b7c16cd -->
