*** Settings ***
Documentation       Navegador compartilhado pelas suítes de interface (biblioteca Browser, Playwright). Sem asserções.
Library             Browser

*** Variables ***
${AQUA_UI_URL}          %{AQUA_UI_URL=http://localhost:4200}
${AQUA_TEST_PASSWORD}   %{AQUA_TEST_PASSWORD=Robot#Teste123}
${AQUA_HEADLESS}        %{AQUA_HEADLESS=true}

*** Keywords ***
Open AQuA-QE Browser
    [Documentation]    Abre um navegador novo, sem sessão, no caminho informado (padrão: a landing pública).
    [Arguments]    ${path}=/
    New Browser    chromium    headless=${AQUA_HEADLESS}
    New Context    locale=pt-BR
    New Page    ${AQUA_UI_URL}${path}

Go To AQuA-QE Path
    [Arguments]    ${path}
    Go To    ${AQUA_UI_URL}${path}

Close AQuA-QE Browser
    Close Browser    ALL

Get Current Page Url
    ${url}=    Get Url
    RETURN    ${url}
