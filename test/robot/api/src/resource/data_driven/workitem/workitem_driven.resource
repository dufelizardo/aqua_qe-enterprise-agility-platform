*** Comments ***
    Dados do módulo workitem. Os usuários robot.<persona> têm o papel deles na unidade raiz (persona-roles no
    compose), então leem tudo; o isolamento entre ramos é coberto pelos testes JUnit (WorkitemIntegrationTests).

*** Variables ***
# post_artifact (POST /api/v1/artifacts)
${post_artifact_initiative_title}       Robot Iniciativa
${post_artifact_initiative_summary}     Iniciativa criada pela suíte Robot de artefatos.
${post_artifact_feature_title}          Robot Feature
${post_artifact_unknown_key}            INIT-999999
