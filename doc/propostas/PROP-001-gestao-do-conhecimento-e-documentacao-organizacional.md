# PROP-001 — Gestão do Conhecimento e Documentação Organizacional

**Produto:** AQuA-QE Hub  
**Tipo:** Proposta técnica e de regras de negócio  
**Status:** Proposta — não aprovada  
**Data:** 21/11/2026  
**Responsável:** Produto / Arquitetura

> Esta proposta apresenta uma evolução potencial do produto. Não representa requisito aprovado, decisão arquitetural, compromisso de roadmap ou funcionalidade já implementada. Os itens descritos deverão ser avaliados contra o PRD, o SAD, o modelo de domínio, o SDD e as prioridades do produto antes de qualquer implementação.

---

## 1. Resumo executivo

Propõe-se criar uma capacidade transversal de **Gestão do Conhecimento e Documentação Organizacional**, permitindo que uma organização produza, organize, relacione, governe e consulte documentos associados ao seu contexto organizacional.

A capacidade deverá permitir documentos flexíveis — por exemplo, regimentos, políticas, normas, procedimentos, planos, manuais, acordos de trabalho e páginas de conhecimento — sem impor uma estrutura documental única a todas as organizações.

O documento poderá ser associado a uma organização, projeto, equipe ou a outros tipos de estrutura organizacional que venham a ser formalmente suportados pelo produto. A associação define o contexto de propriedade; a abrangência define a quem ou a quais estruturas o conteúdo se aplica. Esses conceitos não devem ser confundidos.

A proposta também prevê modelos documentais configuráveis, editor de conteúdo rico, versionamento, revisão, aprovação, publicação, vigência, histórico e relacionamentos entre documentos.

### 1.1. Problema

Informações institucionais e operacionais tendem a ficar dispersas em arquivos, wikis, ferramentas externas e documentos sem relação explícita com as estruturas responsáveis. Isso dificulta:

- Identificar qual documento é aplicável a uma estrutura ou equipe.
- Distinguir documentos em elaboração daqueles aprovados e vigentes.
- Entender a relação entre diretrizes corporativas e acordos locais.
- Rastrear alterações, justificativas, revisões e aprovações.
- Reutilizar conhecimento sem copiar e manter múltiplas versões do mesmo conteúdo.
- Encontrar documentos relacionados a projetos, equipes e atividades.
- Manter uma experiência documental consistente sem obrigar todas as organizações a adotar o mesmo modelo.

### 1.2. Resultado pretendido

Disponibilizar uma capacidade de documentação flexível na composição do conteúdo e rigorosa na governança, com rastreabilidade e isolamento entre organizações.

---

## 2. Objetivos

1. Permitir criar e manter documentos associados a contextos organizacionais suportados.
2. Permitir estruturas documentais livres ou baseadas em modelos configuráveis.
3. Separar conteúdo, estrutura documental, contexto de associação e abrangência.
4. Oferecer versionamento e histórico imutável das versões publicadas.
5. Controlar revisão, aprovação, publicação, vigência e obsolescência.
6. Relacionar documentos a outros documentos e a entidades do produto por referências explícitas.
7. Permitir reutilizar diretrizes por referência, evitando cópias desnecessárias.
8. Preservar autorização, auditoria e isolamento por organização.
9. Evoluir sem acoplar a documentação ao domínio de testes, projetos ou integrações externas.

## 3. Não objetivos

Esta proposta não pretende:

- Transformar o produto em um sistema genérico de gestão empresarial ou substituir ERPs, sistemas de RH, sistemas jurídicos ou ferramentas especializadas de gestão documental.
- Implementar, nesta etapa, uma hierarquia organizacional universal e obrigatória.
- Presumir que Empresa, Unidade de Negócio, Portfólio, Cadeia de Valor e Squad já sejam entidades suportadas pelo modelo de domínio vigente.
- Transformar todo conteúdo em formulário com campos fixos.
- Exigir aprovação formal para toda página ou anotação.
- Implementar colaboração simultânea avançada, assinatura digital juridicamente qualificada ou automação complexa de processos como pré-requisitos do primeiro incremento.
- Replicar o conteúdo integral de documentos superiores em documentos subordinados.
- Substituir a documentação de engenharia já definida para PRD, SAD, SDD, ADRs, contratos e demais artefatos de desenvolvimento do próprio produto.

---

## 4. Conceitos e distinções

Os conceitos abaixo deverão possuir significado distinto no domínio.

| Conceito | Definição |
|---|---|
| Documento | Unidade identificável de informação governada, como regimento, política, procedimento, plano ou página. |
| Modelo documental | Estrutura inicial reutilizável para criação de documentos, com seções sugeridas, orientações e componentes. |
| Seção | Unidade hierárquica de organização do conteúdo de um documento. |
| Bloco de conteúdo | Elemento editável dentro de uma seção, como parágrafo, título, tabela, imagem, lista, citação, diagrama ou referência. |
| Contexto de associação | Entidade à qual o documento pertence ou está primariamente vinculado. |
| Abrangência | Conjunto de contextos, públicos ou unidades aos quais o documento se aplica. |
| Relacionamento documental | Vínculo tipado entre documentos, como complementa, substitui, depende de, referencia ou detalha. |
| Versão | Registro imutável de uma composição documental em determinado momento do ciclo de vida. |
| Revisão | Processo de análise de uma versão ou proposta de alteração. |
| Aprovação | Decisão formal de uma autoridade competente sobre uma versão. |
| Publicação | Disponibilização controlada de uma versão aprovada ou de uma página sem aprovação formal, conforme sua política. |
| Vigência | Intervalo em que uma versão publicada é considerada aplicável. |

### 4.1. Associação não é abrangência

Um documento pode pertencer à organização e aplicar-se a todos os seus projetos. Outro pode pertencer a um projeto e aplicar-se apenas a uma equipe específica. Um documento pode ainda ser mantido por uma equipe, mas ter abrangência em mais de um projeto, desde que o usuário tenha autorização para isso.

A associação identifica o contexto proprietário ou responsável. A abrangência identifica o escopo de aplicação. Não se deve inferir abrangência automaticamente apenas pela associação.

### 4.2. Estrutura organizacional não é estrutura documental

A estrutura organizacional representa entidades e relações de gestão. A estrutura documental representa a organização do conteúdo. Uma não deve ser codificada como se fosse a outra.

Se o produto evoluir para suportar estruturas como Empresa → Unidade de Negócio → Portfólio → Cadeia de Valor → Squad, isso deverá ser especificado como capacidade própria de modelagem organizacional, com tipos de estrutura, regras de relacionamento, cardinalidades e governança. A documentação deverá consumir esse contexto por contratos, sem assumir uma árvore fixa no código.

---

## 5. Escopo funcional proposto

### 5.1. Cadastro e organização documental

O usuário autorizado poderá:

- Criar, editar, arquivar e consultar documentos.
- Informar título, identificador, descrição, tipo, classificação, responsável e metadados configuráveis.
- Associar o documento a um contexto suportado.
- Definir abrangência e público-alvo.
- Organizar documentos em espaços, categorias, coleções ou pastas virtuais.
- Marcar documentos como favoritos e localizar documentos por pesquisa e filtros.
- Relacionar documentos a outros documentos e a entidades do produto, quando houver contrato de integração apropriado.

A organização em pastas ou coleções não deverá determinar, por si só, a autorização ou a abrangência do documento.

### 5.2. Estrutura flexível

O documento deverá permitir:

- Começar em branco ou a partir de um modelo.
- Adicionar, remover, renomear e reordenar seções.
- Criar níveis hierárquicos de títulos e subseções.
- Utilizar blocos de texto, listas, tabelas, imagens, links, citações, código e componentes visuais suportados.
- Incorporar referências a documentos, indicadores ou entidades externas, sem duplicar seus dados.
- Manter metadados governados separados do corpo editorial.

A plataforma poderá recomendar estruturas para tipos documentais conhecidos, mas não deverá obrigar todas as organizações a utilizarem os mesmos capítulos, ordem ou campos.

### 5.3. Modelos documentais

Modelos poderão ser disponibilizados pela plataforma, criados pela organização, copiados e personalizados a partir de um modelo existente ou marcados como padrão para determinado tipo documental ou contexto.

Um modelo deverá definir apenas a estrutura inicial e orientações de preenchimento. A utilização de um modelo não deverá impedir a personalização posterior do documento, salvo restrições explicitamente configuradas e justificadas.

Alterações em um modelo não deverão modificar retroativamente documentos já criados a partir dele. A atualização de documentos existentes deverá ser uma operação explícita.

### 5.4. Editor

O editor deverá priorizar uma experiência de escrita fluida, sem transformar o corpo do documento em um formulário rígido.

Requisitos de experiência:

- Edição de texto rico com títulos, parágrafos, listas, tabelas, links e mídia.
- Inserção e reorganização de blocos.
- Navegação por estrutura de títulos.
- Salvamento automático ou mecanismo equivalente com indicação clara do estado de salvamento.
- Visualização de leitura e edição.
- Aviso de alterações não salvas e tratamento de conflito de edição.
- Acessibilidade por teclado e semântica adequada.
- Exportação e impressão em formato legível, conforme formatos suportados.

Diagramas, mapas mentais e componentes especializados poderão ser adicionados por extensões ou blocos próprios. Não se deve prometer que qualquer conteúdo visual será editável como texto comum.

### 5.5. Ciclo de vida documental

Estados iniciais propostos:

- Rascunho.
- Em revisão.
- Aguardando aprovação.
- Aprovado.
- Publicado.
- Suspenso.
- Obsoleto.
- Arquivado.

A máquina de estados definitiva deverá ser definida no design detalhado. Transições deverão validar permissões, pré-condições e registros de auditoria.

Documentos informativos poderão utilizar um fluxo simplificado. Documentos normativos poderão exigir revisão, aprovação e vigência. A política de governança deverá ser configurável por tipo documental ou documento, dentro das regras suportadas pelo produto.

### 5.6. Versionamento e comparação

- Alterações no rascunho poderão ser salvas sem gerar uma nova versão publicada a cada tecla ou salvamento automático.
- Uma versão formal deverá ser criada em um marco explícito do fluxo, como envio para revisão ou publicação, conforme a política adotada.
- Versões aprovadas ou publicadas deverão ser imutáveis.
- Uma alteração posterior deverá produzir uma nova versão, preservando as anteriores.
- O histórico deverá identificar autor, data, justificativa e resumo da alteração.
- A comparação deverá permitir identificar conteúdo adicionado, removido ou modificado entre versões.
- A comparação automática será assistiva: o responsável deverá poder revisar e complementar o resumo antes de concluir o fluxo.

### 5.7. Revisão, aprovação e publicação

O fluxo poderá incluir criação ou edição, envio para revisão, comentários e solicitação de ajustes, reenvio, aprovação ou rejeição, publicação, definição de vigência e revisão periódica ou substituição.

A aprovação deverá estar vinculada a uma versão específica. Aprovar uma versão não significa aprovar automaticamente alterações futuras.

### 5.8. Relacionamentos e referências

Relacionamentos tipados poderão incluir:

- Complementa.
- Detalha.
- Referencia.
- Substitui.
- É substituído por.
- Depende de.
- Está relacionado a.
- É aplicável a.

O sistema deverá evitar ciclos inválidos em relacionamentos que exijam direção ou exclusividade, como substituição. Referências deverão indicar quando o documento de destino estiver obsoleto, suspenso ou inacessível ao usuário.

---

## 6. Regras de negócio

As regras abaixo são propostas e deverão ser validadas antes de serem consideradas normativas.

| ID | Regra |
|---|---|
| RN-DOC-001 | Todo documento deverá pertencer a exatamente uma organização, respeitando o isolamento multi-tenant. |
| RN-DOC-002 | Todo documento deverá possuir um identificador interno imutável. |
| RN-DOC-003 | Todo documento deverá possuir um contexto de associação principal, escolhido entre os tipos de contexto suportados pela instalação. |
| RN-DOC-004 | A abrangência deverá ser registrada separadamente da associação principal. |
| RN-DOC-005 | Um documento não poderá ser associado a contexto de outra organização. |
| RN-DOC-006 | A criação de documento deverá respeitar as permissões do usuário no contexto de associação. |
| RN-DOC-007 | A organização poderá criar documentos livres ou utilizar modelos disponíveis. |
| RN-DOC-008 | Modelos não imporão estrutura fixa aos documentos derivados, salvo restrição explicitamente configurada. |
| RN-DOC-009 | Alterar um modelo não alterará automaticamente documentos já criados a partir dele. |
| RN-DOC-010 | O corpo do documento será composto por seções e blocos ordenáveis, sem exigir conjunto universal de campos editoriais. |
| RN-DOC-011 | Metadados obrigatórios serão definidos por política documental e deverão ser distintos do conteúdo livre. |
| RN-DOC-012 | O documento deverá possuir responsável ou papel responsável pela manutenção. |
| RN-DOC-013 | Somente usuários autorizados poderão alterar o conteúdo ou os metadados do documento. |
| RN-DOC-014 | Uma versão aprovada ou publicada não poderá ser alterada diretamente. |
| RN-DOC-015 | Alterações após aprovação deverão gerar nova versão e novo ciclo de governança quando exigido pela política. |
| RN-DOC-016 | A aprovação deverá referenciar inequivocamente a versão analisada. |
| RN-DOC-017 | A publicação deverá respeitar o estado, a aprovação exigida, a data de vigência e a autorização do responsável. |
| RN-DOC-018 | Um documento substituído deverá manter seu histórico e apontar para o documento ou versão substituta. |
| RN-DOC-019 | A obsolescência não deverá apagar o documento nem invalidar registros históricos que o referenciem. |
| RN-DOC-020 | A abrangência de um documento não concederá automaticamente permissão de leitura ou edição; autorização e aplicabilidade são conceitos distintos. |
| RN-DOC-021 | A herança de diretrizes deverá ocorrer preferencialmente por referência ao documento de origem, não por cópia automática do conteúdo. |
| RN-DOC-022 | Uma referência herdada deverá preservar a identificação da origem e da versão referenciada, quando a referência exigir estabilidade histórica. |
| RN-DOC-023 | A organização poderá definir exceções ou complementos locais somente quando a política do documento de origem permitir. |
| RN-DOC-024 | Um documento local não poderá declarar que substitui ou revoga documento de escopo superior sem autoridade explícita para isso. |
| RN-DOC-025 | A plataforma deverá sinalizar conflitos conhecidos entre documentos relacionados, mas não deverá inferir automaticamente conflito sem regra verificável. |
| RN-DOC-026 | Todo evento relevante de criação, edição, envio, revisão, aprovação, rejeição, publicação, suspensão, substituição e arquivamento deverá ser auditável. |
| RN-DOC-027 | Registros de auditoria deverão preservar ator, ação, objeto, data/hora e contexto da operação, sem armazenar segredos. |
| RN-DOC-028 | A exclusão física de documentos com versões publicadas, aprovações ou referências deverá ser proibida ou submetida a política de retenção e autorização especial. |
| RN-DOC-029 | A pesquisa e as listagens deverão respeitar as permissões do usuário e o isolamento da organização. |
| RN-DOC-030 | A indisponibilidade de um serviço de integração não deverá corromper ou apagar documentos e versões já persistidos. |
| RN-DOC-031 | Documentos informativos poderão dispensar aprovação formal, conforme política explícita. |
| RN-DOC-032 | Documentos normativos deverão possuir critérios de revisão e aprovação definidos antes da publicação. |
| RN-DOC-033 | Uma nova versão deverá registrar resumo da alteração e justificativa quando exigidos pela política documental. |
| RN-DOC-034 | A data de vigência poderá ser posterior à aprovação, permitindo publicação programada e período de transição. |
| RN-DOC-035 | A expiração de uma vigência deverá gerar sinalização e ação de revisão, sem apagar o documento ou seu histórico. |

---

## 7. Abrangência e herança entre estruturas

### 7.1. Princípio

A proposta admite que organizações possuam diferentes modelos estruturais. Não se deverá codificar uma hierarquia universal fixa.

Caso exista uma hierarquia formalmente suportada — por exemplo, Empresa → Unidade de Negócio → Portfólio → Cadeia de Valor → Squad — a documentação poderá associar-se a qualquer nível disponibilizado pelo módulo responsável pela estrutura organizacional.

Essa hierarquia é um exemplo de configuração e não uma decisão de que todos esses tipos já existem no domínio atual do AQuA-QE Hub.

### 7.2. Aplicação de diretrizes

Exemplo conceitual:

- Um documento corporativo pode ter abrangência sobre toda a organização.
- Uma unidade pode manter documentos complementares para seu contexto.
- Um portfólio pode manter uma carta de governança específica.
- Uma equipe pode manter acordos de trabalho e instruções locais.

O documento local deverá respeitar as diretrizes superiores aplicáveis. A plataforma deverá permitir relacionar explicitamente o documento local à diretriz que complementa ou detalha.

### 7.3. Regras de conflito

- A abrangência superior não significa que todo conteúdo inferior deva ser duplicado.
- A referência a uma diretriz superior não altera a propriedade do documento de origem.
- Uma exceção local deverá registrar justificativa, responsável e aprovação, quando exigida.
- A plataforma deverá apresentar a origem da diretriz e a situação da versão referenciada.
- A precedência entre documentos deverá considerar tipo, autoridade, escopo, vigência e política configurada; não apenas a posição na árvore organizacional.
- Em caso de conflito não resolvido, o sistema deverá sinalizar a situação e encaminhá-la à autoridade competente, sem escolher silenciosamente uma regra.

---

## 8. Modelo conceitual de domínio

O modelo abaixo é conceitual. Não constitui decisão de persistência nem substitui o modelo de domínio aprovado.

| Conceito candidato | Responsabilidade |
|---|---|
| Document | Identidade, título, tipo, organização, contexto de associação, responsável e estado atual. |
| DocumentVersion | Snapshot imutável do conteúdo e dos metadados relevantes de uma versão. |
| DocumentSection | Estrutura hierárquica de seções dentro de uma versão. |
| ContentBlock | Conteúdo tipado e ordenado de uma seção. |
| DocumentTemplate | Modelo reutilizável para criação de documentos. |
| DocumentAssociation | Vínculo do documento a um contexto organizacional ou entidade suportada. |
| DocumentApplicability | Escopo de aplicação, público ou estruturas alcançadas. |
| DocumentRelation | Relacionamento tipado entre documentos. |
| ReviewCycle | Processo de revisão de uma versão. |
| ApprovalDecision | Decisão de aprovação ou rejeição associada a uma versão e autoridade. |
| PublicationRecord | Registro da publicação, canal, data e vigência. |
| DocumentAuditEvent | Evento de auditoria documental, ou integração com o mecanismo de auditoria existente. |

### 8.1. Considerações de modelagem

- O conteúdo de uma versão deverá ser recuperável sem depender do estado atual do editor.
- A versão publicada deverá ser um snapshot consistente, incluindo ordem de seções e blocos.
- O modelo de conteúdo deverá permitir extensões sem exigir migração para cada novo bloco editorial, mantendo validação de esquema e compatibilidade.
- Os vínculos com estruturas organizacionais deverão utilizar contratos do módulo dono dessas estruturas; não deverão criar dependência direta de tabelas alheias.
- A autorização deverá consultar o módulo de identidade e autorização, preservando o modelo de permissões existente.
- A auditoria deverá reutilizar o mecanismo transversal existente quando isso for compatível com os requisitos de retenção e consulta.

---

## 9. Proposta de arquitetura técnica

### 9.1. Diretriz arquitetural

Recomenda-se avaliar um módulo de domínio próprio, provisoriamente denominado **Documentação e Conhecimento**, dentro do monólito modular atual.

A proposta está alinhada, em princípio, às diretrizes documentadas do produto: Java 25 e Spring Boot 4, Spring Modulith e monólito modular, Angular e Angular Material, PostgreSQL, contratos explícitos entre módulos, isolamento por organização e Row-Level Security, e auditoria das operações relevantes.

A compatibilidade final deverá ser confirmada no SDD e nos contratos executáveis.

### 9.2. Responsabilidades do módulo

O módulo seria responsável por:

- Documentos e metadados.
- Modelos documentais.
- Versões e snapshots.
- Seções e blocos de conteúdo.
- Associações e abrangência.
- Relacionamentos documentais.
- Fluxos de revisão e aprovação.
- Publicação e vigência.
- Consulta e pesquisa documental, ou publicação de eventos/projeções para um módulo de busca dedicado.

Não deverá ser responsável por manter a estrutura organizacional, usuários, papéis, permissões globais ou entidades de projetos e testes.

### 9.3. Integrações internas

| Módulo existente ou futuro | Integração proposta |
|---|---|
| Identidade e autorização | Usuários, papéis, permissões e verificação de acesso. |
| Gerenciamento de projetos | Associação de documentos a projetos e equipes, conforme contratos disponíveis. |
| Auditoria | Registro dos eventos documentais relevantes. |
| Relatórios | Projeções de documentos por estado, vigência, revisão e abrangência. |
| Estrutura organizacional (se formalizada) | Consulta de contextos, relações e identificadores organizacionais por contrato. |
| Armazenamento de arquivos | Mídias e anexos, com autorização e metadados de integridade. |
| Busca (se necessário) | Indexação de conteúdo publicado, respeitando autorização e isolamento. |

As integrações deverão ocorrer por interfaces públicas, contratos ou eventos de domínio. O módulo documental não deverá consultar diretamente tabelas de outros módulos.

### 9.4. Persistência e armazenamento

Recomenda-se avaliar:

- Tabelas próprias com prefixo consistente com a convenção do projeto.
- organization_id obrigatório em todos os registros persistidos do contexto, protegido por RLS.
- Identificadores UUID.
- Restrições de unicidade por organização quando aplicável.
- Snapshots versionados do conteúdo.
- Metadados relacionais em tabelas próprias.
- Conteúdo estruturado em JSON validado por esquema ou modelo relacional, após comparação de trade-offs no SDD.
- Arquivos binários em object storage, mantendo no banco metadados, chave, tamanho, tipo de mídia e checksum.
- Índices para organização, estado, tipo, contexto, responsável, vigência e data de atualização.

Não se recomenda armazenar arquivos binários diretamente em colunas comuns do banco sem justificativa técnica.

### 9.5. API candidata

Os caminhos abaixo são ilustrativos e deverão ser alinhados às convenções reais da API.

| Método | Endpoint candidato | Finalidade |
|---|---|---|
| POST | /api/v1/documents | Criar documento. |
| GET | /api/v1/documents | Consultar documentos autorizados. |
| GET | /api/v1/documents/{documentId} | Consultar metadados e estado. |
| PATCH | /api/v1/documents/{documentId} | Atualizar metadados editáveis. |
| POST | /api/v1/documents/{documentId}/versions | Criar ou consolidar uma versão. |
| GET | /api/v1/documents/{documentId}/versions | Consultar histórico de versões. |
| GET | /api/v1/documents/{documentId}/versions/{versionId} | Consultar snapshot de uma versão. |
| POST | /api/v1/documents/{documentId}/submit-review | Enviar versão para revisão. |
| POST | /api/v1/documents/{documentId}/approve | Registrar decisão de aprovação. |
| POST | /api/v1/documents/{documentId}/publish | Publicar versão aprovada ou autorizada. |
| POST | /api/v1/documents/{documentId}/relations | Criar relacionamento documental. |
| GET | /api/v1/document-templates | Consultar modelos disponíveis. |

A API deverá validar organização, permissão, estado e versão em toda operação. Os endpoints finais dependem do design detalhado.

### 9.6. Eventos candidatos

- DocumentCreated
- DocumentMetadataUpdated
- DocumentVersionCreated
- DocumentSubmittedForReview
- DocumentReviewRequested
- DocumentApproved
- DocumentRejected
- DocumentPublished
- DocumentSuspended
- DocumentSuperseded
- DocumentArchived
- DocumentReviewDue

Eventos deverão conter identificadores e metadados necessários, evitando publicar o corpo integral do documento quando não for necessário.

### 9.7. Segurança e isolamento

- Todas as operações deverão ser escopadas por organização.
- RLS deverá proteger os registros persistidos.
- Autorização deverá ser aplicada no backend, não apenas na interface.
- Acesso a anexos deverá validar permissão antes de fornecer URL ou conteúdo.
- Conteúdo publicado não será necessariamente público: visibilidade e autorização são políticas distintas.
- Logs e eventos não deverão expor segredos nem dados sensíveis desnecessários.
- Exportações deverão respeitar as mesmas regras de acesso.
- O mecanismo de auditoria deverá permitir identificar o ator e a operação.

### 9.8. Concorrência e integridade

O editor deverá tratar edição concorrente. A primeira versão poderá adotar controle otimista por versão ou ETag, com detecção de conflito e opção de recarregar ou reconciliar alterações. Edição simultânea em tempo real fica fora do escopo inicial, salvo decisão posterior.

---

## 10. Requisitos não funcionais propostos

| ID | Requisito |
|---|---|
| RNF-DOC-001 | Isolamento completo entre organizações em leitura, escrita, pesquisa, anexos e exportação. |
| RNF-DOC-002 | Autorização aplicada no backend em todas as operações. |
| RNF-DOC-003 | Integridade histórica das versões aprovadas e publicadas. |
| RNF-DOC-004 | Conteúdo recuperável e legível ao longo de atualizações compatíveis do editor. |
| RNF-DOC-005 | Acessibilidade do editor e das páginas de leitura. |
| RNF-DOC-006 | Pesquisa e listagem com desempenho adequado ao volume definido no planejamento de capacidade. |
| RNF-DOC-007 | Auditoria de operações críticas sem exposição de conteúdo ou segredo além do necessário. |
| RNF-DOC-008 | Compatibilidade de esquema para evolução dos blocos de conteúdo. |
| RNF-DOC-009 | Backup e restauração deverão preservar documentos, versões, relações e referências a anexos. |
| RNF-DOC-010 | Exportação deverá manter legibilidade e estrutura sem prometer equivalência visual perfeita para todos os componentes. |

Metas quantitativas de desempenho, disponibilidade, retenção e recuperação deverão ser definidas com base em cenários de uso e requisitos gerais do produto.

---

## 11. Experiência do usuário proposta

A experiência deverá separar três áreas:

### 11.1. Navegação documental

- Espaços ou coleções.
- Filtros por tipo, contexto, responsável, estado e vigência.
- Pesquisa.
- Documentos recentes e favoritos.
- Indicação de documentos vencidos, em revisão ou obsoletos.

### 11.2. Editor

- Área central de edição.
- Navegação lateral pela estrutura de seções.
- Inserção de blocos.
- Propriedades e metadados em área separada do corpo.
- Pré-visualização.
- Histórico e comparação de versões.
- Indicação clara de estado, salvamento e permissões.

### 11.3. Governança

- Linha do tempo do documento.
- Responsáveis, revisores e aprovadores.
- Comentários e solicitações de ajuste.
- Resumo da alteração.
- Justificativa.
- Decisões e evidências.
- Data de publicação e vigência.
- Próxima revisão prevista.

A interface deverá evitar que a gestão documental seja reduzida a uma árvore rígida de campos fixos ou que a navegação organizacional seja confundida com a hierarquia interna do documento.

---

## 12. Escopo incremental sugerido

A sequência abaixo é uma sugestão para avaliação, não um compromisso de roadmap.

### Fase A — Base documental

- Cadastro e consulta de documentos.
- Associação a organização, projeto e equipe nos limites dos contratos existentes.
- Editor rico com seções e blocos básicos.
- Modelos simples.
- Permissões e auditoria.
- Rascunho e publicação simples.
- Anexos e referências.

### Fase B — Governança documental

- Versionamento formal.
- Comparação entre versões.
- Revisão e aprovação.
- Vigência e revisão periódica.
- Histórico de decisões.
- Documentos substituídos e obsoletos.

### Fase C — Conhecimento organizacional ampliado

- Contextos organizacionais adicionais, caso o domínio seja aprovado.
- Abrangência multi-contexto.
- Herança por referência.
- Relacionamentos documentais avançados.
- Pesquisa de conteúdo e navegação por conhecimento.
- Componentes especializados e integrações.

### Fase D — Evoluções avançadas

- Colaboração simultânea.
- Fluxos configuráveis de aprovação.
- Assinatura eletrônica integrada, se houver demanda e avaliação jurídica.
- Análise assistida de alterações e conflitos.
- Recomendações de conteúdo e descoberta semântica, com controles de acesso preservados.

---

## 13. Critérios de aceite de alto nível

A proposta poderá ser considerada atendida em uma primeira entrega quando:

1. Um usuário autorizado conseguir criar documento sem utilizar modelo.
2. Um usuário conseguir criar documento a partir de modelo e personalizar sua estrutura.
3. O documento permitir adicionar, remover e reordenar seções e blocos básicos.
4. O documento puder ser associado a um contexto organizacional suportado.
5. A abrangência puder ser definida separadamente da associação.
6. Usuários sem permissão não conseguirem ler, editar, aprovar ou exportar o documento.
7. Uma versão publicada permanecer recuperável após alterações posteriores.
8. O histórico identificar versões e autores.
9. O fluxo de aprovação, quando exigido, referenciar a versão exata.
10. O documento puder ser relacionado a outro documento sem duplicar seu conteúdo.
11. Operações relevantes gerarem eventos de auditoria.
12. Testes comprovarem isolamento entre organizações, inclusive em pesquisa e anexos.

Os critérios específicos de cada fase deverão ser detalhados em histórias e testes de aceitação.

---

## 14. Riscos e decisões em aberto

| Tema | Risco ou dúvida | Próxima ação recomendada |
|---|---|---|
| Escopo do produto | A documentação organizacional pode ampliar significativamente o posicionamento atual, centrado em Quality Engineering. | Decidir se será uma capacidade complementar do Hub, um produto adjacente ou fora do escopo. |
| Hierarquia organizacional | O modelo atual documenta Organization, Project e Team; uma hierarquia empresarial mais rica não deve ser presumida. | Avaliar se há necessidade real de novo módulo organizacional e formalizar seu domínio separadamente. |
| Editor | Escolher tecnologia de edição pode gerar dependência e complexidade de migração. | Fazer prova de conceito com conteúdo estruturado, acessibilidade, tabelas, mídia e exportação. |
| Versionamento | Snapshot integral pode aumentar armazenamento; diff semântico pode ser complexo. | Comparar snapshot, armazenamento incremental e estratégia híbrida. |
| Permissões | Abrangência e herança podem ser confundidas com autorização. | Validar matriz de acesso e cenários de isolamento antes do design. |
| Herança | Referências podem apontar para conteúdo alterado ou obsoleto. | Definir referência à versão fixa ou à versão vigente conforme o tipo de relação. |
| Fluxos | Workflows muito configuráveis podem aumentar custo e complexidade. | Começar com fluxos padronizados e poucos pontos de configuração. |
| Busca | Indexação de conteúdo pode expor informações se não respeitar ACL. | Projetar autorização também na camada de pesquisa. |
| Retenção | Exclusão e retenção podem ter implicações contratuais e legais. | Definir política por implantação e jurisdição com validação especializada. |

### 14.1. Questão de posicionamento

O PRD vigente posiciona o AQuA-QE Hub como plataforma independente de Quality Engineering, com gerenciamento de testes como núcleo. A Gestão do Conhecimento e Documentação Organizacional pode contribuir para a governança de qualidade, por exemplo, ao manter políticas de teste, padrões, procedimentos e evidências documentais.

Entretanto, uma capacidade ampla de regimentos corporativos, unidades de negócio, portfólios e cadeias de valor pode representar uma expansão relevante de produto. Essa expansão deverá ser deliberada explicitamente antes de atualizar o PRD e o roadmap.

---

## 15. Documentos que deverão ser atualizados após aprovação

A aprovação desta proposta não deverá resultar em implementação baseada apenas neste arquivo. Após decisão de produto:

1. **PRD:** incluir personas, casos de uso, requisitos funcionais e não funcionais, prioridades e critérios de aceite.
2. **SAD:** definir o módulo, limites, dependências, integrações, segurança, implantação e decisões arquiteturais.
3. **Modelo de domínio:** formalizar entidades, relacionamentos, invariantes, estados e propriedade dos dados.
4. **SDD:** detalhar pacotes, tabelas, API, eventos, validações, jobs e componentes de frontend.
5. **ADRs:** registrar decisões com trade-offs quando houver escolhas arquiteturais relevantes.
6. **Protótipo:** validar navegação, editor, leitura, governança e fluxos de aprovação.
7. **Plano de execução:** planejar fases, dependências e critérios de saída.
8. **Testes:** especificar testes unitários, integração, API, autorização, isolamento multi-tenant, acessibilidade e aceitação.

A proposta deverá permanecer como documento de referência; os documentos donos continuarão sendo a fonte oficial de requisitos, arquitetura, domínio e design, conforme a convenção do repositório.

---

## 16. Recomendação

Recomenda-se **aprovar primeiro a investigação e a definição de escopo**, não a implementação integral.

A decisão inicial deve responder:

1. A documentação organizacional será parte do escopo estratégico do AQuA-QE Hub?
2. O produto precisa suportar apenas organização, projetos e equipes ou também estruturas empresariais configuráveis?
3. Quais tipos de documento são prioritários para o público de Quality Engineering?
4. Qual é o nível mínimo de governança necessário para o primeiro incremento?
5. O editor será uma capacidade central compartilhada ou uma funcionalidade isolada?

Com essas respostas, a equipe poderá transformar a proposta em requisitos verificáveis e decisões arquiteturais consistentes.

**Princípio orientador:** a documentação deve ser flexível em sua estrutura, explícita em seu contexto e rigorosa em sua governança. A plataforma oferece modelos e controles; a organização define como estrutura seu conhecimento.
