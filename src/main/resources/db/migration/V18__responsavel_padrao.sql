-- Responsável padrão do artefato criado sem Owner (#43; SSD 3.4). Uma regra por unidade e tipo de artefato; a unidade
-- sem regra própria herda a da unidade acima mais próxima; sem regra em toda a linhagem, o artefato nasce sem Owner.
create table workitem.default_owner_rule (
    org_unit_id      uuid        not null references organization.org_unit (id),
    artifact_type_id uuid        not null references configuration.artifact_type (id),
    -- NONE: sem Owner; CREATOR: quem criou; ROLE: quem tem o papel na unidade do item (ou na acima mais próxima).
    mode             varchar(10) not null check (mode in ('NONE', 'CREATOR', 'ROLE')),
    role             varchar(40),
    updated_by       uuid,
    updated_at       timestamptz not null default now(),
    primary key (org_unit_id, artifact_type_id),
    check ((mode = 'ROLE') = (role is not null))
);
