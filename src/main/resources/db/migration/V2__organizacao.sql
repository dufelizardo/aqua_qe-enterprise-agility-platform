-- Estrutura organizacional configurável (SAD, ADR-14; SSD, seções 3.2, 8.5 e 9).

create table organization.org_level (
    id           uuid primary key default gen_random_uuid(),
    key          varchar(40)  not null unique,
    name         varchar(80)  not null,
    position     integer      not null unique,
    -- Níveis sem Flight Level só agrupam; há no máximo um nível por Flight Level.
    flight_level varchar(3)   unique check (flight_level in ('FL1', 'FL2', 'FL3'))
);

create table organization.org_unit (
    id         uuid primary key default gen_random_uuid(),
    level_id   uuid         not null references organization.org_level (id),
    name       varchar(120) not null,
    parent_id  uuid         references organization.org_unit (id),
    -- Cadeia de ancestrais materializada ("/raiz/.../esta/"), para filtrar subárvores por prefixo.
    path       text         not null,
    active     boolean      not null default true,
    version    integer      not null default 0,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);
create index org_unit_path_idx on organization.org_unit (path text_pattern_ops);
create index org_unit_parent_idx on organization.org_unit (parent_id);
-- Uma única raiz por instalação.
create unique index org_unit_single_root on organization.org_unit ((true)) where parent_id is null;

create table organization.org_unit_link (
    unit_id        uuid        not null references organization.org_unit (id),
    serves_unit_id uuid        not null references organization.org_unit (id),
    created_by     uuid,
    created_at     timestamptz not null default now(),
    primary key (unit_id, serves_unit_id),
    check (unit_id <> serves_unit_id)
);
create index org_unit_link_serves_idx on organization.org_unit_link (serves_unit_id);

-- Instalação padrão (SSD, seção 8.5): cinco níveis e a unidade raiz.
insert into organization.org_level (key, name, position, flight_level) values
    ('ENTERPRISE',    'Empresa',            1, null),
    ('BUSINESS_UNIT', 'Unidade de Negócio', 2, null),
    ('PORTFOLIO',     'Portfólio',          3, 'FL3'),
    ('CADEIA',        'Cadeia',             4, 'FL2'),
    ('SQUAD',         'Squad',              5, 'FL1');

insert into organization.org_unit (id, level_id, name, parent_id, path)
select r.id, l.id, 'Empresa', null, '/' || r.id || '/'
from (select gen_random_uuid() as id) r
cross join organization.org_level l
where l.key = 'ENTERPRISE';
