-- Anexos dos artefatos (SSD 3.7 e 9; #13, REV-08). O conteúdo fica atrás da interface de armazenamento: no MVP, na
-- tabela attachment_blob (storage_key); trocar pelo armazenamento de objetos (SAD) não muda a API. Remover só marca
-- removed_at, para a trilha de auditoria.
create table livingdocs.attachment (
    id           uuid primary key,
    artifact_id  uuid         not null references workitem.artifact (id),
    file_name    varchar(255) not null,
    content_type varchar(150) not null,
    size_bytes   bigint       not null check (size_bytes > 0),
    storage_key  varchar(100) not null unique,
    uploaded_by  uuid         not null,
    uploaded_at  timestamptz  not null default now(),
    removed_at   timestamptz,
    removed_by   uuid
);

create index attachment_artifact_idx on livingdocs.attachment (artifact_id, uploaded_at) where removed_at is null;

create table livingdocs.attachment_blob (
    storage_key varchar(100) primary key,
    content     bytea       not null,
    created_at  timestamptz not null default now()
);
