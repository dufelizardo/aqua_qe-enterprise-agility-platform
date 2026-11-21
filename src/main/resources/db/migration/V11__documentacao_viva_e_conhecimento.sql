-- Motor de documentação viva com dono genérico (SAD 7.6; SSD 3.7, 6 e 8.4) e páginas de conhecimento por unidade
-- (Proposta 07, Fase A). Um só mecanismo de seções, blocos e versões para os documentos dos artefatos e para o Wiki.

-- Templates (SSD 8.4, "a validar"). As seções são [{"key", "title", "required"}]. "governed" indica ciclo com
-- aprovação; "approver_role" é o papel que aprova. org_unit_id fica para a personalização por unidade (MDL-05).
create table configuration.doc_template (
    id             uuid primary key default gen_random_uuid(),
    document_type  varchar(20)  not null,
    version_number int          not null default 1,
    name           varchar(120) not null,
    sections       jsonb        not null,
    approver_role  varchar(40),
    governed       boolean      not null default true,
    org_unit_id    uuid references organization.org_unit (id),
    created_at     timestamptz  not null default now()
);
create unique index doc_template_type_unit_version on configuration.doc_template
    (document_type, coalesce(org_unit_id, '00000000-0000-0000-0000-000000000000'::uuid), version_number);

insert into configuration.doc_template (document_type, name, sections, approver_role, governed) values
    ('PRD', 'PRD', '[
        {"key": "context", "title": "Contexto", "required": true},
        {"key": "objectives", "title": "Objetivos", "required": true},
        {"key": "scope", "title": "Escopo", "required": true},
        {"key": "requirements", "title": "Requisitos", "required": true},
        {"key": "metrics", "title": "Métricas", "required": false},
        {"key": "risks", "title": "Riscos", "required": false}]', 'PRODUCT_MANAGER', true),
    ('SAD', 'SAD', '[
        {"key": "overview", "title": "Visão geral da solução", "required": true},
        {"key": "decisions", "title": "Decisões arquiteturais", "required": true},
        {"key": "diagrams", "title": "Diagramas", "required": false},
        {"key": "security", "title": "Segurança", "required": true},
        {"key": "dependencies", "title": "Dependências", "required": false}]', 'TECH_LEAD', true),
    ('SSD', 'SSD', '[
        {"key": "api-contracts", "title": "Contratos de API", "required": true},
        {"key": "data-model", "title": "Modelo de dados", "required": false},
        {"key": "payloads", "title": "Payloads", "required": false},
        {"key": "error-handling", "title": "Tratamento de erros", "required": false}]', 'TECH_LEAD', true),
    ('UX', 'UX/UI', '[
        {"key": "flows", "title": "Fluxos", "required": true},
        {"key": "prototypes", "title": "Protótipos", "required": false},
        {"key": "accessibility", "title": "Acessibilidade", "required": false}]', 'UX_DESIGNER', true),
    ('QA', 'Planejamento de testes', '[
        {"key": "strategy", "title": "Estratégia", "required": true},
        {"key": "scope", "title": "Escopo", "required": true},
        {"key": "scenarios", "title": "Cenários", "required": false},
        {"key": "test-data", "title": "Massa de dados", "required": false},
        {"key": "automation", "title": "Automação", "required": false},
        {"key": "exit-criteria", "title": "Critérios de saída", "required": true}]', 'QA_ENGINEER', true),
    -- Página livre do Wiki: uma seção, publicada ao salvar (KNOW-D05).
    ('PAGE', 'Página', '[
        {"key": "content", "title": "Conteúdo", "required": false}]', null, false),
    -- Regimento (KNOW-D03): seções da Proposta 07, seção 6; obrigatórias e aprovador "a validar".
    ('REGIMENTO', 'Regimento', '[
        {"key": "identificacao", "title": "Identificação", "required": true},
        {"key": "proposito", "title": "Propósito", "required": true},
        {"key": "missao-objetivos", "title": "Missão e objetivos", "required": false},
        {"key": "escopo", "title": "Escopo de atuação", "required": true},
        {"key": "responsabilidades", "title": "Responsabilidades", "required": true},
        {"key": "estrutura", "title": "Estrutura organizacional", "required": false},
        {"key": "governanca", "title": "Governança", "required": true},
        {"key": "papeis", "title": "Papéis e responsabilidades", "required": false},
        {"key": "priorizacao", "title": "Critérios de priorização", "required": false},
        {"key": "investimentos", "title": "Gestão de investimentos", "required": false},
        {"key": "riscos", "title": "Gestão de riscos", "required": false},
        {"key": "indicadores", "title": "Indicadores", "required": false},
        {"key": "ritos", "title": "Ritos de governança", "required": false},
        {"key": "relacionamentos", "title": "Relacionamentos organizacionais", "required": false},
        {"key": "controle-alteracoes", "title": "Controle de alterações", "required": false},
        {"key": "aprovacao", "title": "Aprovação", "required": false}]', 'BUSINESS_OWNER', true);

-- Documento: dono é um artefato (os cinco documentos da Iniciativa e da Feature) ou uma unidade (página do Wiki).
create table livingdocs.document (
    id                uuid primary key default gen_random_uuid(),
    owner_type        varchar(8)   not null check (owner_type in ('ARTIFACT', 'ORG_UNIT')),
    artifact_id       uuid references workitem.artifact (id),
    org_unit_id       uuid references organization.org_unit (id),
    type              varchar(20)  not null,
    title             varchar(200),
    parent_id         uuid references livingdocs.document (id),
    position          double precision not null default (extract(epoch from clock_timestamp()) * 1000),
    status            varchar(12)  not null check (status in
                          ('PENDING', 'IN_PROGRESS', 'APPROVED', 'DRAFT', 'IN_REVIEW', 'PUBLISHED')),
    template_id       uuid         not null references configuration.doc_template (id),
    current_version   int          not null default 0,
    approved_version  int,
    published_version int,
    classification    varchar(12)  not null default 'INTERNAL'
                          check (classification in ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')),
    archived_at       timestamptz,
    archive_reason    varchar(500),
    created_by        uuid,
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now(),
    check ((owner_type = 'ARTIFACT' and artifact_id is not null and org_unit_id is null)
        or (owner_type = 'ORG_UNIT' and org_unit_id is not null and artifact_id is null and title is not null))
);
create unique index document_artifact_type on livingdocs.document (artifact_id, type) where owner_type = 'ARTIFACT';
create index document_org_unit_idx on livingdocs.document (org_unit_id) where owner_type = 'ORG_UNIT';
create index document_parent_idx on livingdocs.document (parent_id);

-- Versões imutáveis (PRD-RF-020; SAD 7.6): cada gravação cria uma; nunca são alteradas nem apagadas.
create table livingdocs.document_version (
    document_id uuid         not null references livingdocs.document (id),
    number      int          not null,
    sections    jsonb        not null,
    summary     varchar(300),
    author_id   uuid,
    saved_at    timestamptz  not null default now(),
    primary key (document_id, number)
);

create function livingdocs.version_is_immutable() returns trigger language plpgsql as $$
begin
    raise exception 'Versões de documento são imutáveis.';
end;
$$;
create trigger document_version_immutable before update or delete on livingdocs.document_version
    for each row execute function livingdocs.version_is_immutable();
