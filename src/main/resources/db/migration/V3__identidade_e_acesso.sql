-- Conta de usuário (identity) e papéis por unidade (access). SSD, seções 2, 8.5 e 9.

create table identity.user_account (
    id           uuid primary key default gen_random_uuid(),
    -- Identificador do usuário no IdP (claim "sub"); no modo sem senha, "dev:<username>".
    subject      varchar(255) not null unique,
    username     varchar(255),
    name         varchar(255),
    email        varchar(255),
    active       boolean      not null default true,
    last_seen_at timestamptz  not null default now(),
    created_at   timestamptz  not null default now()
);

create table access.role (
    id   uuid primary key default gen_random_uuid(),
    key  varchar(40) not null unique,
    name varchar(80) not null
);

create table access.role_assignment (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid        not null references identity.user_account (id),
    role_id     uuid        not null references access.role (id),
    org_unit_id uuid        not null references organization.org_unit (id),
    granted_by  uuid        references identity.user_account (id),
    granted_at  timestamptz not null default now(),
    revoked_at  timestamptz
);
create unique index role_assignment_active_uq
    on access.role_assignment (user_id, role_id, org_unit_id) where revoked_at is null;
create index role_assignment_user_idx on access.role_assignment (user_id) where revoked_at is null;

-- Os oito papéis padrão (SSD, seção 2).
insert into access.role (key, name) values
    ('PLATFORM_ADMIN',  'Administrador da plataforma'),
    ('BUSINESS_OWNER',  'Business Owner'),
    ('PRODUCT_MANAGER', 'Product Manager'),
    ('TECH_LEAD',       'Tech Lead'),
    ('UX_DESIGNER',     'UX/UI Designer'),
    ('QA_ENGINEER',     'QA Engineer'),
    ('SQUAD_MEMBER',    'Membro de squad'),
    ('VIEWER',          'Somente leitura');
