-- Sprints opcionais por squad e ordenação dos itens (SSD 3.4 e 3.9; PRD-RF-015).

create table board.sprint (
    id         uuid primary key default gen_random_uuid(),
    squad_id   uuid          not null references organization.org_unit (id),
    name       varchar(120)  not null,
    goal       varchar(1000),
    start_date date,
    end_date   date,
    state      varchar(7)    not null default 'PLANNED' check (state in ('PLANNED', 'ACTIVE', 'CLOSED')),
    created_at timestamptz   not null default now(),
    check (end_date is null or start_date is null or end_date >= start_date)
);
create index sprint_squad_idx on board.sprint (squad_id);
-- No máximo uma sprint ativa por squad.
create unique index sprint_one_active_per_squad on board.sprint (squad_id) where state = 'ACTIVE';

create table board.sprint_item (
    sprint_id   uuid not null references board.sprint (id),
    artifact_id uuid not null references workitem.artifact (id),
    primary key (sprint_id, artifact_id)
);
create index sprint_item_artifact_idx on board.sprint_item (artifact_id);

-- Ordenação: cada item ganha uma posição; os novos entram no fim (POST /artifacts/{artifact}/rank reposiciona).
update workitem.artifact set rank = extract(epoch from created_at) * 1000 where rank is null;
alter table workitem.artifact alter column rank set default (extract(epoch from clock_timestamp()) * 1000);
create index artifact_rank_idx on workitem.artifact (rank);
