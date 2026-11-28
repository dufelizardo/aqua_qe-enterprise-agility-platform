*** Settings ***
Resource            ../../../flow/knowledge/attachments/attachments_flow.resource

*** Keywords ***
ATTACHMENTS - UPLOAD LIST DOWNLOAD REMOVE
    [Documentation]    Ciclo de um anexo da Iniciativa (SSD 3.7; #13).
    [Arguments]    ${title}    ${file_name}    ${content}    ${content_type}
    Attachments - Enviar Listar Baixar E Remover    ${title}    ${file_name}    ${content}    ${content_type}

ATTACHMENTS - READER AND EMPTY FILE
    [Documentation]    Envio sem edição e arquivo vazio (SSD 2 e 3.7; #13).
    [Arguments]    ${title}    ${file_name}    ${content_type}
    Attachments - Leitor Nao Envia E Vazio Recusado    ${title}    ${file_name}    ${content_type}
