-- Comentários com @menção, notificações e favoritos (#85; Proposta 07, Fase D: KNOW-024 e KNOW-025).

-- Comentários nas páginas do Wiki e do Regimento. Respostas têm um nível: a resposta aponta para o comentário principal.
-- "Comentar trecho" guarda o texto selecionado em quote. Excluir é lógico (deleted_at), para manter as respostas.
create table livingdocs.page_comment (
    id          uuid primary key default gen_random_uuid(),
    document_id uuid          not null references livingdocs.document (id),
    parent_id   uuid references livingdocs.page_comment (id),
    author_id   uuid          not null,
    body        varchar(2000) not null,
    quote       varchar(500),
    mentions    uuid[]        not null default '{}',
    created_at  timestamptz   not null default now(),
    deleted_at  timestamptz
);
create index page_comment_document_idx on livingdocs.page_comment (document_id, created_at);

-- Notificações de cada pessoa, criadas a partir dos eventos da outbox (menção e resposta).
create schema if not exists notification;
create table notification.notification (
    id          uuid primary key default gen_random_uuid(),
    -- Reentrega do mesmo evento não duplica a notificação (ADR-06).
    event_id    uuid          not null,
    user_id     uuid          not null,
    kind        varchar(20)   not null check (kind in ('MENTION', 'REPLY')),
    actor_id    uuid,
    target_type varchar(20)   not null,
    target_id   uuid          not null,
    org_unit_id uuid,
    title       varchar(200),
    excerpt     varchar(300),
    created_at  timestamptz   not null,
    read_at     timestamptz,
    unique (event_id, user_id)
);
create index notification_user_idx on notification.notification (user_id, created_at desc);

-- Páginas marcadas com estrela por cada pessoa.
create table activity.favorite (
    user_id     uuid         not null,
    target_type varchar(20)  not null check (target_type in ('PAGE')),
    ref         varchar(200) not null,
    created_at  timestamptz  not null default now(),
    primary key (user_id, target_type, ref)
);
create index favorite_user_idx on activity.favorite (user_id, created_at desc);
