-- Limite de WIP por coluna e marca de bloqueado (Proposta 04, decisões de 2026-11-20; SSD 3.4 e 3.9).

alter table board.board_column
    add column wip_limit  integer check (wip_limit is null or wip_limit >= 1),
    -- WARN avisa quando a coluna atinge ou passa do limite; BLOCK também recusa a entrada (guarda de transição).
    add column wip_policy varchar(5) not null default 'WARN' check (wip_policy in ('WARN', 'BLOCK'));

alter table workitem.artifact
    add column blocked        boolean      not null default false,
    add column blocked_reason varchar(500);
