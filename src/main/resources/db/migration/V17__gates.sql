-- Gates por transição do workflow (SSD 5; #78; PRD-RF-022 a 024).
create table governance.gate (
    id            uuid primary key default gen_random_uuid(),
    -- Um gate por transição.
    transition_id uuid          not null unique references configuration.workflow_transition (id),
    name          varchar(120)  not null,
    updated_by    uuid,
    updated_at    timestamptz   not null default now()
);

-- Regras do gate, em ordem. Salvar o gate troca todas as regras (ids novos).
create table governance.gate_rule (
    id        uuid primary key default gen_random_uuid(),
    gate_id   uuid          not null references governance.gate (id) on delete cascade,
    position  int           not null,
    type      varchar(40)   not null,
    params    jsonb         not null default '{}',
    message   varchar(300)  not null
);
create index gate_rule_gate_idx on governance.gate_rule (gate_id, position);

-- Decisões das regras ROLE_APPROVAL. Vale a última decisão dada no status atual do artefato: ao mudar de status, a
-- aprovação deixa de valer para os gates do status novo.
create table governance.approval (
    id         uuid primary key default gen_random_uuid(),
    artifact_id uuid         not null,
    rule_id    uuid          not null,
    status_id  uuid          not null,
    decision   varchar(10)   not null check (decision in ('APPROVED', 'REJECTED')),
    comment    varchar(1000),
    decided_by uuid          not null,
    decided_at timestamptz   not null default now()
);
create index approval_artifact_idx on governance.approval (artifact_id, rule_id, status_id, decided_at desc);
