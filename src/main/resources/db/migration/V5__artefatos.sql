-- Artefatos de todos os níveis (SSD, seções 3.4 e 9).

create table workitem.artifact (
    id                uuid primary key default gen_random_uuid(),
    key               varchar(20)  not null unique,
    type_id           uuid         not null references configuration.artifact_type (id),
    parent_id         uuid         references workitem.artifact (id),
    -- Cadeia de ancestrais materializada ("/raiz/.../este/"): leitura por hierarquia (SSD 2.1, regras 2 e 3).
    path              text         not null,
    org_unit_id       uuid         not null references organization.org_unit (id),
    status_id         uuid         not null references configuration.workflow_status (id),
    title             varchar(200) not null,
    description       text,
    owner_id          uuid         references identity.user_account (id),
    technical_lead_id uuid         references identity.user_account (id),
    qa_owner_id       uuid         references identity.user_account (id),
    assignee_id       uuid         references identity.user_account (id),
    size              varchar(2)   check (size in ('XS', 'S', 'M', 'L', 'XL')),
    custom_fields     jsonb        not null default '{}',
    rank              numeric,
    archived          boolean      not null default false,
    version           integer      not null default 0,
    created_by        uuid         references identity.user_account (id),
    created_at        timestamptz  not null default now(),
    updated_at        timestamptz  not null default now()
);
create index artifact_parent_idx on workitem.artifact (parent_id);
create index artifact_unit_type_status_idx on workitem.artifact (org_unit_id, type_id, status_id);
create index artifact_path_idx on workitem.artifact (path text_pattern_ops);
create index artifact_owner_idx on workitem.artifact (owner_id);
create index artifact_assignee_idx on workitem.artifact (assignee_id);

-- Número sequencial de cada prefixo de chave legível (ex.: FEAT-1024).
create table workitem.key_sequence (
    key_prefix varchar(10) primary key,
    next_value bigint      not null
);
