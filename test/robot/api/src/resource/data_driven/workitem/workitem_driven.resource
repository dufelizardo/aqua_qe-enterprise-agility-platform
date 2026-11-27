*** Comments ***
    Dados do módulo workitem. Os usuários robot.<persona> têm o papel deles na unidade raiz (persona-roles no
    compose), então leem tudo; o isolamento entre ramos é coberto pelos testes JUnit (WorkitemIntegrationTests).

*** Variables ***
# post_artifact (POST /api/v1/artifacts)
${post_artifact_initiative_title}       Robot Iniciativa
${post_artifact_initiative_summary}     Iniciativa criada pela suíte Robot de artefatos.
${post_artifact_feature_title}          Robot Feature
${post_artifact_unknown_key}            INIT-999999

# patch_artifact (PATCH /api/v1/artifacts/{artifact})
${patch_artifact_new_title}             Robot Iniciativa Renomeada

# unit_people (GET /api/v1/org-units/{id}/people; #12, REV-07)
${unit_people_initiative_title}         Robot Iniciativa Com Tech Lead E QA
${unit_people_unknown_type}             TIPO_INEXISTENTE

# default_owner (#43: responsável padrão por unidade e tipo)
${default_owner_initiative_title}        Robot Iniciativa com Owner Padrão
