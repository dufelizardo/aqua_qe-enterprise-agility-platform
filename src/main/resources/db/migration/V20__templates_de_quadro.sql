-- Templates de quadro (#50; Proposta 05, MDL-07 e CFG-006). Catálogo único na organização. O template guarda as colunas
-- de um quadro (nome, fase, status, limite e política de WIP) e é aplicado por cópia ao criar um quadro: mudar ou
-- remover o template não altera os quadros existentes.
create table board.board_template (
    id          uuid primary key default gen_random_uuid(),
    name        varchar(120) not null,
    level       varchar(3)   not null check (level in ('FL1', 'FL2', 'FL3')),
    description varchar(300),
    -- [{"name", "phase", "statusKeys": [...], "wipLimit", "wipPolicy"}], na ordem das colunas.
    columns     jsonb        not null,
    -- Os que vêm com a instalação.
    builtin     boolean      not null default false,
    created_by  uuid,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);
create unique index board_template_name_idx on board.board_template (level, lower(name));

-- Dois por nível, com os status dos workflows padrão (SSD 8.2). Status que não valem para a unidade do quadro são
-- ignorados ao aplicar.
insert into board.board_template (name, level, description, columns, builtin) values
('Kanban simples', 'FL1', 'Três colunas: A fazer, Fazendo e Feito.', '[
  {"name": "A fazer", "phase": "UPSTREAM", "statusKeys": ["BACKLOG", "READY", "TODO", "OPEN"], "wipLimit": null, "wipPolicy": "WARN"},
  {"name": "Fazendo", "phase": "DOWNSTREAM", "statusKeys": ["DEVELOPMENT", "CODE_REVIEW", "TESTING", "IN_PROGRESS", "INVESTIGATING", "FIXING", "RESOLVED"], "wipLimit": null, "wipPolicy": "WARN"},
  {"name": "Feito", "phase": "DOWNSTREAM", "statusKeys": ["DONE", "CLOSED"], "wipLimit": null, "wipPolicy": "WARN"}
]', true),
('Kanban — Sustentação', 'FL1', 'Triagem, atendimento com limite de WIP, validação e resolvido.', '[
  {"name": "Triagem", "phase": "UPSTREAM", "statusKeys": ["OPEN", "BACKLOG", "READY", "TODO"], "wipLimit": null, "wipPolicy": "WARN"},
  {"name": "Em atendimento", "phase": "DOWNSTREAM", "statusKeys": ["INVESTIGATING", "FIXING", "DEVELOPMENT", "IN_PROGRESS"], "wipLimit": 5, "wipPolicy": "WARN"},
  {"name": "Validando", "phase": "DOWNSTREAM", "statusKeys": ["CODE_REVIEW", "TESTING", "RESOLVED"], "wipLimit": null, "wipPolicy": "WARN"},
  {"name": "Resolvido", "phase": "DOWNSTREAM", "statusKeys": ["DONE", "CLOSED"], "wipLimit": null, "wipPolicy": "WARN"}
]', true),
('Kanban simples', 'FL2', 'Três colunas: A fazer, Fazendo e Feito.', '[
  {"name": "A fazer", "phase": "UPSTREAM", "statusKeys": ["BACKLOG", "REFINEMENT", "READY_TO_BUILD"], "wipLimit": null, "wipPolicy": "WARN"},
  {"name": "Fazendo", "phase": "DOWNSTREAM", "statusKeys": ["DEVELOPMENT", "VALIDATION", "READY_FOR_RELEASE", "DEPLOYED", "STABILIZATION"], "wipLimit": null, "wipPolicy": "WARN"},
  {"name": "Feito", "phase": "DOWNSTREAM", "statusKeys": ["DONE"], "wipLimit": null, "wipPolicy": "WARN"}
]', true),
('Fluxo de Feature agrupado', 'FL2', 'Descoberta e pronto no upstream; construção, entrega e concluído no downstream.', '[
  {"name": "Descoberta", "phase": "UPSTREAM", "statusKeys": ["BACKLOG", "REFINEMENT"], "wipLimit": null, "wipPolicy": "WARN"},
  {"name": "Pronto para Construir", "phase": "UPSTREAM", "statusKeys": ["READY_TO_BUILD"], "wipLimit": null, "wipPolicy": "WARN"},
  {"name": "Construção", "phase": "DOWNSTREAM", "statusKeys": ["DEVELOPMENT", "VALIDATION"], "wipLimit": null, "wipPolicy": "WARN"},
  {"name": "Entrega", "phase": "DOWNSTREAM", "statusKeys": ["READY_FOR_RELEASE", "DEPLOYED", "STABILIZATION"], "wipLimit": null, "wipPolicy": "WARN"},
  {"name": "Concluído", "phase": "DOWNSTREAM", "statusKeys": ["DONE"], "wipLimit": null, "wipPolicy": "WARN"}
]', true),
('Kanban simples', 'FL3', 'Três colunas: A fazer, Fazendo e Feito.', '[
  {"name": "A fazer", "phase": "UPSTREAM", "statusKeys": ["IDEA", "BACKLOG", "FEASIBILITY", "REFINEMENT"], "wipLimit": null, "wipPolicy": "WARN"},
  {"name": "Fazendo", "phase": "DOWNSTREAM", "statusKeys": ["BUILDING"], "wipLimit": null, "wipPolicy": "WARN"},
  {"name": "Feito", "phase": "DOWNSTREAM", "statusKeys": ["DONE"], "wipLimit": null, "wipPolicy": "WARN"}
]', true),
('Funil de Portfólio', 'FL3', 'Funil e análise no upstream; execução e concluído no downstream.', '[
  {"name": "Funil", "phase": "UPSTREAM", "statusKeys": ["IDEA", "BACKLOG"], "wipLimit": null, "wipPolicy": "WARN"},
  {"name": "Análise", "phase": "UPSTREAM", "statusKeys": ["FEASIBILITY", "REFINEMENT"], "wipLimit": null, "wipPolicy": "WARN"},
  {"name": "Em execução", "phase": "DOWNSTREAM", "statusKeys": ["BUILDING"], "wipLimit": null, "wipPolicy": "WARN"},
  {"name": "Concluído", "phase": "DOWNSTREAM", "statusKeys": ["DONE"], "wipLimit": null, "wipPolicy": "WARN"}
]', true);
