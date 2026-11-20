-- Quadros por unidade e nível (SSD, seções 3.9 e 9). Sprints entram depois.

create table board.board (
    id          uuid primary key default gen_random_uuid(),
    org_unit_id uuid         not null references organization.org_unit (id),
    level       varchar(3)   not null check (level in ('FL1', 'FL2', 'FL3')),
    name        varchar(120) not null,
    filters     jsonb        not null default '{}',
    created_by  uuid         references identity.user_account (id),
    created_at  timestamptz  not null default now()
);
create index board_org_unit_idx on board.board (org_unit_id);

create table board.board_column (
    id          uuid primary key default gen_random_uuid(),
    board_id    uuid         not null references board.board (id),
    position    integer      not null,
    name        varchar(80)  not null,
    phase       varchar(12)  not null check (phase in ('UPSTREAM', 'DOWNSTREAM')),
    status_keys text[]       not null,
    unique (board_id, position)
);
