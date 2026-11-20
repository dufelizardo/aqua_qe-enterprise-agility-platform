*** Comments ***
    Dados do módulo identity. Cada variável leva o nome da operação como prefixo, para ser única no agregador.
    As personas correspondem aos papéis do SSD, seção 2, e aos usuários robot.<persona> do realm aquaqe-dev.

*** Variables ***
# get_me (GET /api/v1/me)
${get_me_invalid_token}     eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJmYWxzbyJ9.assinatura-invalida
