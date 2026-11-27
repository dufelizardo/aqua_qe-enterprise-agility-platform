-- Campos customizados declarados por tipo de artefato (#17; REV-12; SSD 3.3 e 5.2). O gate só cita um campo declarado,
-- e a gravação do artefato só aceita campos declarados, com o tipo certo. Vale na organização inteira.

create table configuration.custom_field (
    id         uuid primary key default gen_random_uuid(),
    type_id    uuid         not null references configuration.artifact_type (id),
    key        varchar(40)  not null,
    label      varchar(80)  not null,
    field_type varchar(10)  not null check (field_type in ('TEXT', 'NUMBER', 'DATE', 'BOOLEAN', 'SELECT')),
    options    jsonb        not null default '[]'::jsonb,
    position   integer      not null,
    created_by uuid,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now(),
    unique (type_id, key)
);

create index custom_field_type on configuration.custom_field (type_id, position);
