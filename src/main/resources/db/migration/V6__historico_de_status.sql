-- Histórico de mudanças de status (SSD, seções 3.5 e 9). Só inserção.

create table workflow.status_history (
    id             uuid primary key default gen_random_uuid(),
    artifact_id    uuid          not null references workitem.artifact (id),
    from_status_id uuid          not null references configuration.workflow_status (id),
    to_status_id   uuid          not null references configuration.workflow_status (id),
    transition_id  uuid          not null references configuration.workflow_transition (id),
    changed_by     uuid          not null references identity.user_account (id),
    changed_at     timestamptz   not null default now(),
    comment        varchar(1000)
);
create index status_history_artifact_idx on workflow.status_history (artifact_id, changed_at);
