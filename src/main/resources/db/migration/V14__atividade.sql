-- Home: "Continuar de onde parou" e "Novidades" (#83). Módulo activity, com schema próprio (ADR-05).
create schema if not exists activity;

-- Últimos artefatos, documentos e páginas que cada pessoa abriu. Uma linha por item; abrir de novo só atualiza a hora.
create table activity.recent_visit (
    user_id     uuid         not null,
    target_type varchar(20)  not null check (target_type in ('ARTIFACT', 'DOCUMENT', 'PAGE')),
    -- ARTIFACT: chave (INIT-1); DOCUMENT: chave/tipo (INIT-1/PRD); PAGE: id da página.
    ref         varchar(200) not null,
    visited_at  timestamptz  not null default now(),
    primary key (user_id, target_type, ref)
);
create index recent_visit_user_idx on activity.recent_visit (user_id, visited_at desc);

-- O que mudou em cada unidade: cópia enxuta dos eventos de artefatos, documentos e páginas que vão para a trilha de
-- auditoria, com a unidade (a trilha não guarda a unidade e não é consultável por quem não é administrador).
create table activity.unit_event (
    event_id    uuid primary key,
    occurred_at timestamptz  not null,
    org_unit_id uuid         not null,
    actor_id    uuid,
    action      varchar(80)  not null,
    target_type varchar(40)  not null,
    target_id   uuid         not null,
    target_key  varchar(200)
);
create index unit_event_unit_idx on activity.unit_event (org_unit_id, occurred_at desc);

-- Carga inicial a partir da trilha que já existe (uma vez, como na V12): a unidade vem do artefato ou da página.
insert into activity.unit_event (event_id, occurred_at, org_unit_id, actor_id, action, target_type, target_id, target_key)
select e.event_id, e.occurred_at, a.org_unit_id, e.actor_id, e.action, e.target_type, e.target_id, e.target_key
  from audit.audit_event e
  join workitem.artifact a on a.id = e.target_id
 where e.target_type = 'ARTIFACT' and e.action in ('artifact.created', 'artifact.updated', 'artifact.status_changed', 'artifact.archived')
union all
select e.event_id, e.occurred_at, a.org_unit_id, e.actor_id, e.action, e.target_type, e.target_id, e.target_key
  from audit.audit_event e
  join livingdocs.document d on d.id = e.target_id
  join workitem.artifact a on a.id = d.artifact_id
 where e.target_type = 'DOCUMENT'
union all
select e.event_id, e.occurred_at, d.org_unit_id, e.actor_id, e.action, e.target_type, e.target_id, e.target_key
  from audit.audit_event e
  join livingdocs.document d on d.id = e.target_id
 where e.target_type = 'KNOWLEDGE_PAGE'
on conflict (event_id) do nothing;
