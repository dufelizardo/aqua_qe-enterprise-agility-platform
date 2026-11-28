-- Preferências pessoais (#15; REV-10): tema e avisos do sino. Sem linha, valem os padrões (tema do sistema, avisos ligados).
create table identity.user_preference (
    user_id        uuid        primary key references identity.user_account (id),
    theme          varchar(10) not null default 'SYSTEM' check (theme in ('SYSTEM', 'LIGHT', 'DARK')),
    notify_mention boolean     not null default true,
    notify_reply   boolean     not null default true,
    updated_at     timestamptz not null default now()
);
