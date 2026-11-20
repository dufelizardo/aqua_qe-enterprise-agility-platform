# Proposta de evolução funcional: experiência corporativa, pessoas, equipes e gestão estratégica

| | |
|---|---|
| **Versão** | 1.0 |
| **Data** | 2026-11-20 |
| **Status** | PROPOSTO, fora do MVP. Três decisões já tomadas (seção 4); as demais estão abertas. |
| **Documentos-base** | [PRD v4.0](PRD.md), [SAD v1.0](SAD.md), [SSD v1.0](SSD.md), [UX/UI v1.0](UX-UI.md), [Revisão 01](REVIEW-01.md) |
| **Inspiração** | Experiência do Atlassian Home, usada só como referência de padrões. O AQuA-QE não reproduz o produto, os nomes nem a navegação dele. |

Esta proposta avalia funcionalidades de experiência corporativa: menu do usuário, perfil profissional, metas, acompanhamento executivo, equipes, diretório de pessoas, reconhecimento, navegação e configurações da conta. O texto original foi recebido em 2026-11-20 e está resumido por frente na seção 2. A seção 3 o confronta com o que o produto já decidiu, e as seções 4 e 5 registram as decisões tomadas e as que continuam abertas.

Ela não altera o PRD, o SAD, o SSD nem a UX/UI, e não muda o roadmap. Nada desta proposta entra no MVP.

---

## 1. Princípio

A plataforma deve conectar pessoas, estrutura organizacional, equipes, objetivos, iniciativas, entregas e governança. Cada usuário deve conseguir entender onde está na organização, com quem trabalha, quais objetivos e iniciativas se relacionam com o trabalho dele e o que depende dele agora.

O diferencial pretendido não é ter funcionalidades parecidas com as de outras plataformas. É ligar essas informações aos conceitos que o AQuA-QE já tem: Flight Levels, a estrutura organizacional configurável (ADR-14), a Iniciativa, os OKRs e a documentação viva.

## 2. Frentes propostas (resumo do texto original)

| Frente | Proposta original | Importância no original |
|---|---|---|
| **P1. Menu do usuário** | Foto ou avatar, nome, e-mail, cargo, organização e unidade atual, acesso ao perfil, configurações, tema, troca de workspace e saída. | Alta |
| **P2. Perfil profissional** | Abas Visão geral, Metas, Projetos e Elogios. A visão geral traz identificação (cargo, unidade, equipe principal, gestor, localização), "Trabalhando comigo", trabalhos recentes, posição na estrutura e equipes, além de "Locais frequentes de trabalho" em vez de uma entidade nova de espaços. | Alta |
| **P3. Metas** | Objetivos organizacionais, de equipe e individuais, em hierarquia de contribuição, com responsável, indicador, valores inicial, esperado e atual, prazo, riscos, iniciativas relacionadas e atualizações periódicas. O original adverte que metas não são avaliação de desempenho. | Muito alta |
| **P4. Projetos e acompanhamento executivo** | Acompanhamento consolidado de um trabalho que envolve várias equipes. Duas alternativas: **A**, a Iniciativa é a fonte de verdade e "projeto" é uma visão dela; **B**, uma entidade Project separada. O original recomenda avaliar a A primeiro. | Muito alta |
| **P5. Equipes** | Grupo de trabalho distinto da unidade organizacional, com página própria, líder, membros, convidados, política de entrada e visibilidade. O original adverte que pertencer a uma equipe não deve dar acesso automático aos artefatos dela. | Muito alta |
| **P6. Pessoas e diretório** | Busca por nome e cargo; filtros por unidade, equipe, gestor, especialidade, iniciativa e localização, respeitando a privacidade. | Alta |
| **P7. Reconhecimento** | Elogios públicos ligados a uma iniciativa, entrega ou equipe, sem rankings nem pontuação. | Média |
| **P8. Navegação** | Menu lateral com "Para você", Equipes, Pessoas, Elogios, Metas e Projetos. "Para você" seria a central personalizada de trabalho. | Muito alta |
| **P9. Configurações da conta** | Perfil e visibilidade, e-mail, segurança (sessões, dispositivos, MFA), privacidade, preferências (idioma, fuso, tema), aplicativos conectados, abertura de links e configurações do produto, separando preferências pessoais de configurações administrativas. | Alta |
| **P10. Modelo de domínio** | Conceitos: User, User Profile, User Preferences, Organization Unit, Team, Team Membership, Goal, Goal Contribution, Kudos, Initiative, Project View, Personal Dashboard e Activity. | — |

Prioridade sugerida no original:
- **P0:** menu, perfil, equipes e diretório.
- **P1:** "Para você", metas, acompanhamento executivo e configurações.
- **P2:** reconhecimento e personalização avançada.

O próprio original diz que essas prioridades não são compromisso de release.

## 3. Confronto com o que já existe

Metade da proposta já existe no produto com outro nome, e duas frentes contradiziam decisões tomadas.

| Frente | O que já existe | Avaliação |
|---|---|---|
| P1. Menu do usuário | [UX/UI, seção 4](UX-UI.md#4-estrutura-da-aplicação): avatar com nome e papéis na unidade ativa, Preferências, Ajuda e Sair, mais o seletor de workspace (`GET /me/workspaces`). `GET /me` já traz papéis e unidades ([SSD 3.1](SSD.md#31-identity-e-access)). | **Já coberto.** Não existem no modelo: cargo, foto e tema escuro (este último é pendência da UX/UI 13). Senha, MFA e sessões ficam no Keycloak; o menu só leva ao console de conta dele. |
| P2. Perfil profissional | Nada. | Depende de dados que o produto não tem: gestor direto, cargo e localização viriam de RH ou SCIM, não do IdP. "Trabalhos recentes" e "Trabalhando comigo" dependem do módulo `workitem` e de um registro de atividades. **Pós-MVP**, e só depois da decisão de privacidade (seção 6). |
| P3. Metas | **OKRs**: domínio D01 ([PRD, seção 7](PRD.md)), PRD-RF-005 a 008, Fase 2, módulo `okr` ([SAD 5.2](SAD.md#52-módulos-das-fases-seguintes)). A DEC-01 do PRD separa Delivery Progress (calculado) de Business Outcome Progress (medido). | **Não é domínio novo:** metas organizacionais e de unidade são os OKRs da Fase 2. **Metas individuais** não estão em requisito nenhum e são o caminho para virar sistema de RH (risco 4 do original). |
| P4. Projetos | Iniciativa (FL3) com business case, documentos e gates; Executive Overview (UI-03, PRD-RF-004, Fase 2). | A Alternativa A basta: "projeto" é a visão executiva da Iniciativa (decidido, EXP-06). Dela aproveitamos uma ideia nova: **atualizações periódicas de status** da Iniciativa, que o PRD não tem. |
| P5. Equipes | O time **é** uma unidade organizacional: Squad (FL1) e Cadeia (FL2) são níveis da árvore ([ADR-14](SAD.md#10-decisões-de-arquitetura)). O papel vale por unidade, e o vínculo "atende a" cobre o time que atende vários ramos ([SSD 2.1](SSD.md#21-escopo-entre-unidades-rev-01-rev-02-rev-04)). | Contradizia a ADR-14. Uma entidade Team criaria uma segunda fonte de pertencimento e uma segunda via de autorização (risco 3 do original). Decidido manter time = unidade (EXP-02). |
| P6. Pessoas | `GET /users` só para quem gere papéis ([SSD 3.1](SSD.md#31-identity-e-access)). [REV-07](REVIEW-01.md#rev-07--consulta-de-usuários-para-quem-edita): falta uma consulta de usuários para quem edita artefatos. | A necessidade real do MVP é o **REV-07**: escolher Owner, Technical Lead e QA Owner. Um diretório amplo exige dados que não temos e a decisão de privacidade. |
| P7. Reconhecimento | Nada. | Fora do propósito do produto, como admite o próprio original. Não incorporar agora. |
| P8. Navegação | [UX/UI 4 e 12](UX-UI.md#12-divergências-com-os-protótipos): navegação por Flight Levels; o menu "por função" foi explicitamente não adotado. | Contradizia a decisão de navegação. Decidido manter os Flight Levels (EXP-09). "Para você" já é a home "Meu trabalho" ([UX/UI 7.2](UX-UI.md#72-home--meu-trabalho)), pensada como grade de widgets. |
| P9. Configurações | [REV-10](REVIEW-01.md#rev-10--preferências-do-usuário): preferências do usuário. Segurança da conta: console do Keycloak. Aplicativos conectados: integrações (Fase 2). | Preferências pessoais = REV-10. Segurança = link para o Keycloak, sem duplicar o IdP. O resto acompanha as integrações. |
| P10. Activity | [ADR-07](SAD.md#10-decisões-de-arquitetura) e DEC-04 do PRD: a trilha de auditoria é restrita e não serve de fonte para outros usos. | O original sugeria integrar atividades à auditoria: **não**. Um registro de atividades seria outro consumidor dos eventos de domínio (outbox, ADR-06), com as regras de visibilidade do [SSD 2.1](SSD.md#21-escopo-entre-unidades-rev-01-rev-02-rev-04). |

**Ajustes de forma em relação ao original**

- **Identificadores:** as decisões do original eram DEC-01 a DEC-12, que colidem com as DEC-01 a DEC-10 do PRD (com outro significado) e com as D01 a D09 da [proposta de evolução](EVOLUTION-PROPOSAL.md). Aqui elas são **EXP-01 a EXP-12**.
- **Nomes:** "Goals", "Kudos" e "Home" são nomes de produto de terceiros. No AQuA-QE usamos Metas (OKRs), Reconhecimento e Meu trabalho.
- **Prioridade:** o P0 do original (perfil, equipes, diretório) competiria com o núcleo do MVP (artefatos, gates, documentos), que ainda está em construção, e depende dele.

## 4. Decisões tomadas

| ID | Tema | Decisão (2026-11-20) |
|---|---|---|
| EXP-02 | Equipes × unidades | **Time = unidade organizacional** (Squad, Cadeia). Sem entidade Team. Membros são as pessoas com papel na unidade. A Team só volta à pauta se surgir necessidade de grupos que não são donos de trabalho, como guildas e comunidades. |
| EXP-06 | Projetos | **Alternativa A:** "projeto" é a visão executiva da Iniciativa, sem entidade nova. A visão entra com o Executive Overview (UI-03, Fase 2). |
| EXP-09 | Navegação | **Mantém a navegação por Flight Levels** (UX/UI 4). "Para você" é a home "Meu trabalho". "Metas" (OKRs) entra no menu na Fase 2, junto com o módulo `okr`. |

## 5. Decisões abertas

| ID | Tema | Questão | Recomendação |
|---|---|---|---|
| EXP-01 | Perfil | Quais informações são públicas, internas ou restritas? | Decidir junto com o REV-14 (privacidade). |
| EXP-03 | Participação | Papéis e regras de associação a equipes. | Coberto pela EXP-02: são as atribuições de papel por unidade (SSD 2). |
| EXP-04 | Metas | Domínio próprio ou extensão de Portfolio & Strategy? | Extensão: são os OKRs do domínio D01 (Fase 2). |
| EXP-05 | Metas individuais | Onde termina o desenvolvimento profissional e começa a gestão de desempenho? | Não incluir metas individuais. |
| EXP-07 | Atividades | Como consolidar o histórico de atividades? | Consumidor próprio dos eventos de domínio, nunca a trilha de auditoria; visibilidade pelas regras do SSD 2.1. |
| EXP-08 | Reconhecimento | Entra no escopo? | Não por enquanto. |
| EXP-10 | Privacidade | Como proteger dados pessoais e organizacionais? | Tratar no REV-14, que vale para o produto atual e não só para esta proposta. |
| EXP-11 | Configurações | O que é preferência do usuário e o que é configuração administrativa? | Preferências pessoais = REV-10; segurança da conta = Keycloak; configuração do produto = Administração (UX/UI 7.10). |
| EXP-12 | Analytics | Como metas, iniciativas e times alimentam os indicadores? | Junto com o módulo `analytics` da Fase 2, a partir dos eventos de domínio (DEC-04 do PRD). |

## 6. O que a proposta revelou

1. **Privacidade e dados pessoais (LGPD).** Nenhum documento do produto trata disso, e a plataforma já guarda nome, e-mail e última atividade de cada usuário (`identity.user_account`). Registrado como [REV-14](REVIEW-01.md#rev-14--privacidade-e-dados-pessoais), que vale independentemente desta proposta.
2. **Atualizações periódicas de status da Iniciativa** como parte da visão executiva (EXP-06): conceito novo, a avaliar na especificação da UI-03.
3. **Candidatos ao catálogo de widgets da home** (UX/UI 13): locais frequentes, metas acompanhadas, atividade recente e riscos ligados ao usuário.

## 7. Riscos (do original, ainda válidos)

| Risco | Situação |
|---|---|
| Duplicidade Project × Iniciativa | Eliminado pela EXP-06. |
| Sobreposição de estrutura e equipes | Eliminado pela EXP-02. |
| Novos caminhos de acesso a informação | Toda frente futura usa o serviço de autorização do `access` (SAD 7.2) e as regras do SSD 2.1. |
| Virar sistema de RH | EXP-05: sem metas individuais. |
| Excesso de itens na navegação | Eliminado pela EXP-09. |
| Aumento prematuro de escopo | Nada desta proposta entra no MVP. |

<!-- aqeap-experien-273999c5d5 -->
