# AQuA-QE Enterprise Agility Platform

Plataforma corporativa que reúne, em um único produto, gestão de trabalho, documentação e gestão de testes — o que hoje costuma exigir Jira, Confluence e Xray — sem plugins e sem serviços contratados à parte.

> **Estado do projeto:** fase de especificação. Este repositório contém a documentação de produto, arquitetura e interface do MVP. Ainda não há código.

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
| Banco de dados | PostgreSQL, um schema por módulo | Proposto |
| Identidade | Keycloak empacotado, com federação ao provedor do cliente | Proposto |
| Arquivos | Armazenamento de objetos compatível com S3 | Proposto |
| Eventos | Outbox transacional; sem broker no MVP | Proposto |
| Distribuição | Containers e chart Helm para Kubernetes e OpenShift | Proposto |

A plataforma é desenhada para três modelos de instalação: SaaS dedicado, on-premise e ambiente sem conexão externa (air-gapped). Os detalhes e as justificativas estão no [SAD](doc/SAD.md).

## Roteiro

O faseamento é uma proposta ainda a validar ([PRD, seção 13](doc/PRD.md)).

| Fase | Entrega |
|---|---|
| **MVP** | Hierarquia FL3–FL1 e quadros; workspaces de Iniciativa e de Feature com documentação versionada; gates por campo, documento e aprovação; login com SSO e MFA; papéis por unidade organizacional; trilha de auditoria. |
| **Fase 2** | Dependências entre portfólios, OKRs, capacity planning, gestão de testes e rastreabilidade, integração com Git e CI/CD, métricas de fluxo e painéis padrão. |
| **Fase 3** | Construtor de dashboards, motor de customização completo, instalação air-gapped e análises assistidas por IA. |

## Próximos passos

- Plano de testes (QA) do MVP, o quinto documento do conjunto.
- Corrigir as lacunas do SSD apontadas na [especificação de UX/UI, seção 13](doc/UX-UI.md).
- Validar os itens "Proposto" e "a validar" dos quatro documentos.
- Criar a estrutura de código do backend e do frontend.

## Estrutura do repositório

```text
.
├── README.md
├── LICENSE
└── doc/
    ├── PRD.md          requisitos de produto
    ├── SAD.md          arquitetura
    ├── SSD.md          especificação do MVP
    ├── UX-UI.md        interface do MVP
    ├── pdr/            especificações por domínio e PRDs originais (.docx)
    └── prototype/      identidade visual e protótipos iniciais
```

## Licença

Distribuído sob a licença MIT. Veja [LICENSE](LICENSE).
