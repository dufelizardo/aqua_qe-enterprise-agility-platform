*** Comments ***
    Dados do módulo identity. Cada variável leva o nome da operação como prefixo, para ser única no agregador.
    As personas correspondem aos papéis do SSD, seção 2, e aos usuários robot.<persona> do realm aquaqe-dev.

*** Variables ***
# get_me (GET /api/v1/me)
${get_me_invalid_token}     eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJmYWxzbyJ9.assinatura-invalida
# Papel de cada persona na unidade raiz (application.yml, aquaqe.auth.dev-personas)
&{get_me_persona_roles}     admin=PLATFORM_ADMIN    bo=BUSINESS_OWNER    pm=PRODUCT_MANAGER    tl=TECH_LEAD
...                         ux=UX_DESIGNER    qa=QA_ENGINEER    squad=SQUAD_MEMBER    viewer=VIEWER
# preferences (GET/PUT /api/v1/me/preferences, #15)
${preferences_persona}      ux
${preferences_theme}        DARK
