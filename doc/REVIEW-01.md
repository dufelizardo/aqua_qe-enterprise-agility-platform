# Revisão 01 — Documentação do MVP

| | |
|---|---|
| **Versão** | 1.0 |
| **Data** | 2026-11-20 |
| **Status** | Rascunho para revisão |
| **Documentos revisados** | [PRD v4.0](PRD.md), [SAD v1.0](SAD.md), [SSD v1.0](SSD.md), [UX/UI v1.0](UX-UI.md) |

Esta revisão consolida uma análise externa da documentação, conferida contra o texto dos quatro documentos. Ela registra achados e recomendações e **não altera os documentos revisados**. Cada achado aceito vira uma mudança no documento indicado; até lá, o documento original continua valendo.

**Gravidade.** *Crítico*: precisa ser resolvido antes de implementar o módulo afetado. *Alto*: precisa de decisão antes da implementação do mecanismo. *Médio*: lacuna de contrato que bloqueia uma tela. *Baixo*: pode ficar para depois sem retrabalho.

**Status.** *Aberto*: aguarda decisão. *Aceito*: será incorporado ao documento indicado. *Rejeitado*: mantido como está, com justificativa.

---

## 1. Avaliação geral

A documentação está madura o bastante para uma revisão formal de arquitetura, mas ainda não serve como baseline de implementação. As decisões centrais são coerentes e devem ser mantidas: monólito modular, outbox transacional, auditoria separada do analytics, gates com validações automáticas separadas das aprovações humanas e documentação dentro do artefato. O que falta é fechar regras que hoje aparecem como "(a validar)" ou como pendência, sobretudo nas áreas de autorização e de configuração.

Ao conferir a análise externa contra os documentos, três pontos foram ajustados:

- Parte do que ela recomenda para a auditoria já está especificada (REV-03).
- Ela não percebeu um conflito entre a regra de leitura e o contexto herdado (REV-02).
- A menção a uma "integração futura com AQuA-QE" precisa ser esclarecida, porque AQuA-QE é o nome deste produto (Q-01).

## 2. Achados

| ID | Gravidade | Tema | Afeta | Status |
|---|---|---|---|---|
| REV-01 | Crítico | Regras de autorização entre unidades | SAD 7.2, SSD 2, ADR-14 | Resolvido em 2026-11-20 ([SSD 2.1](SSD.md#21-escopo-entre-unidades-rev-01-rev-02-rev-04)) |
| REV-02 | Crítico | Contexto herdado vazio entre ramos | PRD-RF-017, SSD 3.4, UX/UI 6.9 | Resolvido em 2026-11-20 ([SSD 2.1](SSD.md#21-escopo-entre-unidades-rev-01-rev-02-rev-04)) |
| REV-03 | Alto | Garantias da auditoria assíncrona | SAD 7.4 e 7.5, ADR-06, ADR-07, PRD-RF-026 | Parcial em 2026-11-20: recomendações 2 a 5 implementadas; falta fixar o SLA (1) e restringir o usuário de banco |
| REV-04 | Alto | Modelo organizacional (ADR-14) | SAD 5.1 e 13, SSD 3.2 | Resolvido em 2026-11-20 ([SSD 2.1](SSD.md#21-escopo-entre-unidades-rev-01-rev-02-rev-04)) |
| REV-05 | Alto | Contrato do documento independente do editor | SSD 6, UX/UI 6.6, PRD-RF-018 e 020 | Decidido (2026-11-20) |
| REV-06 | Alto | Publicação de nova versão de workflow | SSD 3.3, PRD-RF-011 | Aberto |
| REV-07 | Médio | Consulta de usuários para quem edita | SSD 3.1, UX/UI 13 | Resolvido (#79, #12) |
| REV-08 | Médio | Listagem de anexos | SSD 3.7, UX/UI 13, PRD-RF-018 | Resolvido (#13) |
| REV-09 | Médio | Consulta de quem pode aprovar | SSD 3.6, UX/UI 13, PRD-RF-024 | Resolvido (#14) |
| REV-10 | Baixo | Preferências do usuário | UX/UI 13, SSD 10 | Resolvido (#15) |
| REV-11 | Baixo | Exportação de documentos | PRD, SSD 6 | Aberto |
| REV-12 | Baixo | Campos customizados usados em gates | SSD 5.2 e 10 | Resolvido (#17) |
| REV-13 | Alto | Baseline de segurança por framework | Spec. Segurança V2.0 (RF-SC, RNF-SC), SAD 7 e 13 | Aberto |
| REV-14 | Alto | Privacidade e dados pessoais | SAD 13, SSD 9 e 10, [Proposta de experiência](EXPERIENCE-PROPOSAL.md) | Aberto |

A frente de trabalho que trata cada achado está na [Proposta de evolução, seção 7](EVOLUTION-PROPOSAL.md#7-relação-entre-achados-e-frentes). REV-02, REV-14 e Q-01 ainda não têm frente (REV-12 foi resolvido direto, na #17), e REV-13 tem só cobertura parcial ([seção 8 da proposta](EVOLUTION-PROPOSAL.md#8-lacunas-desta-proposta)).

### REV-01 — Regras de autorização entre unidades

**Evidência.** O [SAD, seção 7.2](SAD.md#72-autorização) e o [SSD, seção 2](SSD.md#2-papéis-e-permissões) definem três regras:

- O papel vale na unidade em que foi atribuído e nas unidades abaixo dela.
- O vínculo de atendimento não estende papéis; ele só permite ligar artefatos entre ramos.
- Quem lê um artefato lê também os descendentes dele, mesmo em outra unidade.

A terceira regra está marcada "(a validar)". O [SAD, seção 13](SAD.md#13-pendências) registra que ainda não está definido como os níveis de visibilidade Private, Shared e Enterprise se combinam com os vínculos.

**Problema.** As regras estão em texto corrido, e cada documento traz só parte delas. Não há como verificar se um caso específico é permitido: por exemplo, editar uma Feature de outro portfólio que pertence à Cadeia do usuário, ou listar artefatos filtrados por portfólio quando o usuário só tem papel na Cadeia.

**Recomendação.**

1. Criar uma matriz de autorização no SSD. As linhas cruzam papel, operação, relação entre a unidade do usuário e a unidade do artefato (mesma, ancestral, outro ramo com vínculo, outro ramo sem vínculo) e relação hierárquica entre artefatos (descendente, ancestral, nenhuma). Cada célula diz permitido ou negado.
2. Definir agora a regra de visibilidade do MVP, mesmo que Private, Shared e Enterprise só entrem na Fase 2, para que a Fase 2 apenas acrescente casos sem mudar os que já existem.
3. Derivar da matriz testes negativos de isolamento entre portfólios, no detalhe e na listagem. Os testes entram no plano de QA.

### REV-02 — Contexto herdado vazio entre ramos

**Evidência.** O contexto herdado (PRD-RF-017) é um dos três diferenciais do produto. O endpoint `GET /artifacts/{artifact}/context` ([SSD, seção 3.4](SSD.md#34-workitem)) e o painel da [UX/UI, seção 6.9](UX-UI.md#69-painel-de-contexto-herdado) devolvem apenas `key` e `type` dos ancestrais que o usuário não pode ler. A regra de leitura do REV-01 só desce pela hierarquia; não sobe.

**Problema.** O caso típico do modelo é uma Cadeia que atende vários portfólios. Nele, o membro de um squad não tem papel no portfólio da Iniciativa e por isso vê só a chave `INIT-12`, sem título, objetivo nem documentos. O diferencial falha justamente no cenário que a ADR-14 foi criada para suportar.

**Recomendação.** Decidir explicitamente uma destas opções e registrar a escolha no SSD, seção 2:

- **(a)** Leitura de resumo dos ancestrais: título, status, início da descrição e status dos documentos, sem o conteúdo dos documentos.
- **(b)** Leitura completa dos ancestrais para quem lê o descendente.
- **(c)** Manter a regra atual e aceitar o contexto parcial.

A opção (a) preserva o diferencial e expõe pouca informação; é a recomendada.

### REV-03 — Garantias da auditoria assíncrona

**Evidência.** Os registros da auditoria vêm dos eventos da outbox e são gravados por um worker. Um único consumidor grava em ordem, para manter a cadeia de hash ([SAD, seções 7.4 e 7.5](SAD.md#74-eventos-de-domínio)). O SSD já especifica:

- idempotência por `eventId` ([seção 7](SSD.md#7-eventos-de-domínio));
- `event_id` único em `audit_event` e o cálculo do hash ([seção 9](SSD.md#9-esquema-físico));
- verificação da cadeia (`GET /audit-events/verification`, [seção 3.10](SSD.md#310-audit-e-busca)).

A ancoragem externa do último hash já é pendência no SAD.

**Problema.** Entre a confirmação da operação e o registro na trilha há uma janela, e nenhum documento diz quanto tempo ela pode durar. A meta de 1 minuto da DEC-02 vale só para indicadores. Também não estão definidos:

- o alarme de atraso ou de falha do consumidor;
- o tratamento de um evento que falha repetidamente, já que ele trava a cadeia inteira por causa do consumidor único;
- a retenção da outbox em relação à auditoria (o [SSD, seção 10](SSD.md#10-pendências), deixa a retenção em aberto).

**Recomendação.**

1. Definir um SLA de atraso da auditoria e uma métrica de lag da outbox, com alerta.
2. Definir o comportamento para um evento que falha repetidamente: parar e alertar, que é o coerente com a cadeia de hash, em vez de pular o evento.
3. Proibir a limpeza de um evento da outbox antes de ele estar gravado na auditoria.
4. Incluir as operações administrativas (estrutura organizacional, papéis e configuração) entre as ações auditadas. Os eventos `org_unit.*` foram acrescentados ao [SSD 7.2](SSD.md#72-catálogo) em 2026-11-20 ([Proposta 03](ADMIN-PROPOSAL.md#6-o-que-a-análise-revelou-no-produto-atual)).
5. Manter o consumidor único no MVP. A ordem global é o que torna a cadeia simples. Cadeias por agregado ou particionadas só se justificam se o volume medido exigir; registrar isso como risco no [SAD, seção 12](SAD.md#12-riscos).

### REV-04 — Modelo organizacional (ADR-14)

**Evidência.** A ADR-14 torna os níveis configuráveis e acrescenta vínculos de atendimento ([SAD, seção 5.1](SAD.md#51-módulos-do-mvp); [SSD, seção 3.2](SSD.md#32-organization)). O nível pode indicar o Flight Level que representa. As regras de leitura e de vínculo entre ramos estão como propostas ([SSD, seção 10](SSD.md#10-pendências)).

**Problema.** O modelo cruza três coisas diferentes: a árvore organizacional, os Flight Levels e os vínculos entre unidades. As regras de negócio (criação de pai e filho, gates, autorização) passam a depender de combinações dessas três coisas, e nenhuma combinação foi exercitada com exemplos.

**Recomendação.** Manter a ADR-14 e validá-la com três a quatro cenários de organização, descritos como dados de exemplo:

- estrutura padrão;
- Cadeia compartilhada entre portfólios;
- organização sem nível de Unidade de Negócio;
- Squad que atende duas Cadeias.

Para cada cenário, registrar quem cria, liga, lê e aprova o quê, usando a matriz do REV-01.

### REV-05 — Contrato do documento independente do editor

**Evidência.** A decisão entre construir e adotar um editor está em aberto no [SAD, seção 13](SAD.md#13-pendências), no [SSD, seção 10](SSD.md#10-pendências) e na [UX/UI, seção 13](UX-UI.md#13-pendências). O SSD registra que a escolha "pode alterar o formato de blocos da seção 6".

**Problema.** Do jeito que está, a biblioteca escolhida define o formato persistido. O formato persistido condiciona a comparação entre versões, o versionamento imutável, a busca (`search_vector`), os anexos e uma futura colaboração.

**Recomendação.** Fechar o [SSD, seção 6](SSD.md#6-formato-dos-documentos), como contrato da plataforma, e inverter a dependência: o editor escolhido converte para esse formato e a partir dele, e não o contrário. A seção 6.2 deixa de ser "(a validar)". Avaliar editores de código aberto pela capacidade de produzir esse formato.

**Decisão (2026-11-20; [Proposta 07](KNOWLEDGE-PROPOSAL.md)).** Aceito. O formato de blocos do SSD 6 é o contrato. O editor é próprio e simples, com parágrafo e destaque em Markdown leve, título, lista, tabela, citação e código. Ele grava nesse formato, e a tela monta os elementos sem gerar HTML. Imagens, anexos e diagramas entram com o armazenamento de objetos. Um editor de terceiros, se vier, converte para o contrato. Implementado nas páginas de conhecimento (SSD 3.7.1); os documentos do artefato usam o mesmo motor na próxima entrega.

**Revisão (2026-11-21, bug #67).** O editor de blocos montados um a um fragmentava a escrita. Ele foi trocado por um editor de texto contínuo, o **Tiptap 3**, baseado em ProseMirror. O contrato continua sendo o do SSD 6: cada seção passa a ser um bloco `richtext` com o documento do editor, e os blocos antigos são convertidos na tela, sem perda.

### REV-06 — Publicação de nova versão de workflow

**Evidência.** O [SSD, seção 3.3](SSD.md#33-configuration), define duas regras: um status com artefatos não pode ser removido (`CONFIG_IN_USE`), e os artefatos existentes passam a usar a nova versão, preservando o status pela `key`.

**Problema.** Não está definido o que acontece quando a nova versão:

- remove uma transição que um artefato precisaria fazer;
- altera ou acrescenta regras de gate em uma transição;
- muda um gate cuja aprovação está registrada ou pendente para um artefato.

Também não está dito se uma aprovação dada sob a versão anterior continua valendo. O SSD já tem o marcador `superseded` em `approval`, mas não diz quando ele é aplicado por mudança de configuração.

**Recomendação.** Definir regras de compatibilidade no SSD, seção 3.3:

1. Os gates são sempre avaliados com a versão vigente no momento da transição.
2. As aprovações registradas continuam válidas se a regra correspondente não mudou e são marcadas `superseded` se mudou.
3. A publicação mostra antes um relatório de impacto: artefatos afetados e aprovações que serão invalidadas.

Nota de 2026-11-27 (#49): decidido a favor da F05. Os itens ficam na versão em que estão e são avaliados com os gates dela; a migração é explícita e mostra antes, por status, quantos itens serão levados. As aprovações não acompanham o item migrado (valem no status em que foram dadas). Ver [SSD 3.3](SSD.md#33-configuration).

Nota de 2026-11-20: a [Proposta 05](CONFIG-PROPOSAL.md#2-confronto-com-o-que-já-existe) pede que novas versões de modelos e configurações **não sobrescrevam em silêncio** o que está em uso, o que reforça a F05 e a divergência D-01 da [proposta de evolução](EVOLUTION-PROPOSAL.md). Os templates de quadro já foram decididos como cópia na criação (MDL-07).

### REV-07 — Consulta de usuários para quem edita

**Evidência.** `GET /users` exige a permissão "gerir papéis" ([SSD, seção 3.1](SSD.md#31-identity-e-access)). Os campos Owner, Technical Lead, QA Owner e responsável precisam buscar pessoas ([UX/UI, seção 13](UX-UI.md#13-pendências)).

**Recomendação.** Criar uma consulta de pessoas, por exemplo `GET /people?q=`, para quem tem permissão de editar artefatos. Ela devolve só identificador, nome e e-mail, filtrados pelo escopo do usuário (ver REV-01).

**Decisão (2026-11-27, #12): aceita, com a busca por unidade em vez de uma global.** Quem edita escolhe pessoas a partir do lugar do item: `GET /artifacts/{artifact}/people` na página do item (#79) e `GET /org-units/{id}/people?artifactType=` no formulário de novo item ([SSD 3.4](SSD.md#34-workitem)). As duas devolvem quem tem papel vigente na unidade, nas de cima e nas de baixo, só com id e nome (sem e-mail), até 20, e exigem a permissão de editar o nível. Assim a busca nunca sai do escopo do item (REV-01), e `GET /users` continua administrativo. Testes no [QA](QA.md).

### REV-08 — Listagem de anexos

**Evidência.** Existem `POST /artifacts/{artifact}/attachments` e `GET /attachments/{id}/content` ([SSD, seção 3.7](SSD.md#37-livingdocs)), mas não há como listar os anexos de um artefato.

**Recomendação.** Criar `GET /artifacts/{artifact}/attachments`, com nome, tipo, tamanho, autor e data, e permissão "ler o artefato".

**Decisão (2026-11-27, #13): aceita.** Os anexos não existiam no código; a entrega fez envio, listagem, download e remoção ([SSD 3.7](SSD.md#37-livingdocs)). A listagem traz nome, tipo, tamanho, autor e data, e segue a leitura do artefato; enviar e remover seguem a edição. Limite de 10 MB por arquivo, configurável. O conteúdo fica no banco, atrás de uma interface de armazenamento, até entrar o armazenamento de objetos do SAD; a API não muda. O download sai sempre como arquivo, com `nosniff`. A tela é o cartão Anexos da página do item ([UX/UI 7.4](UX-UI.md#74-initiative-workspace-ui-06)); o bloco de anexo dentro dos documentos fica para outra entrega. Testes no [QA](QA.md).

### REV-09 — Consulta de quem pode aprovar

**Evidência.** O painel de gates mostra só o papel aguardado. Para mostrar o nome das pessoas, falta uma consulta que não exija permissão administrativa ([UX/UI, seção 13](UX-UI.md#13-pendências)).

**Recomendação.** Acrescentar à resposta de `GET /artifacts/{artifact}/gates` ([SSD, seção 3.6](SSD.md#36-governance)), em cada regra de aprovação, a lista de pessoas que têm o papel aprovador no escopo do artefato. Se ela for grande, devolver só a contagem e um link para a lista paginada.

**Decisão (2026-11-27, #14): aceita.** Cada regra de aprovação do painel (`GET /artifacts/{artifact}/gates`) traz `approvers`, com até 10 pessoas ativas que têm o papel na unidade do item ou acima, e `approverCount`, com o total ([SSD 3.6](SSD.md#36-governance)). Acima de 10, a tela mostra "e mais N"; não foi preciso uma lista paginada. O `PLATFORM_ADMIN` também aprova, mas não é listado, para a lista mostrar só quem responde pelo papel. Testes no [QA](QA.md).

### REV-10 — Preferências do usuário

**Evidência.** O item existe no menu de perfil, mas não tem conteúdo ([UX/UI, seção 13](UX-UI.md#13-pendências)). O [SSD, seção 10](SSD.md#10-pendências), lista as preferências de notificação como pendência.

**Recomendação.** Retirar o item do menu no MVP ou limitá-lo às preferências de notificação, quando estas forem especificadas.

**Decisão (2026-11-28, #15): aceita, além da recomendação.** O item Preferências fica no menu do avatar e abre `/preferencias` ([UX/UI 7.14](UX-UI.md#714-preferências)), com o tema (do sistema, claro ou escuro) e os avisos do sino que existem: menção e resposta a comentário. As preferências ficam no servidor (`/me/preferences`, módulo `identity`; [SSD 3.1](SSD.md)), valem em qualquer navegador, e o aviso desligado não é criado. Idioma e widgets da Home ficam pendentes. Testes no [QA](QA.md).

### REV-11 — Exportação de documentos

**Evidência.** Nenhum documento prevê exportar a documentação viva.

**Recomendação.** Registrar no PRD um requisito de exportação para Markdown e PDF, com DOCX como opção, na Fase 2. O formato da seção 6 do SSD deve permitir essa conversão sem perda das seções e dos anexos, o que reforça o REV-05.

### REV-12 — Campos customizados usados em gates

**Evidência.** No MVP, `customFields` é um objeto livre, e a regra `REQUIRED_FIELD` aceita `customFields.<nome>` ([SSD, seções 5.2 e 10](SSD.md#52-tipos-de-regra-do-mvp)).

**Recomendação.** Exigir que um campo customizado citado em gate ou usado como filtro esteja declarado na configuração do tipo de artefato, com nome e tipo, mesmo antes do Customization Engine. Isso evita gates que nunca passam por um erro de digitação no nome do campo.

**Decisão (2026-11-27, #17): aceita.** Os campos são declarados por tipo de artefato, valendo para a organização inteira ([SSD 3.3](SSD.md#33-configuration)). A declaração vale também na gravação: o artefato só aceita campos declarados, com o tipo certo, e a página do item mostra e edita os campos do tipo. A regra `REQUIRED_FIELD` só cita campo declarado em algum tipo do workflow, e a remoção de um campo citado por gate é recusada. Ainda não há filtro por campo customizado; quando houver, seguirá a mesma regra. Testes no [QA](QA.md).

### REV-13 — Baseline de segurança por framework

**Evidência.** Nenhum dos quatro documentos cita um framework de segurança. A [Especificação de Segurança V2.0](pdr/) menciona apenas LGPD e SOC 2, como controles que a plataforma deve suportar sem assumir certificação. A DEC-05 do [PRD, seção 12](PRD.md#12-decisões-de-consolidação), proíbe alegar conformidade. Os requisitos RF-SC01 a RF-SC18 e RNF-SC01 a RNF-SC08 são qualitativos; alguns são descritos em faixas, como "RF-SC07 & RF-SC08".

**Problema.** Sem um baseline, não há como responder se os requisitos de segurança estão completos nem como testá-los. Também falta um vocabulário comum para responder às avaliações de segurança dos clientes. Fazer um mapeamento preliminar para o NIST CSF 2.0 já revela lacunas:

- **RS (Respond):** nada especificado sobre resposta a incidentes, nem do lado do fornecedor nem do que o produto oferece ao cliente (por exemplo, exportar a trilha ou revogar sessões em massa).
- **GV.SC (cadeia de suprimentos):** o produto distribui Keycloak, MinIO, PostgreSQL e imagens via Helm. Faltam SBOM, assinatura de imagens e uma política de correção de vulnerabilidades, que pesa mais em instalação air-gapped.
- **DE (Detect):** RF-SC16 e RNF-SC07 pedem monitoramento de eventos de segurança sem dizer quais eventos nem para onde vão. Os eventos de autenticação na trilha já são pendência no [SSD, seção 10](SSD.md#10-pendências).
- **PR.AA e PR.DS** estão bem cobertos (SSO, MFA, RBAC, criptografia, auditoria com cadeia de hash), mas dependem do REV-01.
- **RC** já tem meta: RPO de 1 h e RTO de 4 h, no [SAD, seção 11](SAD.md#11-atributos-de-qualidade).

**Recomendação.** Adotar três referências, cada uma no papel em que ela funciona:

| Referência | Papel no projeto | Quando |
|---|---|---|
| OWASP ASVS, nível 2 | Requisitos de segurança da aplicação, verificáveis e usados no plano de QA. | Antes do código. |
| NIST SSDF (SP 800-218) | Processo de desenvolvimento e entrega: SBOM, assinatura de imagens, varredura de dependências e de imagens. | Junto com o pipeline de build. |
| NIST CSF 2.0 | Mapa guarda-chuva: tabela RF-SC e ASVS → subcategorias CSF; depois, perfil CSF da operação SaaS dedicada. | Mapeamento agora; perfil antes do lançamento SaaS. |

O CSF não deve ser usado como fonte de requisitos do produto, porque descreve resultados da organização e não da aplicação. Nas instalações on-premise e air-gapped, quem opera é o cliente; o papel do produto é fornecer as capacidades que ajudam o cliente a atingir os resultados dele. A LGPD continua obrigatória por lei. Clientes do setor financeiro tendem a pedir ISO/IEC 27001 ou a Resolução CMN 4.893, e o mapeamento ao CSF facilita responder a esses pedidos. Em textos externos, usar "mapeado ao NIST CSF 2.0", nunca "conforme" (DEC-05).

O entregável da atividade é a tabela de mapeamento. Ela só deve ser feita depois que este achado for aceito.

### REV-14 — Privacidade e dados pessoais

Achado incluído em 2026-11-20, a partir da análise da [Proposta de experiência corporativa](EXPERIENCE-PROPOSAL.md).

**Evidência.** Nenhum dos documentos trata de dados pessoais. A plataforma já guarda nome, e-mail, usuário do IdP e data da última atividade de cada pessoa (`identity.user_account`, [SSD, seção 9](SSD.md#9-esquema-físico)). Os eventos e a trilha de auditoria registram quem fez o quê. A [Especificação de Segurança V2.0](pdr/) cita a LGPD apenas como controle a suportar. A proposta de experiência pede perfil, diretório, foto, cargo, gestor e localização, que são mais dados pessoais.

**Problema.** Sem uma definição, cada módulo decide sozinho o que guarda, por quanto tempo e quem vê. Isso tende a coletar mais do que o necessário e a expor dados de uma pessoa a quem não precisa deles. Também falta saber o que fazer quando alguém deixa a organização: a conta é desativada, mas o nome continua na trilha de auditoria, que só aceita inserção.

**Recomendação.** Antes de qualquer frente de perfil ou diretório, decidir e registrar no SSD:

- o inventário dos dados pessoais por módulo e a finalidade de cada um (minimização);
- a visibilidade padrão de cada dado (todos os autenticados, a mesma unidade, só administradores);
- o que acontece com os dados de quem foi desativado, incluindo a convivência entre o direito de eliminação e a auditoria imutável (por exemplo, pseudonimizar o nome e manter o identificador);
- a retenção, junto com a da auditoria e a da outbox, que já são pendências;
- as capacidades que o produto oferece a quem o opera para atender pedidos dos titulares, como exportar e anonimizar.

Assim como no REV-13, o produto fornece capacidades e quem opera a instalação responde pela conformidade. Nunca alegar "conforme a LGPD" (DEC-05 do PRD).

## 3. Questões em aberto

| ID | Questão |
|---|---|
| Q-01 | A análise externa cita uma "integração futura com AQuA-QE e seus recursos de qualidade e automação". AQuA-QE é o nome desta plataforma ([PRD, seção 3](PRD.md#3-nome-do-produto)). Se existe outro produto com esse nome, ele precisa entrar no PRD, no glossário e no mapa de integrações. Se não existe, a menção deve ser lida como a futura gestão de testes desta plataforma (Fase 2). |

## 4. Ordem de trabalho proposta

| Ordem | Atividade | Achados | Resultado esperado |
|---|---|---|---|
| 1 | Autorização, isolamento e baseline de segurança | REV-01, REV-02, REV-13 | Matriz no SSD, seção 2; testes negativos no plano de QA; ASVS L2 e SSDF adotados; mapeamento RF-SC → CSF. |
| 2 | Modelo organizacional | REV-04 | Cenários validados contra a matriz; ADR-14 passa a Decidido ou é revista. |
| 3 | Auditoria e eventos | REV-03 | SLA, alertas e regras de retenção no SAD, seções 7.4, 7.5 e 11. |
| 4 | Contrato do documento | REV-05, REV-11 | SSD, seção 6, fechado; critérios para escolher o editor. |
| 5 | Lacunas SSD × UX/UI | REV-07 a REV-10 | Endpoints no SSD; UX/UI, seção 13, atualizada. |
| 6 | Versionamento de configuração | REV-06, REV-12 | Regras de compatibilidade no SSD, seção 3.3. |
| 7 | Estratégia de testes | todos | [Plano de QA do MVP](QA.md), incluindo testes de fronteira de módulo (ADR-04), de autorização e de verificação ASVS (REV-13). |

Os passos 1 e 2 bloqueiam o início dos módulos `access`, `organization` e `workitem`. Os módulos `configuration` e `workflow` podem começar após o passo 6, e o frontend dos workspaces após os passos 4 e 5.

<!-- aqeap-review01-e6ceb25143 -->
