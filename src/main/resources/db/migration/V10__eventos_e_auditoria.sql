-- Outbox de eventos de domínio e trilha de auditoria (SAD 7.4 e 7.5, ADR-06 e ADR-07; SSD 7 e 9; REV-03).

create table events.outbox_event (
    -- Ordem global de entrega: a cadeia de hash da auditoria depende dela.
    id           bigserial primary key,
    event_id     uuid         not null unique,
    type         varchar(80)  not null,
    occurred_at  timestamptz  not null,
    envelope     jsonb        not null,
    published_at timestamptz
);
-- Só os não entregues são lidos pelo repassador.
create index outbox_event_pending_idx on events.outbox_event (id) where published_at is null;

create table audit.audit_event (
    sequence       bigserial primary key,
    -- Reentrega do mesmo evento não duplica o registro (entrega "pelo menos uma vez", ADR-06).
    event_id       uuid          not null unique,
    occurred_at    timestamptz   not null,
    actor_id       uuid,
    action         varchar(80)   not null,
    target_type    varchar(40)   not null,
    target_id      uuid,
    target_key     varchar(40),
    before         jsonb,
    after          jsonb,
    correlation_id varchar(64),
    prev_hash      char(64)      not null,
    hash           char(64)      not null
);
create index audit_event_target_idx on audit.audit_event (target_type, target_id);
create index audit_event_occurred_idx on audit.audit_event (occurred_at);
create index audit_event_actor_idx on audit.audit_event (actor_id);
