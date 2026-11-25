-- A chave do alvo na auditoria vira o título das páginas de conhecimento (até 200 caracteres, SSD 3.7.1). Com 40,
-- uma página de título longo travava a entrega da outbox para a trilha (OutboxRelay reentrega o mesmo evento).
alter table audit.audit_event alter column target_key type varchar(200);
