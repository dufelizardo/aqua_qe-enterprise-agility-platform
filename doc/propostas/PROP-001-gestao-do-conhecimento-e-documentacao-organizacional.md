# PROP-001 — Evolução da Gestão do Conhecimento e Documentação Organizacional

| Campo | Valor |
|---|---|
| Produto | AQuA-QE Enterprise Agility Platform |
| Tipo | Proposta técnica e de evolução de produto |
| Versão | 2.0 |
| Data | 2026-11-21 |
| Status | **Proposta em revisão — não aprovada integralmente** |
| Documento relacionado | [Proposta 07 — Conhecimento organizacional, Wiki e documentação viva](../KNOWLEDGE-PROPOSAL.md) |
| Documentos de referência | [PRD](../PRD.md), [SAD](../SAD.md), [SSD](../SSD.md), [UX/UI](../UX-UI.md) |

> **Finalidade desta revisão:** eliminar a sobreposição entre esta proposta, a Proposta 07 e as especificações do produto. Esta PROP-001 não propõe criar um segundo Wiki, um segundo motor documental, uma nova hierarquia organizacional ou um novo fluxo paralelo ao que já existe. Ela consolida o que foi identificado, separa o que já está implementado do que está especificado e do que ainda é uma evolução a decidir.
>
> **Regra de leitura:** nada descrito como proposta neste documento deve ser entendido como requisito aprovado, decisão arquitetural ou compromisso de roadmap. A implementação existente e os documentos-base prevalecem sobre sugestões futuras desta proposta.

---

## 1. Objetivo e posicionamento

Esta proposta trata da evolução da capacidade de **Gestão do Conhecimento e Documentação Organizacional** da plataforma, considerando a implementação já realizada no módulo `livingdocs` e as decisões registradas na Proposta 07.

O objetivo é organizar, em um único documento de evolução:

1. O que já existe no produto e não deve ser proposto novamente.
2. O que está definido na Proposta 07, mas permanece parcial ou em avaliação.
3. O que pode ser uma evolução adicional, sujeita à decisão de produto e arquitetura.
4. Quais documentos devem ser atualizados caso uma evolução seja aprovada.

A capacidade deve apoiar o contexto de Quality Engineering e a governança da organização sem transformar, por presunção, a plataforma em um sistema corporativo genérico de gestão documental.

### 1.1. Problema que permanece relevante

Mesmo com Wiki e Regimento implementados, ainda pode haver necessidades futuras relacionadas a:

- Governança e ciclo de vida de documentos normativos.
- Responsáveis, revisores e periodicidade de revisão.
- Aplicabilidade explícita de diretrizes a unidades e contextos.
- Referências entre documentos e artefatos sem duplicação de conteúdo.
- Pesquisa e descoberta de conhecimento.
- Comparação de versões e rastreabilidade de decisões.
- Evolução do editor para conteúdo visual especializado, quando houver necessidade comprovada.

Esses pontos não devem ser confundidos com funcionalidades já entregues.

### 1.2. Resultado pretendido

Manter uma capacidade documental única, integrada ao modelo organizacional e aos mecanismos transversais da plataforma, evoluindo-a de forma incremental e verificável.

---

## 2. Relação entre os documentos

Os documentos abaixo têm papéis diferentes. Não devem ser tratados como especificações concorrentes.

| Documento | Papel | Como deve ser utilizado |
|---|---|---|
| **PRD** | Define visão, escopo de produto, personas, requisitos e prioridades. | É a referência para decidir se uma capacidade pertence ao produto e qual sua prioridade. |
| **SAD** | Define arquitetura, módulos, limites, dependências e decisões arquiteturais. | É a referência arquitetural. Esta proposta não cria módulos ou dependências por conta própria. |
| **SSD** | Detalha contratos, regras técnicas, APIs, persistência e comportamento do MVP. | É a referência de especificação técnica detalhada dentro do escopo que cobre. |
| **Proposta 07** | Registra a evolução específica de conhecimento organizacional, Wiki e documentação viva, suas decisões e seu faseamento. | É a referência funcional desta capacidade e do que foi decidido para sua primeira entrega. |
| **PROP-001 (este documento)** | Consolida a análise de lacunas e propõe evoluções adicionais, sem repetir a especificação existente. | Complementa a Proposta 07; não a substitui e não altera automaticamente PRD, SAD ou SSD. |
| **Código e testes** | Materializam o comportamento efetivamente implementado. | Devem ser consultados para confirmar o estado real; documentação de proposta, isoladamente, não comprova implementação. |

### 2.1. Precedência para resolver divergências

Quando houver divergência, aplicar a seguinte ordem de análise:

1. Verificar o comportamento implementado e seus testes.
2. Consultar a especificação técnica vigente (SSD e contratos executáveis).
3. Consultar as decisões arquiteturais do SAD e ADRs.
4. Consultar os requisitos e limites do PRD.
5. Consultar a Proposta 07 para decisões e evolução da capacidade.
6. Tratar esta PROP-001 como recomendação, salvo quando uma decisão tiver sido formalmente incorporada aos documentos donos.

Uma proposta não altera implicitamente um documento de referência.

### 2.2. Classificação obrigatória dos itens

Todo item tratado nesta proposta utiliza uma das classificações:

- **IMPLEMENTADO** — há evidência no código e/ou testes da funcionalidade descrita.
- **ESPECIFICADO** — consta em documento aprovado ou decisão registrada, mas sua implementação não foi confirmada.
- **PARCIAL** — parte do comportamento existe; o restante está explicitamente identificado.
- **EM AVALIAÇÃO** — consta como possibilidade ou fase futura, sem decisão de implementação.
- **NOVA PROPOSTA** — sugestão introduzida por esta PROP-001, ainda não aprovada.
- **NÃO CONFIRMADO** — a evidência disponível não permite afirmar o estado.

A classificação deve se referir ao comportamento específico, não ao nome amplo de uma funcionalidade. Por exemplo, “versionamento implementado” não significa que comparação visual entre versões também esteja implementada.

---

## 3. Diagnóstico do produto atual

A análise da Proposta 07, da documentação técnica consultada e dos componentes do projeto indica que a capacidade documental já possui uma base implementada. Portanto, não se recomenda criar um novo módulo documental nem substituir o mecanismo atual.

### 3.1. Componentes existentes

| Componente | Responsabilidade identificada | Situação |
|---|---|---|
| `livingdocs` | Motor documental compartilhado, documentos, versões e conteúdo. | **IMPLEMENTADO** |
| `organization` | Níveis, unidades organizacionais, árvore e vínculos de atendimento. | **IMPLEMENTADO** |
| `access` | Autorização e escopo de acesso por usuário, papel e unidade. | **IMPLEMENTADO** |
| `configuration` | Templates documentais, incluindo `PAGE` e `REGIMENTO`. | **IMPLEMENTADO**, com evolução de configuração pendente |
| `audit` | Auditoria transversal e registro dos eventos relevantes. | **IMPLEMENTADO**; eventos de conhecimento descritos na Proposta 07 |
| Frontend de conhecimento | Wiki, Regimento, navegação, edição e histórico. | **IMPLEMENTADO** na Fase A, conforme Proposta 07 |

Arquivos de código consultados durante a análise incluem:

- `src/main/java/br/com/aquaqe/platform/livingdocs/Blocks.java`
- `src/main/java/br/com/aquaqe/platform/livingdocs/DocumentRepository.java`
- `src/main/java/br/com/aquaqe/platform/livingdocs/PageController.java`
- `src/main/java/br/com/aquaqe/platform/livingdocs/PageService.java`
- `src/main/java/br/com/aquaqe/platform/organization/OrgUnits.java`
- `frontend/src/app/knowledge/knowledge.service.ts`
- `frontend/src/app/pages/wiki/wiki.ts`

A lista identifica evidências consultadas; não representa uma auditoria exaustiva de todos os arquivos, migrações, endpoints e testes do repositório.

### 3.2. Funcionalidades existentes e limites conhecidos

| Capacidade | Situação | Observação |
|---|---|---|
| Um espaço de conhecimento por unidade organizacional | **IMPLEMENTADO** | A própria unidade representa o espaço; não criar entidade paralela de espaço. |
| Wiki com páginas e subpáginas | **IMPLEMENTADO** | Hierarquia de páginas interna à unidade, sem substituir a hierarquia organizacional. |
| Regimento independente do Wiki | **IMPLEMENTADO** | Um Regimento próprio por unidade, com tela e operações próprias. |
| Motor documental compartilhado | **IMPLEMENTADO** | O módulo `livingdocs` atende ao conhecimento organizacional e foi concebido para documentos de artefatos. |
| Editor de texto contínuo | **IMPLEMENTADO** | Tiptap/ProseMirror, com conteúdo estruturado em JSON; não é um editor genérico de diagramas. |
| Histórico e versões | **IMPLEMENTADO** | A existência de histórico não implica comparação lado a lado ou diff semântico. |
| Ciclo governado do Regimento | **IMPLEMENTADO** | Rascunho, revisão, aprovação e publicação conforme template e regras existentes. |
| Publicação de página livre | **IMPLEMENTADO** | Página comum segue o comportamento simplificado descrito na Proposta 07. |
| Leitura de conhecimento publicado das unidades superiores | **IMPLEMENTADO** | Regra de leitura herdada registrada na Proposta 07 e no SSD. Não equivale a aplicabilidade documental configurável. |
| Permissões e isolamento por contexto | **IMPLEMENTADO** | A autorização deve continuar centralizada nos módulos responsáveis; não duplicar regras no Wiki. |
| Auditoria das operações de conhecimento | **IMPLEMENTADO** | Eventos específicos descritos na Proposta 07. |
| Documentos vinculados a Iniciativas e Features no mesmo motor | **ESPECIFICADO / PRÓXIMA ENTREGA**, conforme Proposta 07 | Confirmar a entrega no código antes de alterar a classificação. |
| Configuração de templates pela interface | **EM AVALIAÇÃO — Fase B** | Templates existentes não significam que a interface de administração esteja pronta. |
| Revisão periódica, responsáveis e revisores | **EM AVALIAÇÃO — Fase B** | Não assumir que exista governança completa para todos os tipos documentais. |
| Knowledge Center e busca de conteúdo | **EM AVALIAÇÃO — Fase C** | Não tratar como funcionalidade já entregue. |
| Aplicabilidade explícita e referências avançadas | **EM AVALIAÇÃO — Fase C** | A leitura herdada atual não implementa, por si só, essas capacidades. |
| Comentários, menções, notificações e favoritos | **EM AVALIAÇÃO — Fase D** | Não fazem parte da base implementada descrita na Proposta 07. |
| Anexos e imagens com object storage | **EM AVALIAÇÃO** | A Proposta 07 condiciona essa evolução à integração de armazenamento. |
| Comparação lado a lado de versões | **PARCIAL / EVOLUÇÃO PENDENTE** | O histórico permite consultar versões anteriores; comparação visual permanece posterior. |
| Mapas mentais, diagramas C4 e diagramas especializados editáveis | **NÃO CONFIRMADO / NOVA PROPOSTA** | O editor atual é de texto rico; suporte visual especializado exige avaliação própria. |

A Proposta 07 é a fonte para o faseamento e os requisitos KNOW. Esta tabela resume seu estado para evitar que a PROP-001 repita ou contradiga aquela especificação.

---

## 4. Modelo conceitual que deve ser preservado

A evolução deverá preservar as distinções de domínio já estabelecidas. Elas são importantes para evitar que navegação, propriedade, aplicabilidade e autorização sejam tratadas como a mesma coisa.

| Conceito | Significado | Diretriz |
|---|---|---|
| Unidade organizacional | Contexto organizacional mantido pelo módulo `organization`. | A documentação consome esse contexto; não cria outra árvore organizacional. |
| Documento | Conteúdo identificado e mantido pelo motor `livingdocs`. | Reutilizar o motor existente. |
| Proprietário | Unidade ou artefato ao qual o documento pertence no modelo atual. | Não confundir com quem pode ler ou com quem o documento se aplica. |
| Estrutura de páginas | Relação pai/filho entre páginas do Wiki da mesma unidade. | Não confundir com a árvore de unidades. |
| Abrangência / aplicabilidade | Contextos aos quais determinado conteúdo se aplica. | A aplicabilidade explícita é evolução futura; não presumir que já exista. |
| Visibilidade | Quem pode consultar determinado conteúdo segundo as regras de acesso. | Continua sob responsabilidade do módulo `access` e das regras registradas no SSD. |
| Edição | Quem pode alterar conteúdo ou metadados. | Não decorre automaticamente da aplicabilidade. |
| Aprovação | Decisão de autoridade competente sobre uma versão ou documento governado. | Deve permanecer associada ao fluxo e à versão correspondentes. |
| Versão | Registro histórico do conteúdo em determinado momento. | Versões aprovadas/publicadas devem preservar integridade histórica conforme os contratos vigentes. |
| Referência | Vínculo para outro documento ou artefato sem copiar seu conteúdo. | Evolução proposta; deve preservar origem, autorização e contexto da referência. |

### 4.1. Associação não é aplicabilidade

O proprietário identifica onde o documento é mantido e quem responde por sua manutenção. A aplicabilidade identifica a quais contextos uma diretriz ou informação se destina.

Um documento mantido por uma unidade pode ser relevante para outras unidades. Isso não significa que as unidades leitoras se tornem proprietárias, nem que recebam automaticamente permissão de edição.

### 4.2. Aplicabilidade não é autorização

Um documento pode ser aplicável a uma unidade sem que todo usuário dessa unidade tenha acesso irrestrito a ele. A política de acesso continua sendo verificada pelo backend, conforme o modelo de autorização do produto.

### 4.3. Hierarquia organizacional não é hierarquia documental

A estrutura organizacional é configurável e pertence ao módulo `organization`. A hierarquia de páginas organiza o conteúdo do Wiki dentro de uma unidade. Uma não deve ser implementada como cópia da outra.

Exemplos como Empresa → Unidade de Negócio → Portfólio → Cadeia de Valor → Squad são configurações organizacionais possíveis, não uma árvore fixa a ser codificada no módulo documental.

---

## 5. Escopo desta proposta

Esta PROP-001 não reespecifica a Fase A da Proposta 07. Seu escopo é avaliar evoluções e estabelecer diretrizes para que elas não gerem duplicidade.

### 5.1. Incluído como diretriz

- Preservar o motor documental `livingdocs`.
- Preservar Wiki e Regimento como experiências independentes, compartilhando o motor.
- Preservar a estrutura organizacional configurável do módulo `organization`.
- Preservar autorização e auditoria nos módulos transversais existentes.
- Distinguir propriedade, aplicabilidade, visibilidade, edição e aprovação.
- Evoluir a governança documental sem impor o mesmo fluxo a todo tipo de conteúdo.
- Manter versões e referências rastreáveis.
- Integrar documentos de artefatos ao mesmo motor, conforme o escopo já registrado na Proposta 07.

### 5.2. Fora do escopo desta revisão

- Criar outro módulo Wiki, CMS ou DMS.
- Criar outra tabela ou serviço de versões em paralelo a `livingdocs`.
- Criar uma nova hierarquia de unidades organizacionais dentro da documentação.
- Alterar a árvore de navegação ou o menu sem tratar a mudança na Proposta 06 e nos documentos de UX/UI.
- Substituir o editor Tiptap/ProseMirror sem avaliação técnica e decisão registrada.
- Prometer edição completa de diagramas, mapas mentais ou C4 sem prova de conceito.
- Atualizar automaticamente PRD, SAD, SSD, ADRs ou roadmap apenas pela aprovação desta proposta.
- Introduzir microserviços ou dependências externas sem justificativa arquitetural.

---

## 6. Evoluções propostas

Os itens desta seção são possibilidades de evolução. Não são funcionalidades atuais nem decisões aprovadas.

### 6.1. Governança documental ampliada

**Classificação: EM AVALIAÇÃO — relacionada à Fase B da Proposta 07.**

Avaliar a evolução de:

- Responsável pela manutenção do documento.
- Revisores e aprovadores por tipo documental.
- Periodicidade e data da próxima revisão.
- Lembretes de revisão e sinalização de documentos vencidos.
- Justificativa e resumo obrigatório de alterações, conforme política.
- Histórico de decisões de revisão, aprovação, rejeição e publicação.
- Configuração de templates e políticas sem necessidade de alteração de código.

A política deve permitir fluxos simplificados para páginas informativas e fluxos controlados para documentos normativos. Não se recomenda exigir aprovação formal de toda página.

### 6.2. Aplicabilidade explícita

**Classificação: EM AVALIAÇÃO — relacionada à Fase C da Proposta 07.**

Avaliar metadados que permitam declarar a quais unidades, contextos ou públicos um documento se aplica.

Regras orientadoras:

1. A aplicabilidade não muda o proprietário do documento.
2. A aplicabilidade não concede permissão automaticamente.
3. A unidade de destino não recebe uma cópia do documento por padrão.
4. A interface deve identificar a origem do documento e sua versão.
5. A retirada ou alteração da aplicabilidade deve ser rastreável.
6. A autorização deve ser verificada no momento da consulta.

O mecanismo deve consumir os contratos de `organization` e `access`, sem manter uma hierarquia paralela.

### 6.3. Referências e relacionamentos

**Classificação: EM AVALIAÇÃO — relacionada à Fase C da Proposta 07.**

Avaliar referências tipadas entre documentos e, quando houver contrato disponível, entre documentos e artefatos da plataforma.

Exemplos de relação:

- Referencia.
- Complementa.
- Detalha.
- Depende de.
- Substitui.
- É substituído por.
- Aplica-se a.

A relação deve apontar para a entidade de origem, respeitar autorização e evitar duplicação do conteúdo. Relações que impliquem substituição ou precedência exigem regras explícitas para impedir ciclos e ambiguidades.

### 6.4. Pesquisa e descoberta

**Classificação: EM AVALIAÇÃO — relacionada ao Knowledge Center da Fase C.**

A pesquisa deverá:

- Respeitar autorização e isolamento organizacional.
- Distinguir documentos publicados, rascunhos e históricos conforme as permissões.
- Permitir filtros por tipo, unidade, responsável, estado e vigência, quando esses metadados estiverem disponíveis.
- Indicar a origem e o estado do documento encontrado.
- Não revelar existência, título ou conteúdo de documentos fora do escopo de acesso.

A implementação deve ser alinhada à busca transversal já especificada para a plataforma, evitando um mecanismo isolado de pesquisa documental.

### 6.5. Editor e conteúdo visual especializado

**Classificação: NOVA PROPOSTA — depende de investigação técnica e UX.**

O editor atual deve continuar sendo a experiência padrão de escrita contínua. Avaliar extensões específicas para:

- Diagramas de arquitetura e C4.
- Fluxogramas e mapas de processo.
- Mapas mentais.
- Quadros e tabelas especializadas.
- Incorporação de conteúdo visual externo ou anexado.

A avaliação deverá considerar edição, persistência, versionamento, acessibilidade, exportação, impressão, colaboração, segurança e compatibilidade futura.

Não se deve transformar o editor em uma coleção de formulários ou blocos obrigatórios. Também não se deve afirmar que Tiptap/ProseMirror, por si só, fornece edição nativa de todos esses diagramas.

### 6.6. Comparação e integridade de versões

**Classificação: PARCIAL — evolução a avaliar.**

O histórico de versões já existe. Avaliar comparação entre versões com:

- Identificação de conteúdo adicionado, removido e alterado.
- Comparação de metadados relevantes.
- Identificação da versão publicada e da versão em edição.
- Preservação de versões aprovadas/publicadas.
- Resumo de alterações revisável por uma pessoa.

A comparação visual lado a lado e o diff semântico não devem ser declarados implementados sem evidência.

### 6.7. Integração com documentos de artefatos

**Classificação: ESPECIFICADO / ENTREGA A CONFIRMAR.**

A Proposta 07 registra que documentos de Iniciativas e Features deverão utilizar o mesmo motor `livingdocs`. A evolução deve manter um único mecanismo para:

- Conteúdo.
- Versões.
- Histórico.
- Templates.
- Auditoria.
- Autorização contextual.

A implementação e os contratos devem ser confirmados antes de atualizar o status deste item. Não criar um subsistema de documentos de artefatos separado.

---

## 7. Regras de negócio orientadoras

As regras abaixo são diretrizes para evoluções futuras. Não substituem as regras já especificadas no SSD nem as decisões da Proposta 07.

| ID | Regra orientadora | Classificação |
|---|---|---|
| PROP-RN-001 | O motor documental existente deve ser reutilizado; não deve ser criado um segundo mecanismo de documentos e versões. | Diretriz desta proposta |
| PROP-RN-002 | O proprietário de um documento deve permanecer identificável e não pode ser inferido apenas a partir de sua abrangência. | Princípio de domínio; validar contrato atual |
| PROP-RN-003 | A aplicabilidade não concede, por si só, autorização de leitura ou edição. | Evolução proposta |
| PROP-RN-004 | A leitura de conteúdo publicado herdado deve continuar obedecendo às regras já registradas no SSD e na Proposta 07. | Regra existente |
| PROP-RN-005 | Uma referência documental deve preservar a identificação do documento de origem. | Evolução proposta |
| PROP-RN-006 | A aprovação deve estar associada à versão efetivamente analisada. | Princípio de governança; confirmar alcance no fluxo atual |
| PROP-RN-007 | Alterações em modelos não devem modificar silenciosamente documentos já criados. | Evolução proposta |
| PROP-RN-008 | A consulta, pesquisa e exportação devem respeitar a autorização aplicada pelo backend. | Diretriz arquitetural e de segurança |
| PROP-RN-009 | Eventos relevantes do ciclo documental devem ser auditáveis pelo mecanismo transversal existente. | Regra existente a preservar |
| PROP-RN-010 | A obsolescência ou substituição não deve apagar o histórico necessário à rastreabilidade. | Evolução proposta |
| PROP-RN-011 | A organização poderá adotar fluxos diferentes por tipo documental, dentro das opções suportadas pelo produto. | Evolução proposta |
| PROP-RN-012 | A estrutura de páginas do Wiki não deve ser usada para representar a hierarquia de unidades organizacionais. | Regra de domínio a preservar |
| PROP-RN-013 | Nenhuma integração deve duplicar o conteúdo de origem quando uma referência autorizada for suficiente. | Diretriz de evolução |

As regras classificadas como evolução proposta só passam a ser normativas após decisão e incorporação no documento dono correspondente.

---

## 8. Diretrizes técnicas

### 8.1. Reutilizar os módulos existentes

A evolução deve respeitar as responsabilidades atuais:

| Responsabilidade | Módulo dono |
|---|---|
| Documentos, conteúdo e versões | `livingdocs` |
| Unidades, níveis e vínculos organizacionais | `organization` |
| Identidade, papéis e autorização | `identity` / `access` |
| Templates e configurações suportadas | `configuration` |
| Auditoria | `audit` |
| Interface e navegação | Frontend, conforme contratos e documentos UX/UI |

A tabela é um mapa de responsabilidades, não autorização para alterar limites de módulos. Qualquer mudança de propriedade ou dependência deve ser registrada no SAD/ADR.

### 8.2. Não criar arquitetura paralela

Não propor, sem justificativa e decisão explícita:

- Novo módulo de Wiki.
- Novo serviço de Document Management.
- Novo repositório de versões.
- Nova autorização específica para documentos.
- Nova árvore organizacional.
- Novo mecanismo de auditoria.
- Novo índice de busca isolado da estratégia transversal.

A escolha entre processamento interno, eventos ou integração externa deve seguir o SAD e os ADRs existentes.

### 8.3. Segurança e integridade

Toda evolução deve preservar:

- Autorização validada no backend.
- Isolamento entre organizações.
- Controle de concorrência e conflitos.
- Integridade das versões históricas.
- Auditoria das operações relevantes.
- Validação de conteúdo estruturado e de links.
- Proteção de anexos e arquivos, quando implementados.
- Ausência de vazamento de conteúdo por busca, logs, eventos ou exportação.

Os mecanismos concretos — incluindo RLS, políticas de banco, contratos e testes — devem ser os definidos no SAD/SSD e no código vigente. Esta proposta não introduz um mecanismo alternativo.

---

## 9. Faseamento consolidado

O faseamento oficial da capacidade continua sendo o da Proposta 07. A tabela abaixo o resume para orientar a leitura desta proposta; não cria novo roadmap.

| Etapa | Escopo resumido | Situação registrada |
|---|---|---|
| Fase A | Espaço por unidade, Wiki, páginas, Regimento independente, editor, versões, permissões, publicação e auditoria. | **IMPLEMENTADA**, conforme Proposta 07. |
| Entrega seguinte | Documentos de Iniciativas e Features utilizando o motor comum. | **Próxima entrega / confirmar no código**. |
| Fase B | Templates configuráveis, responsáveis, revisores, revisão periódica e políticas de aprovação. | **EM AVALIAÇÃO**. |
| Fase C | Knowledge Center, busca, aplicabilidade explícita, referências e conteúdo relacionado. | **EM AVALIAÇÃO**. |
| Fase D | Comentários, menções, notificações e favoritos. | **EM AVALIAÇÃO**. |
| Evoluções adicionais | Comparação avançada, diagramas e mapas mentais editáveis, recursos visuais especializados. | **NOVA PROPOSTA / requer investigação**. |

A priorização deve considerar o PRD e o planejamento do produto. Esta PROP-001 não altera o escopo do MVP.

---

## 10. Critérios para aceitar uma evolução

Uma evolução documental só deve ser considerada pronta para especificação/implementação quando:

1. O problema e o público beneficiado estiverem descritos no PRD ou em requisito aprovado.
2. A capacidade não duplicar função já existente em `livingdocs`, `organization`, `access`, `configuration` ou `audit`.
3. A propriedade dos dados e o módulo responsável estiverem definidos.
4. As regras de autorização e isolamento estiverem descritas e testáveis.
5. Os estados e transições do ciclo documental estiverem definidos, quando aplicável.
6. O impacto sobre versões existentes e compatibilidade de conteúdo estiver avaliado.
7. A experiência de navegação e edição estiver validada nos documentos de UX/UI.
8. Os contratos de API, eventos e persistência estiverem definidos no SSD.
9. Os critérios de aceite e testes cobrirem fluxos positivos, negativos, autorização e concorrência.
10. O estado de implementação puder ser verificado por código e testes, não apenas por texto de proposta.

---

## 11. Riscos e decisões pendentes

| Tema | Risco de confusão ou impacto | Tratamento recomendado |
|---|---|---|
| Sobreposição documental | PROP-001 e Proposta 07 podem parecer duas especificações da mesma funcionalidade. | Manter a Proposta 07 como referência funcional e esta PROP-001 como análise de evolução. |
| Status de implementação | Uma funcionalidade pode ser descrita como implementada em uma proposta e ainda estar parcial no código. | Atualizar status somente com evidência de código/testes. |
| Escopo do produto | Regimentos e governança corporativa ampla podem ampliar o posicionamento do produto. | Validar no PRD se o escopo atende à estratégia de Enterprise Agility e Quality Engineering. |
| Aplicabilidade | Pode ser confundida com visibilidade e permissão. | Definir conceitos, casos de uso e matriz de autorização antes de implementar. |
| Referências | Referências podem apontar para versões alteradas, obsoletas ou inacessíveis. | Definir se cada relação aponta para documento atual ou versão fixa. |
| Editor visual | Diagramas e mapas mentais podem exigir tecnologia e persistência distintas do texto rico. | Fazer prova de conceito e registrar decisão antes de ampliar o editor. |
| Busca | Índices podem revelar conteúdo não autorizado. | Aplicar autorização também à indexação, consulta, snippets e resultados. |
| Governança | Fluxos excessivamente configuráveis podem aumentar complexidade. | Começar pelas políticas e templates prioritários, com regras explícitas. |

### 11.1. Decisões necessárias antes de ampliar o escopo

- Confirmar no código a entrega de documentos associados a Iniciativas e Features.
- Priorizar quais tipos documentais além de Wiki e Regimento são relevantes para o produto.
- Decidir quais itens da Fase B e C devem ser priorizados.
- Definir se aplicabilidade explícita é necessária no primeiro ciclo de evolução.
- Decidir se diagramas e mapas mentais são requisito do produto ou integração/extensão opcional.
- Validar se a abrangência corporativa pretendida está alinhada ao PRD vigente.

Nenhuma dessas decisões deve ser presumida como tomada por esta revisão.

---

## 12. Atualização dos documentos de referência

A aprovação de uma evolução não significa atualizar todos os documentos indiscriminadamente. Cada artefato deve receber apenas o conteúdo de sua responsabilidade.

| Documento | Quando atualizar | O que deve receber |
|---|---|---|
| **PRD** | Quando a capacidade ou prioridade for aprovada como escopo de produto. | Problema, personas, casos de uso, requisitos, prioridade e critérios de aceite. |
| **SAD** | Quando houver mudança ou detalhamento de arquitetura. | Responsabilidades dos módulos, dependências, integrações, segurança e decisões arquiteturais. |
| **SSD** | Quando contratos e comportamento forem definidos para implementação. | API, eventos, modelo de dados, estados, validações e regras técnicas. |
| **ADR** | Quando uma decisão arquitetural exigir registro de alternativas e trade-offs. | Decisão, contexto, alternativas, consequências e status. |
| **UX/UI** | Quando navegação, editor ou fluxos mudarem. | Estrutura de telas, interação, acessibilidade e estados da interface. |
| **Proposta 07** | Quando mudar o estado, o faseamento ou uma decisão da capacidade de conhecimento. | Decisões e requisitos KNOW, preservando o histórico da proposta. |
| **PROP-001** | Quando a análise de lacunas ou as recomendações mudarem. | Estado consolidado, classificação e recomendações, sem duplicar especificações. |
| **Testes/QA** | Quando comportamento novo for aprovado para implementação. | Cenários de aceitação, autorização, isolamento, regressão e compatibilidade. |

---

## 13. Recomendação

Recomenda-se:

1. **Manter a Proposta 07** como documento funcional principal da capacidade de conhecimento organizacional.
2. **Manter esta PROP-001 como documento complementar**, dedicado à análise do que já existe e às evoluções que ainda precisam de decisão.
3. Não criar outro motor documental, Wiki, módulo de organização ou mecanismo de autorização.
4. Tratar as Fases B, C e D conforme priorização explícita, sem considerá-las entregues.
5. Fazer qualquer ampliação do editor por meio de investigação técnica e validação de UX.
6. Atualizar PRD, SAD, SSD, ADRs e UX/UI somente quando houver decisão correspondente e conteúdo suficientemente especificado.

**Princípio orientador:** uma capacidade documental, um motor compartilhado, responsabilidades de módulo explícitas e status de implementação verificáveis. A proposta deve esclarecer o produto — nunca criar uma segunda fonte de verdade.
