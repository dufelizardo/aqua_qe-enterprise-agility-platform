-- Documentos da Iniciativa e da Feature (SSD 3.7; #63): PRD, SAD, SSD, UX e QA nascem com o artefato, em PENDING e
-- sem versão. Daqui em diante, o módulo livingdocs os cria na criação do artefato; aqui, os artefatos que já existem.
insert into livingdocs.document (owner_type, artifact_id, type, status, template_id, current_version, created_by)
select 'ARTIFACT', a.id, t.document_type, 'PENDING', t.id, 0, a.created_by
  from workitem.artifact a
  join configuration.artifact_type at on at.id = a.type_id and at.key in ('INITIATIVE', 'FEATURE')
  join lateral (
        select distinct on (dt.document_type) dt.id, dt.document_type
          from configuration.doc_template dt
         where dt.document_type in ('PRD', 'SAD', 'SSD', 'UX', 'QA') and dt.org_unit_id is null
         order by dt.document_type, dt.version_number desc
       ) t on true
on conflict (artifact_id, type) where owner_type = 'ARTIFACT' do nothing;
