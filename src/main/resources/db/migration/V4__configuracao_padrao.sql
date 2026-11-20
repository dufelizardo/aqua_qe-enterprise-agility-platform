-- Tipos de artefato e workflows da instalação padrão (SSD, seções 3.3, 8.1, 8.2 e 9). No MVP, só leitura pela API.

create table configuration.artifact_type (
    id              uuid primary key default gen_random_uuid(),
    key             varchar(40) not null unique,
    level           varchar(3)  not null check (level in ('FL1', 'FL2', 'FL3')),
    name            varchar(80) not null,
    key_prefix      varchar(10) not null unique,
    parent_required boolean     not null
);

create table configuration.artifact_type_parent (
    type_id        uuid not null references configuration.artifact_type (id),
    parent_type_id uuid not null references configuration.artifact_type (id),
    primary key (type_id, parent_type_id)
);

create table configuration.workflow (
    id             uuid primary key default gen_random_uuid(),
    name           varchar(120) not null,
    version_number integer      not null,
    org_unit_id    uuid         not null references organization.org_unit (id),
    active         boolean      not null default true
);

create table configuration.workflow_artifact_type (
    workflow_id      uuid not null references configuration.workflow (id),
    artifact_type_id uuid not null references configuration.artifact_type (id),
    primary key (workflow_id, artifact_type_id)
);

create table configuration.workflow_status (
    id          uuid primary key default gen_random_uuid(),
    workflow_id uuid        not null references configuration.workflow (id),
    key         varchar(40) not null,
    name        varchar(80) not null,
    phase       varchar(12) check (phase in ('UPSTREAM', 'DOWNSTREAM')),
    category    varchar(12) not null check (category in ('TODO', 'IN_PROGRESS', 'DONE')),
    -- Sem posição: status fora da sequência (Cancelado).
    position    integer,
    unique (workflow_id, key)
);

create table configuration.workflow_transition (
    id             uuid primary key default gen_random_uuid(),
    workflow_id    uuid not null references configuration.workflow (id),
    from_status_id uuid not null references configuration.workflow_status (id),
    to_status_id   uuid not null references configuration.workflow_status (id),
    unique (from_status_id, to_status_id)
);

-- 8.1 Tipos de artefato
insert into configuration.artifact_type (key, level, name, key_prefix, parent_required) values
    ('INITIATIVE',         'FL3', 'Iniciativa',             'INIT',  false),
    ('FEATURE',            'FL2', 'Feature',                'FEAT',  true),
    ('SUSTAINING_FEATURE', 'FL2', 'Feature de Sustentação', 'SUST',  false),
    ('STORY',              'FL1', 'História',               'STORY', true),
    ('SPIKE',              'FL1', 'Spike',                  'SPIKE', false),
    ('TECH_DEBT',          'FL1', 'Débito Técnico',         'DEBT',  false),
    ('BUG',                'FL1', 'Bug',                    'BUG',   false),
    ('PRODUCTION_ISSUE',   'FL1', 'Problema de Produção',   'PROD',  false),
    ('MAINTENANCE',        'FL1', 'Sustentação',            'MAINT', false),
    ('TASK',               'FL1', 'Task',                   'TASK',  false),
    ('SUBTASK',            'FL1', 'Subtask',                'SUB',   true);

insert into configuration.artifact_type_parent (type_id, parent_type_id)
select t.id, p.id
from (values
    ('FEATURE', 'INITIATIVE'),
    ('SUSTAINING_FEATURE', 'INITIATIVE'),
    ('STORY', 'FEATURE'), ('STORY', 'SUSTAINING_FEATURE'),
    ('SPIKE', 'FEATURE'), ('SPIKE', 'SUSTAINING_FEATURE'),
    ('TECH_DEBT', 'FEATURE'), ('TECH_DEBT', 'SUSTAINING_FEATURE'),
    ('BUG', 'FEATURE'), ('BUG', 'SUSTAINING_FEATURE'), ('BUG', 'STORY'),
    ('MAINTENANCE', 'SUSTAINING_FEATURE'),
    ('TASK', 'FEATURE'), ('TASK', 'SUSTAINING_FEATURE'), ('TASK', 'STORY')
) as v(type_key, parent_key)
join configuration.artifact_type t on t.key = v.type_key
join configuration.artifact_type p on p.key = v.parent_key;

-- Subtask: qualquer tipo de FL1, exceto Subtask.
insert into configuration.artifact_type_parent (type_id, parent_type_id)
select t.id, p.id
from configuration.artifact_type t
join configuration.artifact_type p on p.level = 'FL1' and p.key <> 'SUBTASK'
where t.key = 'SUBTASK';

-- 8.2 Workflows (versão 1, na unidade raiz)
create temporary table seed_workflow (wkey varchar(20), name varchar(120)) on commit drop;
insert into seed_workflow values
    ('INITIATIVE', 'Fluxo padrão de Iniciativa'),
    ('FEATURE',    'Fluxo padrão de Feature'),
    ('ITEM',       'Fluxo padrão de itens de FL1'),
    ('TASK',       'Fluxo padrão de Task e Subtask'),
    ('PRODUCTION', 'Fluxo padrão de Problema de Produção');

create temporary table seed_workflow_type (wkey varchar(20), type_key varchar(40)) on commit drop;
insert into seed_workflow_type values
    ('INITIATIVE', 'INITIATIVE'),
    ('FEATURE', 'FEATURE'), ('FEATURE', 'SUSTAINING_FEATURE'),
    ('ITEM', 'STORY'), ('ITEM', 'SPIKE'), ('ITEM', 'TECH_DEBT'), ('ITEM', 'BUG'), ('ITEM', 'MAINTENANCE'),
    ('TASK', 'TASK'), ('TASK', 'SUBTASK'),
    ('PRODUCTION', 'PRODUCTION_ISSUE');

create temporary table seed_status (wkey varchar(20), key varchar(40), name varchar(80), phase varchar(12),
    category varchar(12), position integer) on commit drop;
insert into seed_status values
    ('INITIATIVE', 'IDEA',              'Ideia',                  'UPSTREAM',   'TODO',        1),
    ('INITIATIVE', 'BACKLOG',           'Backlog',                'UPSTREAM',   'TODO',        2),
    ('INITIATIVE', 'FEASIBILITY',       'Estudo de Viabilidade',  'UPSTREAM',   'IN_PROGRESS', 3),
    ('INITIATIVE', 'REFINEMENT',        'Em Refinamento',         'UPSTREAM',   'IN_PROGRESS', 4),
    ('INITIATIVE', 'BUILDING',          'Em Construção',          'DOWNSTREAM', 'IN_PROGRESS', 5),
    ('INITIATIVE', 'DONE',              'Concluído',              'DOWNSTREAM', 'DONE',        6),
    ('FEATURE',    'BACKLOG',           'Backlog',                'UPSTREAM',   'TODO',        1),
    ('FEATURE',    'REFINEMENT',        'Em Refinamento',         'UPSTREAM',   'IN_PROGRESS', 2),
    ('FEATURE',    'READY_TO_BUILD',    'Pronto para Construir',  'UPSTREAM',   'TODO',        3),
    ('FEATURE',    'DEVELOPMENT',       'Em Desenvolvimento',     'DOWNSTREAM', 'IN_PROGRESS', 4),
    ('FEATURE',    'VALIDATION',        'Em Validação',           'DOWNSTREAM', 'IN_PROGRESS', 5),
    ('FEATURE',    'READY_FOR_RELEASE', 'Pronto para Release',    'DOWNSTREAM', 'IN_PROGRESS', 6),
    ('FEATURE',    'DEPLOYED',          'Implantado',             'DOWNSTREAM', 'IN_PROGRESS', 7),
    ('FEATURE',    'STABILIZATION',     'Em Estabilização',       'DOWNSTREAM', 'IN_PROGRESS', 8),
    ('FEATURE',    'DONE',              'Concluído',              'DOWNSTREAM', 'DONE',        9),
    ('ITEM',       'BACKLOG',           'Backlog',                'UPSTREAM',   'TODO',        1),
    ('ITEM',       'READY',             'Pronto',                 'UPSTREAM',   'TODO',        2),
    ('ITEM',       'DEVELOPMENT',       'Em Desenvolvimento',     'DOWNSTREAM', 'IN_PROGRESS', 3),
    ('ITEM',       'CODE_REVIEW',       'Em Code Review',         'DOWNSTREAM', 'IN_PROGRESS', 4),
    ('ITEM',       'TESTING',           'Em Teste',               'DOWNSTREAM', 'IN_PROGRESS', 5),
    ('ITEM',       'DONE',              'Concluído',              'DOWNSTREAM', 'DONE',        6),
    ('TASK',       'TODO',              'A Fazer',                null,         'TODO',        1),
    ('TASK',       'IN_PROGRESS',       'Em Andamento',           null,         'IN_PROGRESS', 2),
    ('TASK',       'DONE',              'Concluído',              null,         'DONE',        3),
    ('PRODUCTION', 'OPEN',              'Aberto',                 null,         'TODO',        1),
    ('PRODUCTION', 'INVESTIGATING',     'Em Investigação',        null,         'IN_PROGRESS', 2),
    ('PRODUCTION', 'FIXING',            'Em Correção',            null,         'IN_PROGRESS', 3),
    ('PRODUCTION', 'RESOLVED',          'Resolvido',              null,         'IN_PROGRESS', 4),
    ('PRODUCTION', 'CLOSED',            'Fechado',                null,         'DONE',        5);
-- Todo workflow tem também Cancelado, fora da sequência.
insert into seed_status select wkey, 'CANCELLED', 'Cancelado', null, 'DONE', null from seed_workflow;

alter table seed_workflow add column id uuid default gen_random_uuid();
update seed_workflow set id = gen_random_uuid();

insert into configuration.workflow (id, name, version_number, org_unit_id)
select w.id, w.name, 1, u.id
from seed_workflow w
cross join organization.org_unit u
where u.parent_id is null;

insert into configuration.workflow_artifact_type (workflow_id, artifact_type_id)
select w.id, t.id
from seed_workflow_type wt
join seed_workflow w on w.wkey = wt.wkey
join configuration.artifact_type t on t.key = wt.type_key;

insert into configuration.workflow_status (workflow_id, key, name, phase, category, position)
select w.id, s.key, s.name, s.phase, s.category, s.position
from seed_status s
join seed_workflow w on w.wkey = s.wkey;

-- Transições padrão (SSD 8.2): avançar, voltar e, de qualquer status não final, ir para Cancelado.
insert into configuration.workflow_transition (workflow_id, from_status_id, to_status_id)
select a.workflow_id, a.id, b.id
from configuration.workflow_status a
join configuration.workflow_status b
  on b.workflow_id = a.workflow_id and a.position is not null and b.position is not null
 and (b.position = a.position + 1 or b.position = a.position - 1);

insert into configuration.workflow_transition (workflow_id, from_status_id, to_status_id)
select a.workflow_id, a.id, c.id
from configuration.workflow_status a
join configuration.workflow_status c on c.workflow_id = a.workflow_id and c.key = 'CANCELLED'
where a.category <> 'DONE';
