# Baseline de segurança

| | |
|---|---|
| **Origem** | [REV-13](REVIEW-01.md#rev-13--baseline-de-segurança-por-framework), issue #18, decidido em 2026-11-28 |
| **Fontes dos requisitos** | [Especificação de Segurança, Confiabilidade e Governança de Dados V2.0](pdr/) (RF-SC01 a RF-SC18, RNF-SC01 a RNF-SC08); [SAD, seções 7 e 11](SAD.md#7-mecanismos-transversais) |
| **Situação** | Mapeamento preliminar. O perfil CSF da operação SaaS dedicada fica para antes do lançamento SaaS. |

Este documento diz **quais referências de segurança o projeto usa**, **como os requisitos da especificação se ligam a elas**, **o que já está coberto e testado** e **o que falta**. Ele não declara conformidade: em textos externos, usar "mapeado ao NIST CSF 2.0" ou "verificado contra o OWASP ASVS nível 2", nunca "conforme" ou "certificado" ([PRD, DEC-05](PRD.md#12-decisões-de-consolidação)).

## 1. Referências adotadas

| Referência | Papel no projeto | Onde aparece |
|---|---|---|
| **OWASP ASVS 4.0.3, nível 2** | Requisitos de segurança da aplicação, verificáveis. É a régua dos testes. | Seção 3; tags `ASVS-Vn` nos testes Robot ([QA](QA.md)) |
| **NIST SSDF (SP 800-218)** | Processo de desenvolvimento e entrega: SBOM, varredura de dependências e de imagens, assinatura. | Seção 4; workflow `supply-chain.yml` |
| **NIST CSF 2.0** | Mapa guarda-chuva, para responder a avaliações de clientes. Não é fonte de requisitos do produto: descreve resultados da organização, não da aplicação. | Seção 2 |

- **ASVS 4.0.3:** é a numeração já usada nas tags dos testes (`ASVS-V3` sessão, `ASVS-V4` controle de acesso). A migração para o ASVS 5.0, que renumera os capítulos, fica registrada na seção 6.
- **Quem opera:** nas instalações on-premise e air-gapped, quem opera é o cliente. O papel do produto é fornecer as capacidades que ajudam o cliente a atingir os resultados do CSF dele (por exemplo, a verificação da trilha de auditoria). Na operação SaaS dedicada (ADR-13), o fornecedor responde também pelos resultados da organização.
- **Outras exigências:** a LGPD é obrigatória por lei ([REV-14](REVIEW-01.md#rev-14--privacidade-e-dados-pessoais), #32). Pedidos de ISO/IEC 27001 ou da Resolução CMN 4.893, comuns no setor financeiro, são respondidos a partir do mapa da seção 2.

## 2. Mapa dos requisitos

Cada requisito da especificação aparece com o capítulo do ASVS que o verifica, as subcategorias do CSF 2.0 a que ele contribui e a situação no MVP. Os itens agrupados na especificação (como "RF-SC07 & RF-SC08") ficam agrupados aqui também.

| Requisito | ASVS 4.0.3 | CSF 2.0 | Situação no MVP |
|---|---|---|---|
| RF-SC01 a 03: controle por perfil e unidade, imutabilidade dos logs, proteção dos dados | V4, V7 | PR.AA-05, PR.PS-04, PR.DS-01 | **Coberto.** Autorização por escopo num ponto único ([SAD 7.2](SAD.md#72-autorização); [SSD 2.1](SSD.md#21-escopo-entre-unidades-rev-01-rev-02-rev-04)); trilha append-only com cadeia de hash ([SAD 7.5](SAD.md#75-trilha-de-auditoria)). |
| RF-SC04: SSO corporativo | V2 | PR.AA-01, PR.AA-03 | **Coberto pelo IdP.** Keycloak empacotado, a aplicação só fala OIDC (ADR-08); login conduzido pelo backend (ADR-16). |
| RF-SC05: MFA configurável | V2 | PR.AA-03 | **Coberto pelo IdP.** Política de MFA do realm do Keycloak. Falta o realm de produção (#25). |
| RF-SC06: autorização híbrida (RBAC, ABAC, por recurso) | V4 | PR.AA-05 | **Parcial.** RBAC por escopo no MVP; ABAC e autorização por recurso na Fase 2, sem mudar os módulos de negócio ([SAD 7.2](SAD.md#72-autorização)). |
| RF-SC07 e 08: isolamento e compartilhamento entre portfólios | V4 | PR.AA-05, PR.DS-01 | **Parcial.** Regras de escopo entre unidades no MVP (SSD 2.1); níveis Private, Shared e Enterprise na Fase 2. |
| RF-SC09 e 10: auditoria protegida | V7 | PR.PS-04, DE.CM-03, DE.AE-03 | **Coberto, com pendências da #8.** Consulta restrita (`GET /audit-events`) e verificação da cadeia (`GET /audit-events/verification`). Pendentes: usuário de banco só com `INSERT` e `SELECT` e ancoragem externa do hash ([REV-03](REVIEW-01.md#rev-03--garantias-da-auditoria-assíncrona)). |
| RF-SC11 e 12: classificação e mascaramento | V8 | ID.AM-07, PR.DS-01 | **Parcial.** A classificação do documento é registrada (SSD 6.1); políticas por classificação e mascaramento na Fase 2. |
| RF-SC13: gestão de segredos | V6, V14 | PR.AA-01, PR.DS-01 | **Parcial.** Segredos de infraestrutura no mecanismo do cluster; nenhum segredo no repositório (o lint LKDF recusa segredo literal nos testes). Credenciais de integração, com rotação, na Fase 2 ([SAD 7.10](SAD.md#710-segredos-e-criptografia)). |
| RF-SC14: retenção e descarte | V8 | ID.AM-08 | **Parcial.** Inventário, retenção e anonimização decididos na #32 ([SSD 2.2](SSD.md#22-dados-pessoais-rev-14)); falta a retenção automática dos avisos e das contas inativas. |
| RF-SC15: disaster recovery | — | PR.DS-11, RC.RP-01 | **Parcial.** Meta provisória RPO 1 h e RTO 4 h ([SAD 11](SAD.md#11-atributos-de-qualidade)); falta o procedimento testado de restauração ([OPERACAO](OPERACAO.md)). |
| RF-SC16 e 18: monitoramento de eventos de segurança, expiração e revogação de tokens | V3, V7 | DE.CM-03, DE.AE-02, PR.AA-03 | **Parcial.** Sessão por cookie no backend e logout que encerra a sessão no IdP (testes `ASVS-V3`). Faltam os eventos de autenticação na trilha e a revogação em massa (lacunas S-03 e S-04). |
| RF-SC17: integridade referencial | V11 | PR.DS-01 | **Coberto.** Chaves estrangeiras no banco e validações no serviço; um schema por módulo (ADR-05). |
| RNF-SC01: confidencialidade | V4, V8, V9 | PR.AA-05, PR.DS-01, PR.DS-02 | **Parcial.** Ponto único de autorização e filtro de escopo em toda listagem e busca. TLS só no ambiente de produção, ainda adiado (#25). |
| RNF-SC02: integridade | V7 | PR.DS-01, DE.CM-09 | **Coberto.** Cadeia de hash e versões imutáveis dos documentos. |
| RNF-SC03: disponibilidade | — | PR.IR-03 | **Parcial.** Meta provisória de 99,5% mensal (SAD 11). |
| RNF-SC04: recuperabilidade | — | RC.RP-01, PR.DS-11 | Como RF-SC15. |
| RNF-SC05: criptografia | V6, V9 | PR.DS-01, PR.DS-02 | **Parcial.** TLS em trânsito e criptografia em repouso delegadas à infraestrutura (SAD 7.10); dependem do ambiente de produção (#25). |
| RNF-SC06: escalabilidade de autorização e auditoria | — | — | Fora do escopo de segurança; tratado nos atributos de qualidade (SAD 11). |
| RNF-SC07: observabilidade de segurança | V7 | DE.CM-01, DE.CM-03 | **Lacuna.** Os eventos de segurança e o destino deles não estão especificados (lacuna S-03). |
| RNF-SC08: auditabilidade | V7 | PR.PS-04 | **Coberto.** Todo evento de domínio vai para a trilha (SAD 7.4 e 7.5). |
| Cadeia de suprimentos (não está na especificação) | V14.2 | GV.SC-01 a 10, ID.RA-01, PR.PS-02 | **Parcial.** SBOM e varredura no CI desde a #18 (seção 4); falta a assinatura das imagens (lacuna S-01). |
| Resposta a incidentes (não está na especificação) | — | RS.MA-01, RS.AN-03, RS.MI-01 | **Lacuna** (lacuna S-04). |

## 3. Cobertura do ASVS nível 2

Situação por capítulo do ASVS 4.0.3 e onde está a evidência. "Coberto" vale para o escopo do MVP; o detalhe dos casos está no [QA](QA.md).

| Capítulo | Situação | Evidência e o que falta |
|---|---|---|
| V1 Arquitetura | Parcial | SAD e ADRs; autorização centralizada. Falta um modelo de ameaças por fluxo crítico (login, transição com gate, auditoria). |
| V2 Autenticação | Delegado | Keycloak (ADR-08). A política de senha e de MFA é do realm; é verificada na entrega de produção (#25). |
| V3 Sessão | Parcial | BFF com cookie de sessão, sem token no navegador (ADR-16); logout encerra a sessão no IdP. Testes com a tag `ASVS-V3` (login e logout na UI; 401 sem sessão na API). Faltam o tempo máximo de sessão definido e a revogação em massa. |
| V4 Controle de acesso | Coberto | Ponto único de autorização; 403 e 404 testados em cada recurso. Testes com a tag `ASVS-V4`. |
| V5 Validação e codificação | Parcial | Erros de validação padronizados (400 `VALIDATION_FAILED`); o editor só aceita os nós e marcas conhecidos, recusa `javascript:` em links e cores fora de `#rrggbb` (SSD 6.2); a exportação Markdown escapa o texto (SSD 6.4). |
| V6 Criptografia armazenada | Delegado | Banco e armazenamento de objetos (SAD 7.10). |
| V7 Erros e logs | Parcial | Problem Details sem detalhes internos; trilha de auditoria encadeada, sem senhas nem tokens. Faltam os eventos de autenticação e de segurança (S-03). |
| V8 Proteção de dados | Parcial | `Cache-Control: no-store` nos downloads de anexos e exportações. Classificação só registrada; retenção depende do REV-14. |
| V9 Comunicação | Lacuna nos ambientes atuais | Os ambientes dev usam HTTP em nomes `.local` (ADR-15 e 16). TLS é requisito da produção (#25). |
| V10 Código malicioso | Parcial | CodeQL (Java e TypeScript) a cada push; varredura de dependências com o Trivy (seção 4). |
| V11 Lógica de negócio | Parcial | Gates na mesma transação da transição; bloqueio otimista com versão. Faltam limites de taxa (S-05). |
| V12 Arquivos | Coberto | Anexos com limite de tamanho (413), nome saneado, `Content-Disposition: attachment` e `nosniff` (#13). |
| V13 API | Parcial | CSRF por cookie no BFF; Bearer validado a cada requisição para integrações. Faltam limites de taxa (S-05). |
| V14 Configuração | Parcial | Modo dev sem senha recusado em produção (ADR-17, `AuthModeSafetyCheck`); SBOM e varredura no CI. Faltam os cabeçalhos de segurança HTTP (S-02). |

## 4. Processo de entrega (SSDF)

| Prática SSDF | Como o projeto atende | Situação |
|---|---|---|
| PO.1: requisitos de segurança definidos | Este documento, a especificação de segurança e as tags ASVS dos testes. | Coberto |
| PS.1: proteger o código | Branch `main` protegida com checks obrigatórios ([OPERACAO](OPERACAO.md)); commits só por PR na promoção (ADR-15). | Coberto |
| PS.2: verificar a integridade da entrega | Imagens por SHA do commit no GitOps (ADR-15). Falta a assinatura. | Lacuna S-01 |
| PS.3: arquivar a entrega e o SBOM | SBOM CycloneDX do repositório e de cada imagem, gerado a cada push e guardado 90 dias como artefato do run. Falta publicar o SBOM junto da imagem. | Parcial (S-01) |
| PW.4: reusar componentes seguros | Versões fixadas (ADR-02); dependências varridas a cada push. | Coberto |
| PW.7: revisar e analisar o código | CodeQL a cada push e PR; revisão no PR de promoção. | Coberto |
| PW.8: testar | JUnit, Vitest e Robot, com casos de segurança marcados por capítulo do ASVS. | Coberto |
| RV.1: identificar vulnerabilidades | Trivy nas dependências (Maven, npm, pip) e nas duas imagens, a cada push e PR, com resultado no Code Scanning. | Coberto |
| RV.2: avaliar e corrigir | Política abaixo. | Coberto (prazos a validar) |

**Política de vulnerabilidades (decidida em 2026-11-28):**

- **CRITICAL com versão corrigida:** bloqueia o pipeline (`supply-chain.yml`) até a correção. Isso inclui a promoção para a `main`.
- **HIGH e CRITICAL sem correção:** não bloqueiam. Vão para o Code Scanning do GitHub, para triagem. Quem tria registra a decisão no alerta: corrigir, mitigar ou aceitar o risco, com justificativa.
- **Prazos de correção (a validar com a operação SaaS):** CRITICAL em 7 dias e HIGH em 30 dias depois que a correção existir.
- **Air-gapped:** a base de vulnerabilidades é baixada no CI. O cliente recebe a imagem e o SBOM da versão; a varredura no ambiente dele é responsabilidade de quem opera.
- **Situação em 2026-11-28 (Trivy 0.58.1):** nenhuma vulnerabilidade HIGH ou CRITICAL nas dependências do repositório. A imagem do frontend tinha uma CRITICAL e dezenas de HIGH nos pacotes do Alpine da imagem base, todas com correção. Por isso as duas imagens passaram a rodar `apk upgrade` no build; depois disso, nenhuma HIGH ou CRITICAL nas duas.

## 5. Lacunas

Cada lacuna é uma issue no Backlog. As que já tinham issue apontam para ela.

| Id | Lacuna | Referência | Issue |
|---|---|---|---|
| S-01 | Assinatura das imagens e SBOM publicado junto da imagem | SSDF PS.2 e PS.3; CSF GV.SC | #107 |
| S-02 | Cabeçalhos de segurança HTTP: CSP, `frame-ancestors`, `Referrer-Policy`, `X-Content-Type-Options` em todas as respostas e HSTS com TLS | ASVS V14.4 | #108 |
| S-03 | Eventos de autenticação e de segurança na trilha (login, falha de login, logout, troca de papel) e para onde vão | RF-SC16, RNF-SC07; CSF DE.CM-03; SSD 10 | #109 |
| S-04 | Capacidades de resposta a incidentes: revogar as sessões de um usuário ou de todos, e exportar a trilha de um período | CSF RS.MA-01, RS.MI-01 | #110 |
| S-05 | Limites de taxa da API, por usuário e por origem | ASVS V11.1 e V13 | #111 |
| — | Garantias da auditoria: usuário de banco só com `INSERT` e `SELECT`, ancoragem externa do hash | RF-SC09, RNF-SC02 | #8 (REV-03) |
| — | Privacidade, dados pessoais e retenção | RF-SC14; LGPD | resolvido na #32 (REV-14), menos a retenção automática |
| — | TLS, realm de produção e política de MFA | RNF-SC05, RF-SC05; ASVS V9 | #25 |

## 6. Pendências deste documento

- **Perfil CSF da operação SaaS dedicada:** antes do lançamento SaaS, com os resultados da organização (GV, RS e RC do lado do fornecedor).
- **Modelo de ameaças** dos fluxos críticos (ASVS V1.1).
- **Migração para o ASVS 5.0:** renumerar as tags dos testes quando o baseline for revisto.
- **Prazos da política de vulnerabilidades:** validar com quem vai operar o SaaS.
