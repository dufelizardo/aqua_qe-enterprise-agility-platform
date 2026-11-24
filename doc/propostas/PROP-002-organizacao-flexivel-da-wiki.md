# PROP-002 — Organização Flexível da Wiki

| Campo | Valor |
|---|---|
| Produto | AQuA-QE Enterprise Agility Platform |
| Tipo | Proposta de evolução funcional e técnica |
| Versão | 1.0 |
| Data | 2026-11-21 |
| Status | **Aprovada em 2026-11-24 (alternativa A) e implementada.** Ver [Situação](#situação-2026-11-24). Pastas sem conteúdo ficam para depois. |
| Documento relacionado | [Proposta 07 — Conhecimento organizacional, Wiki e documentação viva](../KNOWLEDGE-PROPOSAL.md) |
| Proposta complementar | [PROP-001 — Evolução da Gestão do Conhecimento e Documentação Organizacional](PROP-001-gestao-do-conhecimento-e-documentacao-organizacional.md) |
| Documentos de referência | [PRD](../PRD.md), [SAD](../SAD.md), [SSD](../SSD.md), [UX/UI](../UX-UI.md), [Proposta 06 — Navegação](../NAVIGATION-PROPOSAL.md) |

> **Importante:** este documento apresenta uma proposta de evolução. Não altera por si só os requisitos KNOW da Proposta 07, os contratos do SSD, a arquitetura do SAD, o escopo do PRD ou o comportamento implementado. As recomendações dependem de decisão de produto, validação técnica e posterior incorporação nos documentos donos.

## Situação (2026-11-24)

Decisão do usuário: **mover e reordenar, alternativa A**. Páginas-pai funcionam como agrupador; sem pastas vazias por enquanto. Registrado na [Proposta 07, seção 10](../KNOWLEDGE-PROPOSAL.md#10-revisão-prop-002-organização-flexível-da-wiki).

| Item | Situação |
|---|---|
| Reordenar, mover para outro nível, promover, rebaixar, mover para a raiz | **Implementado**: `POST /pages/{id}/rank` e o menu ⋯ de cada página na árvore. |
| Movimentação de subárvore, sem ciclo | **Implementado.** |
| Identidade, conteúdo, versões, status e links preservados | **Implementado**: organizar não cria versão; o link é pelo id. |
| Auditoria da movimentação | **Implementado**: `knowledge_page.moved`, com pai e posição antes e depois. |
| Arrastar e soltar, com alternativa por teclado | **Implementado**: arrastar põe a página onde foi solta, inclusive em outro nível; o menu ⋯ faz tudo pelo teclado. |
| Ordem manual compartilhada pela unidade | **Implementado.** |
| Agrupadores (pastas sem conteúdo) | Não implementado, conforme a decisão (alternativa B adiada). |
| Ordenações alternativas (alfabética, por data) | Não implementado. |

**Decisões da seção 14**
1. Agrupadores: páginas-pai bastam na primeira evolução.
2. Ordem manual compartilhada: sim.
3. Ordenações alternativas: fora desta etapa.
4. Permissão: quem edita a página (`EDIT_KNOWLEDGE`).
5. Profundidade: sem limite.
6. Exclusão de agrupador: não se aplica, sem pastas.
7. Navegação global: a árvore do Wiki fica só na tela do Wiki.

---

## 1. Objetivo

Propor uma evolução da organização interna da Wiki para que usuários possam estruturar e reorganizar o conhecimento de acordo com as necessidades de cada unidade organizacional.

A Wiki não deve ficar limitada a uma árvore rígida, determinada pela ordem de criação, por ordenação alfabética ou por uma relação fixa entre páginas. A estrutura deve poder evoluir sem exigir recriação de documentos e sem comprometer conteúdo, identidade, histórico, autorização ou referências.

A proposta trata de:

- Hierarquia de páginas e subpáginas.
- Ordenação manual dos itens.
- Movimentação e reorganização da árvore.
- Agrupadores opcionais, apresentados visualmente como pastas.
- Separação entre estrutura de navegação e propriedade do documento.
- Integridade de links, permissões, histórico e auditoria durante as movimentações.

## 2. Contexto e problema

A Proposta 07 estabelece um Wiki por unidade organizacional, com páginas e subpáginas. A implementação descrita nela já contempla criação, renomeação, movimentação sem ciclo, arquivamento e restauração de páginas.

A necessidade identificada agora é mais ampla do que simplesmente mover uma página para outra página-pai. A organização precisa poder definir a ordem e a composição da árvore de navegação de forma natural, por exemplo:

- Colocar uma página antes ou depois de outra.
- Transformar uma página principal em subpágina.
- Promover uma subpágina para a raiz.
- Mover uma página e toda a sua descendência para outro ponto da árvore.
- Criar agrupamentos temáticos para organizar conjuntos maiores de documentos.
- Alterar a organização posteriormente sem recriar os documentos.

A árvore deve representar a maneira como a unidade deseja navegar pelo conhecimento, e não impor uma taxonomia universal.

## 3. Princípio de produto

**A Wiki deve ser uma estrutura hierárquica flexível de conhecimento, com organização e ordenação controladas pela unidade, sem que reorganizar a navegação altere a identidade ou o conteúdo dos documentos.**

A estrutura deve permitir crescimento gradual. Uma unidade pode começar com poucas páginas na raiz e, conforme o volume aumenta, criar agrupamentos e níveis adicionais.

Não deve ser obrigatório criar pastas antes de criar páginas, nem deve existir uma profundidade fixa definida artificialmente pelo produto. Limites técnicos de profundidade, caso necessários, deverão ser justificados e documentados.

## 4. Modelo conceitual proposto

### 4.1. Itens da árvore

A árvore da Wiki poderá conter dois tipos conceituais de item:

| Tipo | Descrição | Possui conteúdo documental? |
|---|---|---|
| **Página** | Documento com título, conteúdo, metadados, histórico e ciclo de vida aplicável. | Sim |
| **Agrupador** | Elemento de navegação usado para reunir páginas e outros agrupadores por assunto ou finalidade. Pode ser apresentado como pasta. | Não, por padrão |

Exemplo:

```text
Wiki da Engenharia
│
├── 📁 Engenharia
│   ├── 📄 Arquitetura
│   ├── 📄 Padrões de desenvolvimento
│   └── 📁 Qualidade
│       ├── 📄 Estratégia de testes
│       └── 📄 Automação
│
├── 📁 Gestão
│   ├── 📄 Indicadores
│   └── 📄 Processos
│
└── 📄 Apresentação da unidade
```

Os ícones são ilustrativos. A interface deve deixar claro o que é uma página navegável e o que é somente um agrupador.

### 4.2. Agrupadores são opcionais

O usuário poderá organizar a Wiki diretamente com páginas e subpáginas, sem criar agrupadores.

Exemplo sem agrupadores:

```text
Wiki
├── Arquitetura
│   ├── Padrões
│   └── Decisões
└── Qualidade
    ├── Estratégia de testes
    └── Automação
```

Exemplo com agrupadores:

```text
Wiki
└── Engenharia
    ├── Arquitetura
    │   ├── Padrões
    │   └── Decisões
    └── Qualidade
        ├── Estratégia de testes
        └── Automação
```

O produto não deve obrigar o usuário a adotar uma estrutura baseada em pastas.

### 4.3. Agrupador não é diretório físico

O termo pasta pode ser utilizado na interface por ser familiar, mas o conceito é lógico e pertence à navegação da Wiki.

Não se propõe criar diretórios físicos, armazenar arquivos em caminhos de sistema operacional ou transformar a Wiki em um gerenciador de arquivos.

O agrupador não deve ser confundido com:

- Unidade organizacional.
- Espaço de conhecimento independente.
- Documento ou página com conteúdo.
- Permissão ou fronteira de segurança.
- Categoria global obrigatória.

## 5. Operações de organização

A interface deverá avaliar o suporte às operações abaixo:

| Operação | Comportamento esperado |
|---|---|
| Reordenar | Alterar a posição de um item entre seus irmãos, para cima ou para baixo. |
| Mover para outro nível | Alterar o item-pai, preservando o item e seus descendentes. |
| Promover | Remover o pai e posicionar o item no nível imediatamente superior, inclusive na raiz. |
| Rebaixar | Tornar o item filho de outro item elegível. |
| Mover para a raiz | Remover a relação de pai, mantendo o documento na mesma Wiki/unidade. |
| Criar agrupador | Criar um novo elemento de organização sem conteúdo documental. |
| Renomear agrupador | Alterar o rótulo de navegação sem alterar documentos contidos nele. |
| Excluir agrupador | Remover o agrupador após tratar explicitamente seus filhos; nunca excluir documentos implicitamente. |
| Expandir/recolher | Controlar a visualização dos descendentes sem alterar a estrutura persistida. |

A operação de mover deve oferecer uma alternativa acessível ao arrastar e soltar, como uma ação **Mover para...** com seleção do destino.

### 5.1. Arrastar e soltar

O arrastar e soltar é recomendado como interação rápida para:

- Reordenar itens no mesmo nível.
- Mover itens para outro agrupador ou página-pai.
- Promover ou rebaixar itens, quando a interface tornar o destino inequívoco.

A interface deve indicar visualmente o destino antes de confirmar a operação. Não deve permitir soltar um item em um destino inválido.

Arrastar e soltar não pode ser o único mecanismo disponível. Todas as operações principais devem possuir alternativa por teclado e controles acessíveis.

### 5.2. Ordenação

A ordem manual deve ser a forma padrão de organização da árvore.

Como opções adicionais de visualização, podem ser avaliadas:

- Ordem alfabética.
- Ordem por data de atualização.
- Ordem por data de criação.

É importante distinguir **ordem persistida pela unidade** de **ordem temporária escolhida pelo usuário para visualizar a lista**. Uma ordenação pessoal/temporária não deve sobrescrever silenciosamente a ordem oficial da Wiki.

A unidade deve conseguir retornar à ordenação manual depois de utilizar uma ordenação alternativa.

## 6. Regras de negócio propostas

As regras abaixo são propostas e dependem de aprovação.

| ID | Regra | Classificação |
|---|---|---|
| WIKI-ORG-RN-001 | Cada Wiki continua pertencendo a uma única unidade organizacional, conforme a Proposta 07. | Preservar regra existente |
| WIKI-ORG-RN-002 | A estrutura interna da Wiki é independente da hierarquia organizacional. | Preservar regra existente |
| WIKI-ORG-RN-003 | Uma página pode ser movida e reordenada sem recriação do documento. | Evolução proposta |
| WIKI-ORG-RN-004 | A movimentação preserva identificador, conteúdo, versões e histórico da página. | Evolução proposta |
| WIKI-ORG-RN-005 | A movimentação de uma página com descendentes preserva toda a subárvore, salvo quando o usuário executar operações explícitas nos descendentes. | Evolução proposta |
| WIKI-ORG-RN-006 | A estrutura não pode conter ciclos; um item não pode ser movido para dentro de si mesmo ou de um descendente. | Regra de integridade a preservar |
| WIKI-ORG-RN-007 | A ordem manual deve ser persistida e compartilhada pelos usuários que consultam a mesma Wiki, respeitando autorização. | Evolução proposta |
| WIKI-ORG-RN-008 | Agrupadores são opcionais e não precisam conter páginas para existir. | Evolução proposta |
| WIKI-ORG-RN-009 | Agrupadores não são documentos e não recebem conteúdo editorial ou versão documental por padrão. | Evolução proposta |
| WIKI-ORG-RN-010 | Excluir um agrupador não pode excluir automaticamente suas páginas ou subagrupadores. | Evolução proposta |
| WIKI-ORG-RN-011 | A exclusão de agrupador deverá exigir uma política explícita para os filhos: mover para o pai, mover para a raiz ou cancelar. | Evolução proposta |
| WIKI-ORG-RN-012 | Reorganizar a árvore não deve alterar o estado de publicação nem iniciar novo ciclo de aprovação do conteúdo. | Evolução proposta |
| WIKI-ORG-RN-013 | A movimentação e a reordenação devem ser auditáveis, incluindo origem, destino, autor e data. | Evolução proposta |
| WIKI-ORG-RN-014 | Links internos devem continuar resolvendo a mesma página após movimentações. | Evolução proposta |
| WIKI-ORG-RN-015 | A autorização de leitura e edição deve continuar sendo validada pelos mecanismos existentes; a posição na árvore não concede permissão. | Preservar regra arquitetural |
| WIKI-ORG-RN-016 | A estrutura da Wiki não pode ser utilizada para criar, simular ou substituir unidades organizacionais. | Preservar regra existente |
| WIKI-ORG-RN-017 | A interface deve oferecer alternativa acessível às operações por arrastar e soltar. | Evolução proposta |
| WIKI-ORG-RN-018 | Ordenações temporárias de visualização não devem sobrescrever a ordenação manual sem ação explícita. | Evolução proposta |

## 7. Identidade, propriedade e navegação

É necessário separar três conceitos:

1. **Propriedade:** unidade organizacional responsável pelo documento.
2. **Localização na árvore:** posição em que o item aparece na navegação da Wiki.
3. **Autorização:** operações que cada usuário pode executar sobre o conteúdo e a estrutura.

Mover uma página dentro da Wiki não transfere sua propriedade para outra unidade. A estrutura de navegação não deve ser interpretada como mecanismo de compartilhamento ou autorização.

Nesta proposta, mover páginas entre Wikis de unidades diferentes não está incluído. Esse cenário depende das regras de propriedade, aplicabilidade, referências e compartilhamento tratadas na Proposta 07 e na PROP-001.

## 8. Integridade, histórico e referências

### 8.1. Identidade estável

A movimentação deve alterar somente os dados de organização e posição. O identificador estável do documento deve permanecer o mesmo.

Não se deve implementar uma movimentação como sequência de exclusão e recriação.

### 8.2. Histórico documental

Reordenar, mover, promover ou rebaixar uma página não altera seu conteúdo. Portanto, essas operações não devem gerar uma nova versão de conteúdo documental.

O evento de organização deve ser registrado na auditoria, separado do evento de edição de conteúdo.

### 8.3. Links e referências

Links internos devem preferencialmente referenciar a identidade estável da página, e não depender exclusivamente de um caminho textual da árvore.

Se a interface exibir caminhos de navegação (breadcrumbs), eles devem ser recalculados após a movimentação. Links antigos devem continuar resolvendo a página de destino.

A solução técnica deverá ser compatível com o mecanismo de referências documentais previsto na Proposta 07 e na PROP-001, sem antecipar sua implementação.

### 8.4. Concorrência

Duas pessoas podem reorganizar a mesma árvore simultaneamente. A solução deverá evitar perda silenciosa de atualizações, utilizando controle de concorrência compatível com os mecanismos existentes.

Conflitos de ordenação ou movimentação devem produzir resultado previsível e permitir que a interface atualize a árvore.

## 9. Diretrizes técnicas para investigação

A Proposta 07 descreve páginas com relação pai e posição no motor livingdocs. A inclusão de agrupadores sem conteúdo pode exigir evolução do modelo de navegação. Essa necessidade deve ser investigada antes de qualquer implementação.

Alternativas a avaliar:

| Alternativa | Descrição | Considerações |
|---|---|---|
| A. Apenas páginas hierárquicas | Manter a árvore composta somente por páginas e permitir que páginas-pai funcionem como agrupamento e documento ao mesmo tempo. | Menor mudança técnica; não oferece pasta sem conteúdo e pode obrigar a criar páginas artificiais. |
| B. Nó de navegação tipado | Representar itens da árvore como nós tipados, por exemplo, página ou agrupador, mantendo o conteúdo documental nas páginas. | Atende melhor à distinção conceitual, mas exige avaliar persistência, APIs, migração e autorização. |
| C. Taxonomia/categorias independentes | Organizar páginas por categorias sem uma árvore de navegação única. | Pode ajudar em descoberta, mas não substitui a necessidade de ordenar e aninhar itens; pode gerar múltiplas formas de navegação. |

**Recomendação preliminar:** investigar a alternativa B, mantendo a alternativa A como opção de menor complexidade caso a necessidade de agrupadores vazios não justifique a mudança de modelo.

Não se recomenda decidir previamente por uma nova tabela, serviço ou módulo. A solução deve reutilizar livingdocs e respeitar as responsabilidades de organization, access, configuration e audit. O desenho de persistência e API pertence ao SAD/SSD após validação.

### 9.1. Aspectos técnicos a especificar

Antes da implementação, definir:

- Identidade e tipos dos nós da árvore.
- Relação pai-filho e validação contra ciclos.
- Estratégia de ordenação e reordenação concorrente.
- Comportamento de movimentação de subárvores.
- Exclusão e esvaziamento de agrupadores.
- Contratos de API para criar, mover e reordenar.
- Eventos de auditoria.
- Compatibilidade com páginas existentes e dados atuais.
- Estratégia de migração, se necessária.
- Atualização de breadcrumbs, links e cache.
- Autorização das operações no backend.
- Comportamento da árvore em telas menores e com grande quantidade de itens.

## 10. Experiência de usuário proposta

A árvore deve permitir que o usuário compreenda e controle a organização sem exigir conhecimento técnico.

Recomenda-se avaliar:

- Menu contextual por item com ações de mover, renomear e criar filho.
- Ação para criar página na raiz ou dentro do item selecionado.
- Ação para criar agrupador.
- Indicação visual distinta de página e agrupador.
- Expandir e recolher níveis.
- Arrastar e soltar com indicação do destino.
- Comando explícito **Mover para...**.
- Reordenação por comandos acessíveis.
- Breadcrumbs na página aberta.
- Confirmação para operações destrutivas ou com impacto em vários itens.
- Mensagens claras quando uma operação não for permitida.

A árvore deve continuar utilizável quando houver muitos documentos e níveis. A experiência não deve depender exclusivamente de ícones ou de gestos de mouse.

## 11. Escopo e não objetivos

### 11.1. Dentro da proposta

- Reorganização da hierarquia da Wiki.
- Ordenação manual de páginas e agrupadores.
- Promoção e rebaixamento de páginas.
- Movimentação de subárvores.
- Agrupadores opcionais.
- Preservação de identidade, conteúdo, histórico, links e autorização.

### 11.2. Fora da proposta

- Alterar a hierarquia organizacional da empresa.
- Mover documentos entre unidades organizacionais.
- Criar compartilhamento ou aplicabilidade por meio da árvore.
- Criar um gerenciador de arquivos ou diretórios físicos.
- Criar um segundo motor documental.
- Alterar a independência entre Wiki e Regimento.
- Alterar o editor de texto contínuo.
- Definir a estrutura interna do Regimento.
- Criar categorias globais obrigatórias ou taxonomia corporativa universal.
- Atualizar automaticamente PRD, SAD, SSD ou UX/UI sem decisão e especificação correspondentes.

## 12. Critérios de aceite preliminares

Os critérios abaixo servem para orientar a futura especificação; não representam testes já implementados.

1. Deve ser possível alterar a ordem de páginas irmãs e persistir a nova ordem.
2. Deve ser possível mover uma página para outro pai da mesma Wiki.
3. Deve ser possível promover uma subpágina para a raiz.
4. Deve ser possível rebaixar uma página para filha de outro item elegível.
5. A movimentação deve preservar a identidade, o conteúdo e o histórico.
6. Ao mover uma página com descendentes, toda a subárvore deve ser preservada.
7. O sistema deve impedir ciclos e destinos inválidos.
8. A árvore deve refletir a estrutura atualizada para os usuários autorizados.
9. Links internos existentes devem continuar apontando para a mesma página.
10. A operação deve ser auditada.
11. A posição na árvore não deve conceder permissão de leitura ou edição.
12. Se agrupadores forem aprovados, deve ser possível criá-los sem criar uma página documental.
13. Excluir um agrupador com filhos deve exigir decisão explícita sobre o destino dos filhos.
14. Deve existir alternativa acessível ao arrastar e soltar.
15. Ordenação temporária não deve sobrescrever a ordenação manual sem confirmação explícita.

## 13. Relação com os documentos existentes

| Documento | Tratamento recomendado |
|---|---|
| **Proposta 07 — KNOWLEDGE-PROPOSAL.md** | Continua sendo a referência funcional principal. Após aprovação, incorporar requisitos de ordenação, movimentação e, se aprovada, agrupadores. Atualizar faseamento e status sem marcar como implementado antecipadamente. |
| **PROP-001** | Continua sendo a proposta guarda-chuva de evolução do conhecimento. Esta PROP-002 a complementa com foco na organização da Wiki. |
| **Proposta 06 — NAVIGATION-PROPOSAL.md** | Avaliar impactos na navegação contextual e na árvore exibida no menu da unidade. Não confundir a árvore interna da Wiki com a navegação global da plataforma. |
| **PRD** | Atualizar somente se a capacidade for aprovada como escopo e prioridade do produto. |
| **SAD** | Atualizar após a decisão sobre o modelo de nós e responsabilidades arquiteturais. |
| **SSD** | Especificar contratos, persistência, ordenação, movimentação, eventos e validações após decisão. |
| **UX/UI** | Definir árvore, interações, acessibilidade, estados e comportamentos responsivos. |
| **Testes/QA** | Criar cenários de aceitação, autorização, concorrência, regressão e migração quando houver especificação aprovada. |

## 14. Decisões pendentes

Antes de converter esta proposta em requisito aprovado, decidir:

1. **Agrupadores:** a Wiki deve permitir pastas sem conteúdo ou páginas-pai são suficientes para a primeira evolução?
2. **Ordenação:** a ordem manual será compartilhada por todos os usuários da unidade? A recomendação é sim.
3. **Ordenações alternativas:** serão apenas filtros temporários de visualização ou configurações persistentes por usuário?
4. **Permissão de organização:** quais papéis podem mover, reordenar, criar e excluir agrupadores?
5. **Profundidade:** haverá limite técnico? Se houver, qual justificativa e comportamento?
6. **Exclusão de agrupadores:** quais opções serão oferecidas para tratar os filhos?
7. **Navegação global:** a árvore completa da Wiki será exibida no menu contextual da unidade ou apenas em sua tela própria?

## 15. Recomendação final

Recomenda-se aprovar como objetivo de produto a **reorganização flexível da árvore da Wiki**, incluindo ordenação manual, movimentação, promoção e rebaixamento de páginas, preservação de identidade e histórico.

Recomenda-se também investigar agrupadores opcionais (pastas lógicas) como evolução complementar. A decisão de incluí-los deve considerar o benefício de organizar grandes volumes de conteúdo versus o custo de introduzir um novo tipo de nó na estrutura.

A primeira evolução não deve exigir que toda unidade utilize pastas. A organização deve continuar simples para Wikis pequenas e oferecer recursos adicionais quando a documentação crescer.

**Princípio orientador:** a Wiki deve se adaptar à organização do conhecimento da unidade — e não obrigar a unidade a adaptar seu conhecimento a uma árvore rígida do sistema.
