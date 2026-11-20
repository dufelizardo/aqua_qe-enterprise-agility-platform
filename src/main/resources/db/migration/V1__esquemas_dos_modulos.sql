-- Um schema por módulo (ADR-05). Cada módulo é dono das próprias tabelas; nenhum acessa tabelas de outro.
create schema if not exists identity;
create schema if not exists access;
create schema if not exists organization;
create schema if not exists configuration;
create schema if not exists workitem;
create schema if not exists workflow;
create schema if not exists governance;
create schema if not exists livingdocs;
create schema if not exists portfolio;
create schema if not exists board;
create schema if not exists audit;
create schema if not exists events;
