-- Garantias da auditoria (#8; REV-03; SAD 7.5).

-- 1. A trilha só aceita inserção: UPDATE, DELETE e TRUNCATE são recusados para qualquer usuário. Quem administra o banco
--    ainda pode desligar o gatilho, mas aí a cadeia de hash e as âncoras denunciam a alteração.
create function audit.reject_change() returns trigger language plpgsql as $$
begin
    raise exception 'audit.% só aceita inserção (REV-03)', tg_table_name using errcode = 'insufficient_privilege';
end
$$;

create trigger audit_event_append_only before update or delete on audit.audit_event
    for each row execute function audit.reject_change();
create trigger audit_event_no_truncate before truncate on audit.audit_event
    for each statement execute function audit.reject_change();

-- 2. Âncoras da cadeia: de tempos em tempos, a sequência e o hash do último registro. Cada âncora também vai para o log
--    estruturado, que pode ser guardado fora do banco; a verificação confere a trilha com as âncoras.
create table audit.hash_anchor (
    id          bigserial   primary key,
    sequence    bigint      not null unique,
    hash        varchar(64) not null,
    anchored_at timestamptz not null default now()
);
create trigger hash_anchor_append_only before update or delete on audit.hash_anchor
    for each row execute function audit.reject_change();
create trigger hash_anchor_no_truncate before truncate on audit.hash_anchor
    for each statement execute function audit.reject_change();

-- 3. Fila de falhas dos consumidores que não são a auditoria (D-03): depois de 5 tentativas, o evento fica aqui para o
--    administrador reprocessar, e a entrega segue. A auditoria nunca vem para cá: ela para e alerta.
create table events.consumer_failure (
    id              uuid primary key default gen_random_uuid(),
    event_id        uuid          not null,
    consumer        varchar(120)  not null,
    envelope        jsonb         not null,
    attempts        int           not null,
    last_error      varchar(1000),
    first_failed_at timestamptz   not null default now(),
    last_failed_at  timestamptz   not null default now(),
    dead_at         timestamptz,
    resolved_at     timestamptz,
    resolved_by     uuid,
    unique (event_id, consumer)
);
create index consumer_failure_open_idx on events.consumer_failure (dead_at) where dead_at is not null and resolved_at is null;
