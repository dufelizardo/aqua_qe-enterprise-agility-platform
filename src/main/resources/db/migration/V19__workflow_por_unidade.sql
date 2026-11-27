-- Workflow próprio por unidade, com versões (#49; SSD 3.3; F05/D-01). Cada linha de configuration.workflow é uma
-- versão imutável, com status e transições próprios. As versões de um mesmo workflow têm o mesmo family_id; só a mais
-- recente fica ativa. Os artefatos ficam na versão em que estão (o status_id aponta para um status dela); a migração
-- para a versão ativa é explícita.
alter table configuration.workflow add column family_id uuid;
update configuration.workflow set family_id = id;
alter table configuration.workflow alter column family_id set not null;
alter table configuration.workflow add column published_by uuid;
alter table configuration.workflow add column published_at timestamptz not null default now();
create unique index workflow_family_active_idx on configuration.workflow (family_id) where active;
create index workflow_org_unit_idx on configuration.workflow (org_unit_id) where active;
