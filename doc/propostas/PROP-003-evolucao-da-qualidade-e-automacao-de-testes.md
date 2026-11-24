# PROP-003 — Evolução da Qualidade e Automação de Testes

| Campo | Valor |
|---|---|
| Produto | AQuA-QE Enterprise Agility Platform |
| Tipo | Proposta técnica e de evolução da engenharia de qualidade |
| Versão | 1.0 |
| Data | 2026-11-24 |
| Status | **Aprovada em etapas (2026-11-24).** Fases 0 e 1 implementadas (ver [Situação](#situação-2026-11-24)); Fases 2 a 4 aguardam priorização. |
| Proposta complementar | [PROP-001 — Evolução da Gestão do Conhecimento e Documentação Organizacional](PROP-001-gestao-do-conhecimento-e-documentacao-organizacional.md) |
| Proposta complementar | [PROP-002 — Organização Flexível da Wiki](PROP-002-organizacao-flexivel-da-wiki.md) |
| Documentos de referência | [PRD](../PRD.md), [SAD](../SAD.md), [SSD](../SSD.md), [QA](../QA.md), [Operação](../OPERACAO.md) |
| Referência arquitetural de automação | [Layered Keyword-Driven Framework (LKDF)](https://github.com/dufelizardo/Layered-Keyword-Driven-Framework-LKDF) |

> **Importante:** esta proposta consolida oportunidades de evolução identificadas na análise do projeto de testes. Ela não declara que todas as recomendações já estejam implementadas, não substitui o plano de QA e não altera automaticamente requisitos, arquitetura, contratos ou pipeline. Cada mudança depende de validação, priorização e atualização dos documentos responsáveis.

## Situação (2026-11-24)

Decisão do usuário: começar pela **Fase 0** (baseline e saneamento) e pela **Fase 1** (guardrails LKDF), nesta ordem, antes das propostas de Wiki (PROP-002) e dos documentos do artefato (#63).

| Fase | Situação | Onde |
|---|---|---|
| 0 — Baseline e saneamento | **Implementada.** `test/robot/tools/inventory.py` calcula suítes, casos, camadas e rastreabilidade a cada execução (14 suítes e 51 casos em 2026-11-24; o único caso sem identificador ganhou a tag `SAD-7.9`). O `doc/QA.md` foi reconciliado: saiu a frase "o projeto ainda não tem código", e a estrutura e o pipeline foram atualizados. | [QA, seção 3.2](../QA.md#32-estrutura-de-pastas) |
| 1 — Guardrails LKDF | **Implementada.** `test/robot/tools/lkdf_lint.py`, com 14 testes próprios, roda no job `robot-dryrun` e bloqueia o pipeline. Ele encontrou 13 violações reais, todas corrigidas: <br>• cenários de interface abrindo e fechando o navegador pelo POM, que agora passam pelo `flow/common/browser_flow.resource`; <br>• um FLOW com `Wait For Elements State`; <br>• um cenário chamando `Get Card Key`. <br>Há uma exceção registrada. | [QA, seção 3.4](../QA.md#34-conformidade-verificada-guardrail-lkdf), [OPERACAO, seção 6.3](../OPERACAO.md#63-checks-do-pipeline-de-testes) |
| 2 — Autorização e isolamento | Aguarda priorização. | — |
| 3 — Cobertura por domínio e rastreabilidade | Aguarda priorização. | — |
| 4 — Confiabilidade e indicadores | Parcial antes desta proposta: retentativa seletiva e issue de teste instável (ADR-18). | [QA, seção 9](../QA.md#9-ambiente-e-execução) |

**Decisões da seção 15 respondidas nesta etapa**
1. **Regras bloqueantes desde a primeira versão:** LKDF-QA-001, 002, 003, 004, 005, 007, 008 e 010 (caminho local). Ficam como **avisos**: LKDF-QA-009 (documentação, tags e rastreabilidade) e o literal de segredo da 010. A LKDF-QA-006 (RESOURCE sem lógica) entra quando houver keyword em `resource/`.
2. **Ferramenta:** o parser do próprio Robot Framework (`robot.api`) e a libdoc das bibliotecas seguras, sem dependência nova.
5. **Relatório:** inventário em Markdown e JSON, publicado como artefato `robot-inventory` do pipeline, com retenção de 30 dias. O relatório de rastreabilidade com resultados de execução fica para a Fase 3.
6. **Checks obrigatórios:** dry-run, testes do validador e conformidade LKDF em todo push e PR, inclusive na promoção para `main`.

---

## 1. Objetivo

Estabelecer uma evolução coordenada da engenharia de qualidade do AQuA-QE, preservando a arquitetura de automação baseada no **Layered Keyword-Driven Framework (LKDF)** e ampliando a capacidade de verificar, de forma repetível e rastreável, a qualidade dos domínios da plataforma.

A proposta organiza cinco frentes:

1. **Conformidade arquitetural LKDF:** transformar as regras das camadas em verificações automatizadas.
2. **Cobertura funcional e de segurança:** ampliar a cobertura de autorização, isolamento organizacional e jornadas críticas.
3. **Rastreabilidade:** relacionar requisitos, domínios, suítes, casos e resultados de execução.
4. **Visibilidade da cobertura:** apresentar o que está coberto por testes unitários, integração, API, UI e demais níveis.
5. **Confiabilidade da execução:** tratar instabilidade, dados de teste, ambiente, evidências e resultados do pipeline.

O objetivo não é aumentar indiscriminadamente a quantidade de testes. É aumentar a confiança de que os comportamentos importantes estão especificados, cobertos, executados e protegidos contra regressões.

## 2. Contexto e diagnóstico inicial

A análise do repositório identificou uma base de automação Robot Framework organizada em projetos de API e UI, com diretórios correspondentes às camadas POM, FLOW, SCENARIO e TEST, além de recursos compartilhados.

Também foram identificados:

- Suítes Robot para operações de diferentes domínios da plataforma.
- Uso de RequestsLibrary para aceitação de API e Browser/Playwright para interface.
- Scripts para execução local, execução de CI e retentativa seletiva de casos que falharam.
- Etapa de dry-run no pipeline para verificar sintaxe e imports.
- Execução de aceitação contra a aplicação inicializada em ambiente integrado.
- Integração com testes JUnit, Testcontainers, Spring Modulith e testes do frontend.
- Estratégia de rastreabilidade por identificadores de requisitos e tags.

O levantamento preliminar realizado durante a análise identificou 14 arquivos de suíte, com 51 casos de teste nos arquivos examinados: nove suítes de API e cinco de UI. Esse número é uma fotografia do conjunto inspecionado, não uma métrica oficial de cobertura nem uma afirmação de que todos os testes existentes foram inventariados. Deve ser recalculado automaticamente ou confirmado em uma auditoria completa antes de servir como baseline.

### 2.1. Evidências e limites da análise

A análise consultou código, scripts, documentação e configuração de pipeline no repositório. Também foi localizada uma execução do GitHub Actions associada ao commit `6121bd6be68195facb6aa0e61024909d366ef12d`, em que os jobs consultados apresentaram sucesso, incluindo Robot Dry Run e Acceptance Robot.

Essa evidência demonstra que houve uma execução bem-sucedida naquele commit e naquele contexto. Não comprova que todas as execuções atuais estejam verdes, que todos os domínios estejam cobertos ou que não existam defeitos.

O documento `doc/QA.md` contém trechos que não refletem de forma consistente o estado atual: há uma declaração de que o projeto ainda não possui código, enquanto outras seções descrevem testes JUnit, Robot, Vitest e funcionalidades já implementadas. A reconciliação desse estado documental é parte desta proposta.

### 2.2. O que a proposta não presume

- A existência de uma suíte não comprova cobertura integral do domínio.
- Um caso aprovado não comprova que todos os caminhos de negócio foram exercitados.
- Um dry-run bem-sucedido valida sintaxe e imports, não comportamento funcional.
- Uma execução verde do pipeline não substitui análise de cobertura, qualidade dos dados de teste ou avaliação de riscos.
- A estrutura de diretórios LKDF, isoladamente, não comprova conformidade arquitetural.
- A ausência de uma suíte Robot não significa ausência de testes em JUnit ou no frontend.
- A quantidade de casos não deve ser usada isoladamente como indicador de qualidade.

## 3. Princípios orientadores

1. **LKDF como arquitetura, não como convenção de nomenclatura.** As responsabilidades e dependências das camadas devem ser verificáveis.
2. **Pirâmide e complementaridade de testes.** Unidade, integração, fronteiras de módulo, API e UI têm objetivos distintos.
3. **Risco antes de volume.** Priorizar segurança, autorização, integridade, fluxos críticos e regras de negócio de maior impacto.
4. **Rastreabilidade verificável.** Um requisito deve apontar para evidências de teste, e lacunas devem ser visíveis.
5. **Dados reproduzíveis.** Execuções devem ser repetíveis e independentes, inclusive quando executadas em paralelo.
6. **Falha real não pode ser mascarada por retentativa.** Testes instáveis devem continuar visíveis e gerar trabalho de correção.
7. **Backend é a autoridade de segurança.** Ocultar ações na interface não substitui autorização no servidor.
8. **Evolução incremental.** Reutilizar ferramentas, módulos e mecanismos existentes antes de introduzir novas dependências.
9. **Documentação alinhada ao código.** O estado descrito deve ser confirmado por evidências e revisado junto com mudanças relevantes.
10. **Resultado auditável.** Execuções devem preservar logs e relatórios suficientes para investigar falhas.

## 4. Escopo

### 4.1. Incluído

- Auditoria automatizada de conformidade LKDF.
- Matriz de cobertura por domínio, requisito, risco e nível de teste.
- Evolução da cobertura de autorização e isolamento organizacional.
- Cobertura de jornadas e contratos críticos de API.
- Geração e validação de rastreabilidade.
- Melhoria de dados, fixtures e preparação do ambiente de teste.
- Indicadores de execução, instabilidade e lacunas.
- Revisão e atualização do plano de QA e das instruções de execução.

### 4.2. Fora do escopo desta proposta

- Substituir o Robot Framework, o LKDF, JUnit ou a stack atual sem justificativa técnica independente.
- Reescrever as suítes existentes apenas por preferência de estilo.
- Automatizar toda combinação possível de telas, campos e operações sem avaliação de risco.
- Criar um novo módulo de produto para armazenar resultados de testes nesta etapa.
- Introduzir uma plataforma externa de gestão de testes sem decisão de produto e arquitetura.
- Tratar teste de carga, desempenho, caos ou recuperação de desastre como automaticamente cobertos pelo escopo atual. Esses temas exigem objetivos, ambientes, métricas e propostas específicas.
- Alterar regras de negócio ou contratos de autorização apenas para facilitar a automação.

## 5. Frente A — Conformidade arquitetural LKDF

### 5.1. Objetivo

Converter as regras do LKDF em guardrails automatizados que detectem violações antes que sejam incorporadas à branch de integração.

A organização atual das pastas é uma boa base, mas a conformidade deve ser verificada sobre o conteúdo dos arquivos, as chamadas entre keywords, os imports e as dependências efetivas.

### 5.2. Regras normativas a verificar

| ID | Regra | Verificação esperada |
|---|---|---|
| LKDF-QA-001 | A direção de execução é TEST → SCENARIO → FLOW → POM. | Detectar chamadas ou imports que invertam ou pulem camadas. |
| LKDF-QA-002 | TEST declara casos, dados de entrada, tags e documentação. | Detectar lógica condicional e assertions funcionais diretamente em TEST. |
| LKDF-QA-003 | SCENARIO descreve a jornada e encaminha a execução para FLOW. | Detectar acesso direto a POM, chamadas técnicas ou assertions em SCENARIO. |
| LKDF-QA-004 | FLOW concentra orquestração, decisões funcionais e assertions. | Detectar chamadas HTTP, uso direto de drivers ou seletores de interface em FLOW. |
| LKDF-QA-005 | POM encapsula a interação técnica com o sistema sob teste. | Detectar regras de negócio, decisões de fluxo e assertions funcionais em POM. |
| LKDF-QA-006 | RESOURCE fornece dados, configurações, schemas, locators e agregadores. | Detectar dependências de RESOURCE para camadas superiores ou lógica de negócio escondida em recursos. |
| LKDF-QA-007 | As dependências entre camadas são acíclicas e unidirecionais. | Validar grafo de dependências e ausência de ciclos. |
| LKDF-QA-008 | API e UI mantêm seus adaptadores e recursos específicos separados. | Detectar imports indevidos entre POMs, locators ou configurações de API e UI. |
| LKDF-QA-009 | A convenção de nomenclatura e documentação é aplicada de forma consistente. | Validar presença de documentação, tags e identificadores de rastreabilidade. |
| LKDF-QA-010 | A execução não deve depender de caminhos locais ou segredos versionados. | Verificar variáveis de ambiente, arquivos de configuração e possíveis credenciais expostas. |

As regras devem considerar a sintaxe e as particularidades do Robot Framework. Não se recomenda começar com uma análise textual simplista que gere falsos positivos. A implementação deverá avaliar parser/modelo de dados do Robot Framework e, quando necessário, metadados explícitos nos arquivos.

### 5.3. Classificação das violações

- **Erro bloqueante:** dependência proibida, inversão de camada, chamada técnica fora de POM ou violação que comprometa a arquitetura.
- **Aviso:** inconsistência de documentação, tag ausente ou convenção que ainda não represente risco estrutural.
- **Exceção justificada:** desvio deliberado, documentado, com responsável, motivo e prazo de revisão.

Exceções não devem transformar o guardrail em uma lista permanente de permissões irrestritas.

### 5.4. Integração ao pipeline

Recomenda-se criar uma etapa específica de validação arquitetural, executada em pull requests e pushes na branch de desenvolvimento. Violações bloqueantes devem impedir a promoção.

O relatório deverá indicar arquivo, linha ou keyword, regra violada, camada de origem e camada de destino sempre que essa informação puder ser determinada.

### 5.5. Critérios de aceite

1. As regras aprovadas possuem verificações automatizadas.
2. Uma violação intencional de cada regra bloqueante é detectada em teste do próprio validador.
3. O pipeline executa o validador sem depender da aplicação estar no ar.
4. O relatório permite localizar e corrigir a violação.
5. Exceções são explícitas, rastreáveis e revisáveis.
6. O validador não classifica automaticamente como erro comportamentos que não consegue interpretar com segurança.

## 6. Frente B — Cobertura de autorização e isolamento organizacional

### 6.1. Objetivo

Evoluir a cobertura de segurança para demonstrar que usuários podem consultar e executar somente as operações permitidas pelo papel, pelo contexto organizacional e pelo vínculo aplicável.

Esta é a frente de maior prioridade funcional e de risco.

### 6.2. Dimensões da matriz

A matriz de autorização deverá considerar, conforme as regras efetivamente aprovadas no SSD:

- Persona e papel.
- Unidade organizacional de atuação.
- Relação entre a unidade do usuário e a unidade proprietária do recurso.
- Relação hierárquica: mesma unidade, ancestral, descendente, ramo relacionado ou ramo sem vínculo.
- Tipo de recurso e domínio.
- Operação: listar, consultar detalhe, pesquisar, criar, editar, mover, aprovar, arquivar e excluir, quando aplicável.
- Estado do recurso e do usuário.
- Resultado esperado e motivo da decisão.

A matriz não deve assumir que todos os papéis possuem a mesma semântica em todos os módulos. Cada combinação deve derivar das regras do SSD e das decisões de produto.

### 6.3. Modelo de cenários

| Dimensão | Exemplos de variação |
|---|---|
| Papel | PLATFORM_ADMIN, BUSINESS_OWNER, PRODUCT_MANAGER, TECH_LEAD, UX_DESIGNER, QA_ENGINEER, SQUAD_MEMBER, VIEWER |
| Relação organizacional | Mesma unidade, ancestral, descendente, ramo com vínculo, ramo sem vínculo |
| Operação | Leitura, criação, alteração, aprovação, arquivamento |
| Resultado | Permitido, não autenticado, proibido ou recurso não visível |
| Contexto | Artefato, documento, quadro, configuração, auditoria |

A lista é um conjunto inicial de dimensões extraído do plano de QA; a matriz final deve ser conferida com os papéis e regras vigentes no SSD.

### 6.4. Regras essenciais

1. Os testes devem validar a autorização no backend por HTTP, não somente a presença ou ausência de botões.
2. Cenários de leitura e escrita devem ser independentes.
3. O teste deve distinguir usuário sem autenticação, usuário autenticado sem permissão e recurso não visível, conforme o contrato de erro vigente.
4. A resposta para recurso invisível deve respeitar a regra documentada de não revelar sua existência, quando aplicável.
5. Acesso por vínculo não deve permitir acesso lateral a unidades sem relação autorizada.
6. Testes devem cobrir listagem, busca e detalhe, pois uma proteção aplicada somente ao detalhe pode vazar metadados em coleções.
7. Anexos, documentos, referências e downloads devem respeitar a autorização do recurso relacionado.
8. A interface deve refletir as permissões, mas o backend permanece a fonte de verdade.
9. Operações administrativas e de aprovação devem testar segregação de funções quando essa regra estiver aprovada.
10. Dados de teste devem permitir provar tanto o acesso permitido quanto o negado.

### 6.5. Implementação no LKDF

Recomenda-se manter os dados variáveis na camada RESOURCE, os cenários declarativos em SCENARIO, a seleção de persona e as assertions de autorização em FLOW, e a interação HTTP em POM.

A matriz pode ser data-driven, mas cada linha precisa ter identificação própria e rastreabilidade para a regra de autorização que verifica.

### 6.6. Critérios de aceite

1. Cada regra de autorização aprovada no SSD possui ao menos um cenário positivo e um negativo relevante.
2. Os principais contextos organizacionais estão representados.
3. Listagem, detalhe, busca e operações de escrita são cobertos onde aplicável.
4. Acesso a documentos e anexos não contorna as regras do recurso proprietário.
5. A matriz permite identificar combinações ainda não testadas.
6. Resultados de autorização são verificáveis por contrato HTTP, sem depender exclusivamente da UI.

## 7. Frente C — Cobertura funcional por domínio e risco

### 7.1. Objetivo

Construir uma visão única da cobertura de qualidade sem exigir que toda funcionalidade seja testada em todos os níveis.

Cada domínio deverá identificar suas jornadas críticas, regras de negócio, contratos externos, riscos e nível de teste adequado.

### 7.2. Níveis de teste

| Nível | Responsabilidade principal | Ferramentas existentes |
|---|---|---|
| Unidade de backend | Regras isoladas, cálculos, validações e decisões locais. | JUnit 5 |
| Integração de backend | Persistência, transações, eventos, migrações e comportamento integrado. | Spring Boot Test, Testcontainers |
| Fronteiras de módulos | Restrições de dependência entre módulos. | Spring Modulith |
| Unidade de frontend | Componentes, serviços e comportamento local da interface. | Ferramentas do Angular/Vitest conforme o projeto |
| Aceitação de API | Contratos HTTP, autorização, regras observáveis e integração do produto. | Robot Framework + RequestsLibrary |
| Interface e jornadas | Fluxos do usuário e comportamento visível no navegador. | Robot Framework + Browser/Playwright |

A seleção do nível deve considerar custo, velocidade, estabilidade e risco. Testes de UI não devem duplicar indiscriminadamente todas as combinações já verificadas por testes de API ou unidade.

### 7.3. Matriz de cobertura por domínio

Criar e manter uma matriz com, no mínimo:

| Campo | Descrição |
|---|---|
| Domínio/módulo | Área funcional da plataforma. |
| Capacidade | Operação ou comportamento verificável. |
| Requisito/regra | Identificador do PRD, SSD, ADR ou decisão correspondente. |
| Risco | Impacto e probabilidade definidos pela estratégia de QA. |
| Teste unitário | Caso ou conjunto de casos que cobre a regra. |
| Teste de integração | Caso que valida colaboração entre componentes. |
| Teste de API | Suíte/caso Robot, quando aplicável. |
| Teste de UI | Suíte/caso Robot, quando aplicável. |
| Status | Coberto, parcialmente coberto, não coberto, não aplicável ou não confirmado. |
| Última evidência | Identificador da execução e data. |

### 7.4. Priorização

A ordem recomendada de evolução é:

1. Segurança, autenticação, autorização e isolamento.
2. Integridade de dados, auditoria e operações irreversíveis.
3. Workflows, gates, aprovações e segregação de funções.
4. Documentos, versionamento e concorrência.
5. Estrutura organizacional, navegação e contexto herdado.
6. Quadros, backlog, sprints e portfolio.
7. Configuração e administração.
8. Experiências de interface e conveniência.

A prioridade final deverá considerar a matriz de risco e os requisitos efetivamente incluídos na release.

### 7.5. Critérios de aceite

1. Todos os domínios do escopo possuem avaliação de cobertura.
2. Cada capacidade crítica está ligada a um ou mais testes adequados.
3. Lacunas são identificadas sem confundir ausência de Robot com ausência de testes em outros níveis.
4. Testes redundantes são identificados e justificados, não removidos automaticamente.
5. Requisitos sem evidência de teste aparecem como lacunas.
6. A matriz pode ser atualizada a partir de dados do repositório e dos resultados de execução.

## 8. Frente D — Rastreabilidade de requisitos e resultados

### 8.1. Objetivo

Tornar verificável a relação entre necessidade de produto e evidência de qualidade.

Fluxo de rastreabilidade proposto:

`Requisito / Regra → Domínio → Suíte → Caso de teste → Execução → Resultado / Evidência`

A rastreabilidade deve permitir responder:

- Quais testes verificam determinado requisito?
- Quais requisitos não possuem teste associado?
- Quais casos falharam na última execução?
- Qual foi a última execução bem-sucedida?
- Quais domínios apresentam lacunas de cobertura?
- Quais testes estão instáveis?
- Quais mudanças de código podem exigir revisão de cobertura?

### 8.2. Identificadores e tags

Preservar a convenção atual de identificadores de requisitos e tags, incluindo os identificadores de PRD, frentes de evolução, achados de revisão e tipos de execução.

Recomenda-se estabelecer um vocabulário controlado e validar:

- Existência do identificador referenciado.
- Correspondência entre tag e requisito.
- Consistência entre nome do caso e metadata.
- Ausência de requisitos órfãos.
- Ausência de casos sem referência quando a rastreabilidade for obrigatória.
- Identificação de casos que cobrem mais de uma regra, com justificativa.

Não criar identificadores fictícios para preencher lacunas. Requisitos sem cobertura devem permanecer explicitamente sem cobertura.

### 8.3. Relatório

A partir dos arquivos de resultado do Robot Framework e dos relatórios dos demais níveis, produzir um relatório consolidado com:

- Requisito.
- Domínio.
- Casos relacionados.
- Último resultado conhecido.
- Data e identificador da execução.
- Link para logs e evidências disponíveis.
- Situação da cobertura.

A integração com JUnit e frontend deverá utilizar os formatos de relatório já gerados pelas ferramentas, sem introduzir um formato proprietário desnecessário.

### 8.4. Critérios de aceite

1. É possível navegar de requisito para os testes associados.
2. Requisitos sem testes são listados como lacunas.
3. Casos sem rastreabilidade obrigatória são identificados.
4. Resultados exibidos apontam para uma execução real.
5. O relatório distingue ausência de execução, falha e sucesso.
6. A geração do relatório não altera os resultados originais.

## 9. Frente E — Confiabilidade, dados e execução

### 9.1. Objetivo

Reduzir falsos resultados, dependências ocultas e instabilidade, mantendo a capacidade de reproduzir falhas localmente e no CI.

### 9.2. Dados e isolamento

Recomenda-se:

- Padronizar fixtures e seeds por domínio.
- Utilizar identificadores únicos em dados que não devam colidir.
- Definir pré-condições e pós-condições de cada suíte.
- Evitar dependência da ordem de execução dos testes.
- Permitir execução isolada de um caso ou suíte.
- Separar dados estáticos de configuração e dados criados durante a execução.
- Documentar a limpeza de dados e o comportamento em caso de falha.
- Evitar compartilhamento concorrente de usuários ou recursos mutáveis sem isolamento explícito.
- Garantir que credenciais e segredos venham do ambiente seguro de execução.

### 9.3. Testes instáveis

A retentativa seletiva existente deve ser mantida como mecanismo de diagnóstico, não como forma de transformar falhas em sucesso silencioso.

Um teste que falha inicialmente e passa na retentativa deve:

- Permanecer identificado como instável.
- Gerar evidência da primeira e da segunda execução.
- Ser associado a issue ou item de débito técnico.
- Ter causa investigada: sincronização, dados, concorrência, ambiente, dependência externa ou defeito real.
- Ser removido da condição de instável somente após correção e execução estável.

A política de retentativa deve continuar limitada e explícita. Não aumentar o número de tentativas apenas para obter pipeline verde.

### 9.4. Evidências e relatórios

Preservar, conforme o nível de teste:

- Robot `output.xml`, log e relatório HTML.
- Relatórios JUnit/Surefire.
- Relatórios do frontend.
- Logs de inicialização da aplicação e serviços, quando houver falha.
- Screenshots e traces de navegador nos casos em que forem úteis e seguros.
- Identificador do commit, workflow e ambiente.
- Classificação de falha e informação de retentativa.

Dados sensíveis, tokens e informações pessoais não devem ser publicados nos artefatos.

### 9.5. Ambiente

O ambiente de aceitação deve ser reproduzível e possuir versões controladas de dependências, navegador, banco, Keycloak e aplicação. A configuração deve permanecer centralizada nos mecanismos já utilizados pelo projeto.

Não se recomenda manter ambientes de teste com dados compartilhados e mutáveis sem política explícita de isolamento e limpeza.

### 9.6. Critérios de aceite

1. Uma suíte pode ser executada isoladamente e reproduzida localmente.
2. Dados de teste não dependem da ordem de execução.
3. Falhas preservam evidências suficientes para diagnóstico.
4. Retentativas não ocultam a primeira falha.
5. Testes instáveis geram rastreabilidade de correção.
6. Artefatos não expõem segredos ou dados sensíveis.
7. O pipeline informa claramente qual commit e ambiente foram testados.

## 10. Indicadores de qualidade

Os indicadores deverão orientar decisões e não incentivar crescimento artificial de volume.

| Indicador | Definição | Uso |
|---|---|---|
| Cobertura de requisitos críticos | Requisitos críticos com evidência de teste / total de requisitos críticos aplicáveis. | Identificar risco sem cobertura. |
| Cobertura por domínio | Capacidades críticas cobertas / capacidades críticas mapeadas por domínio. | Comparar lacunas entre domínios. |
| Conformidade LKDF | Regras arquiteturais aprovadas sem violação / total de regras verificadas. | Medir erosão arquitetural. |
| Taxa de sucesso na primeira execução | Casos aprovados sem retentativa / casos executados. | Medir confiabilidade real. |
| Taxa de instabilidade | Casos que falham e passam na retentativa / casos executados. | Priorizar correção de flakiness. |
| Tempo de feedback | Tempo entre início da execução e resultado disponível. | Controlar custo do pipeline. |
| Falhas escapadas | Defeitos identificados após a etapa de teste prevista. | Avaliar efetividade, com análise de contexto. |
| Requisitos sem evidência | Requisitos aplicáveis sem caso associado ou execução válida. | Tornar lacunas explícitas. |

Metas numéricas deverão ser definidas após a obtenção de uma baseline confiável. Não se recomenda fixar metas de 100% para cobertura de linhas ou quantidade de casos, pois isso pode gerar testes de baixo valor e distorcer prioridades.

## 11. Faseamento recomendado

O faseamento abaixo é uma recomendação de execução, não um compromisso de roadmap aprovado.

### Fase 0 — Baseline e saneamento documental

- Conferir a branch e o estado atual dos arquivos de teste.
- Inventariar suítes, casos, recursos e dependências.
- Reconciliar o conteúdo de `doc/QA.md` com a implementação.
- Identificar inconsistências entre README, QA, scripts e pipeline.
- Definir baseline de cobertura e execução.
- Registrar lacunas e riscos conhecidos.

**Saída:** inventário validado e plano de QA coerente com o código.

### Fase 1 — Guardrails LKDF

- Definir regras automatizáveis.
- Implementar validador de dependências e responsabilidades.
- Criar testes do próprio validador.
- Integrar ao pipeline como check obrigatório.
- Estabelecer política de exceções.

**Saída:** violações arquiteturais bloqueantes são detectadas antes da promoção.

### Fase 2 — Autorização e isolamento

- Consolidar matriz papel × relação organizacional × operação.
- Preparar dados e personas reproduzíveis.
- Implementar cenários positivos e negativos prioritários.
- Cobrir listagem, detalhe, busca e escrita.
- Validar anexos e documentos relacionados.

**Saída:** evidência automatizada para as regras de acesso de maior risco.

### Fase 3 — Cobertura por domínio e rastreabilidade

- Mapear requisitos e capacidades críticas.
- Relacionar testes nos diferentes níveis.
- Gerar relatório de requisitos cobertos e lacunas.
- Identificar redundâncias e pontos sem evidência.

**Saída:** visão consolidada da cobertura por requisito e domínio.

### Fase 4 — Confiabilidade e indicadores

- Evoluir isolamento de dados e execução.
- Padronizar evidências.
- Acompanhar testes instáveis.
- Publicar indicadores e histórico.
- Ajustar tempos e estratégias de execução com base em dados.

**Saída:** execução mais previsível, diagnóstica e mensurável.

## 12. Governança e responsabilidades

| Papel | Responsabilidade proposta |
|---|---|
| Product Owner | Priorizar riscos e capacidades de produto que exigem evidência. |
| Tech Lead / Arquitetura | Aprovar regras arquiteturais, exceções e impacto técnico. |
| QA / Quality Engineering | Definir estratégia, matriz de cobertura, critérios e evidências. |
| Desenvolvedores | Implementar testes dos níveis adequados junto com a funcionalidade e corrigir violações. |
| Responsáveis pelos domínios | Validar regras de negócio, personas, relações e cenários críticos. |
| Responsáveis pelo pipeline | Manter checks, artefatos, ambiente e política de promoção. |

A responsabilidade pela qualidade é compartilhada. A automação não deve ser tratada como uma etapa isolada executada somente após o desenvolvimento.

## 13. Impactos nos documentos e componentes

Caso aprovada, a proposta deverá ser incorporada nos documentos donos, sem duplicar especificações.

| Documento/componente | Alteração necessária após aprovação |
|---|---|
| `doc/QA.md` | Atualizar baseline, arquitetura, cobertura, rastreabilidade, execução e critérios de saída. |
| `doc/SAD.md` | Registrar decisões arquiteturais caso o validador, relatórios ou integração introduzam componentes ou dependências relevantes. |
| `doc/SSD.md` | Atualizar contratos ou requisitos de testabilidade somente quando houver mudança correspondente no produto. |
| `doc/OPERACAO.md` | Documentar checks obrigatórios, artefatos, política de promoção e diagnóstico. |
| `.github/workflows/pipeline.yml` | Integrar guardrails e publicação dos relatórios aprovados. |
| `test/robot/` | Evoluir suites, recursos, dados, validações e rastreabilidade conforme o LKDF. |
| Testes backend/frontend | Complementar cobertura de domínio nos níveis adequados, sem duplicação automática. |

A aprovação desta proposta não autoriza, por si só, alterar contratos funcionais ou arquitetura de produção.

## 14. Riscos e medidas de mitigação

| Risco | Consequência | Mitigação |
|---|---|---|
| Validador LKDF simplista | Falsos positivos ou violações não detectadas. | Utilizar parser, regras testáveis e implantação incremental. |
| Matriz de autorização excessivamente grande | Alto custo de execução e manutenção. | Priorização por risco, equivalência de regras e particionamento data-driven. |
| Dados compartilhados | Testes concorrentes interferem uns nos outros. | Isolamento, identificadores únicos e limpeza controlada. |
| Retentativas mascaram defeitos | Falsa percepção de estabilidade. | Medir primeira execução e manter registro de instabilidade. |
| Rastreabilidade manual | Informação desatualizada e divergente. | Gerar relatórios a partir de tags e resultados reais. |
| Métricas de volume | Incentivo a testes redundantes ou frágeis. | Priorizar risco, cobertura de regra e efetividade. |
| Pipeline excessivamente lento | Redução de frequência de integração. | Separar checks rápidos de aceitação completa e medir tempo de feedback. |
| Documentação divergente | Decisões baseadas em estado incorreto. | Revisão documental vinculada às mudanças de código. |

## 15. Decisões pendentes

As questões abaixo precisam de decisão antes de transformar a proposta em plano de implementação:

1. Quais regras LKDF serão bloqueantes desde a primeira versão do validador?
2. Qual ferramenta ou implementação será usada para analisar os recursos Robot Framework?
3. Qual é a matriz oficial e vigente de papéis, relações organizacionais e operações?
4. Quais requisitos serão classificados como críticos para a primeira cobertura?
5. Qual será o formato e o local de publicação do relatório consolidado?
6. Quais checks serão obrigatórios em pull requests para `developer` e para promoção a `main`?
7. Qual retenção será aplicada aos artefatos de execução?
8. Quais metas iniciais serão estabelecidas após a baseline?
9. Como serão tratados testes instáveis que dependem de serviços ou ambientes externos?
10. Qual será o responsável pela revisão periódica da matriz de cobertura e das exceções LKDF?

## 16. Critérios de conclusão da iniciativa

A iniciativa poderá ser considerada concluída quando:

- As regras LKDF aprovadas estiverem protegidas por verificações automatizadas no pipeline.
- A matriz de autorização e isolamento cobrir os cenários críticos definidos.
- Todos os domínios do escopo tiverem uma avaliação de cobertura por nível de teste.
- Requisitos críticos sem evidência forem identificados e tratados ou formalmente aceitos como risco.
- A rastreabilidade ligar requisitos, casos e execuções reais.
- A documentação de QA refletir o código e o pipeline vigentes.
- Testes instáveis forem identificados, rastreados e tratados sem mascaramento.
- Os relatórios e indicadores forem reproduzíveis e tiverem responsáveis definidos.

## 17. Recomendação final

Recomenda-se aprovar a evolução em etapas, começando pela **Fase 0 — Baseline e saneamento documental** e pela definição das regras de conformidade LKDF.

A arquitetura atual de automação não deve ser substituída. O investimento deve se concentrar em protegê-la, ampliar a evidência de autorização e isolamento, tornar a cobertura visível e melhorar a confiabilidade dos resultados.

O principal resultado esperado não é simplesmente ter mais testes. É fazer com que cada mudança relevante na plataforma tenha uma relação clara com seus requisitos, riscos, verificações e evidências, mantendo a arquitetura de automação sustentável à medida que o AQuA-QE cresce.
