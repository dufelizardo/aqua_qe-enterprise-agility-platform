*** Settings ***
Documentation       Ações na tela Wiki. Sem asserções: as esperas só sincronizam.
Library             Browser
Library             OperatingSystem
Library             Collections
Resource            ../../resource/locators/wiki/wiki_locator.resource

*** Keywords ***
Wait For Wiki Tree
    Wait For Elements State    ${WIKI_TREE}    visible    timeout=30s

Create Wiki Page
    [Arguments]    ${title}
    Click    ${WIKI_NEW_PAGE}
    Fill Text    ${WIKI_NEW_TITLE}    ${title}
    Click    ${WIKI_CREATE_SUBMIT}

Wait For Regimento Empty
    Wait For Elements State    ${REGIMENTO_EMPTY}    visible    timeout=30s

Create Regimento
    [Documentation]    Na tela Regimento da unidade sem regimento: "Criar regimento" com o título informado.
    [Arguments]    ${title}
    Click    ${REGIMENTO_CREATE}
    Fill Text    ${WIKI_NEW_TITLE}    ${title}
    Click    ${WIKI_CREATE_SUBMIT}

Wait For Wiki Page Title
    [Arguments]    ${title}
    Wait For Condition    Text    ${WIKI_PAGE_TITLE}    ==    ${title}    timeout=30s

Wait For Wiki Page Link
    [Arguments]    ${title}
    ${locator}=    Evaluate    $WIKI_PAGE_LINK_BY_TITLE.replace('{}', $title)
    Wait For Elements State    ${locator}    visible    timeout=30s

Write Paragraph And Save
    [Documentation]    Editar, clicar na área de texto, ligar o negrito pela barra, digitar e salvar (bug #67).
    [Arguments]    ${text}    ${summary}
    Click    ${WIKI_EDIT}
    Wait For Elements State    ${WIKI_EDITOR}    visible    timeout=30s
    Click    ${WIKI_EDITOR}
    Click    ${WIKI_RT_BOLD}
    Keyboard Input    type    ${text}
    Fill Text    ${WIKI_SUMMARY}    ${summary}
    Click    ${WIKI_SAVE}

Get Bold Text Count
    ${count}=    Get Element Count    ${WIKI_READ_BOLD}
    RETURN    ${count}

Wait For Wiki Page Version
    [Arguments]    ${number}
    Wait For Condition    Text    ${WIKI_PAGE_VERSION}    ==    Versão ${number}    timeout=30s

Get Wiki Page Text
    ${text}=    Get Text    ${WIKI_PAGE}
    RETURN    ${text}

Open Wiki History
    Click    ${WIKI_HISTORY}
    Wait For Elements State    ${WIKI_VERSION_LINKS} >> nth=0    visible    timeout=30s

Get Wiki Version Count
    ${count}=    Get Element Count    ${WIKI_VERSION_LINKS}
    RETURN    ${count}

Wait For Wiki Status
    [Arguments]    ${label}
    Wait For Condition    Text    ${WIKI_PAGE_STATUS}    ==    ${label}    timeout=30s

Click Submit For Review
    Click    ${WIKI_SUBMIT_REVIEW}

Click Approve
    Click    ${WIKI_APPROVE}

Click Back To Draft
    Click    ${WIKI_BACK_DRAFT}

Get Approve Button Count
    ${count}=    Get Element Count    ${WIKI_APPROVE}
    RETURN    ${count}

Wait For Wiki Error
    Wait For Elements State    ${WIKI_ERROR}    visible    timeout=30s

Get Wiki Error Text
    ${text}=    Get Text    ${WIKI_ERROR}
    RETURN    ${text}

Get Wiki Missing Text
    ${text}=    Get Text    ${WIKI_MISSING}
    RETURN    ${text}

Take Wiki Screenshot
    [Arguments]    ${name}
    Take Screenshot    filename=${name}

Click Wiki Up
    [Documentation]    Menu ⋯ da página aberta, na árvore: "↑ Subir".
    Click    ${WIKI_CURRENT_NODE_MENU}
    Click    ${WIKI_UP}

Move Wiki Page Into
    [Documentation]    Menu ⋯ da página aberta, "Mover para…", escolhendo uma página da raiz pelo título (na raiz, o
    ...    rótulo é o próprio título; em níveis abaixo ele vem precedido de "— ").
    [Arguments]    ${target_title}
    Click    ${WIKI_CURRENT_NODE_MENU}
    Click    ${WIKI_MOVE}
    Select Options By    ${WIKI_MOVE_TARGET}    label    ${target_title}
    Click    ${WIKI_MOVE_CONFIRM}

Get Wiki Tree Titles
    ${elements}=    Get Elements    ${WIKI_TREE_TITLES}
    ${titles}=    Create List
    FOR    ${element}    IN    @{elements}
        ${text}=    Get Text    ${element}
        Append To List    ${titles}    ${text}
    END
    RETURN    ${titles}

Get Wiki Tree Level
    [Arguments]    ${title}
    ${locator}=    Evaluate    $WIKI_TREE_ITEM_BY_TITLE.replace('{}', $title)
    ${level}=    Get Attribute    ${locator}    aria-level
    RETURN    ${level}

Wait For Wiki Tree Level
    [Arguments]    ${title}    ${level}
    ${locator}=    Evaluate    $WIKI_TREE_ITEM_BY_TITLE.replace('{}', $title)
    Wait For Condition    Attribute    ${locator}    aria-level    ==    ${level}    timeout=30s

Drag Wiki Page Onto
    [Documentation]    Arrasta a página com o mouse, em passos (o CDK só começa a arrastar depois de alguns pixels), e
    ...    solta sobre a linha de destino.
    [Arguments]    ${title}    ${target_title}
    ${source}=    Evaluate    $WIKI_PAGE_LINK_BY_TITLE.replace('{}', $title)
    ${target}=    Evaluate    $WIKI_PAGE_LINK_BY_TITLE.replace('{}', $target_title)
    Scroll To Element    ${target}
    ${a}=    Get BoundingBox    ${source}
    ${b}=    Get BoundingBox    ${target}
    Mouse Move    ${{$a['x'] + 20}}    ${{$a['y'] + 10}}
    Mouse Button    down
    Mouse Move    ${{$a['x'] + 20}}    ${{$a['y'] + 2}}    steps=5
    Mouse Move    ${{$b['x'] + 20}}    ${{$b['y'] + 3}}    steps=20
    Mouse Button    up

Rename Current Wiki Page
    [Documentation]    Menu ⋯ da página aberta, "Renomear": troca o nome e salva.
    [Arguments]    ${new_title}
    Click    ${WIKI_CURRENT_NODE_MENU}
    Click    ${WIKI_RENAME}
    Fill Text    ${WIKI_RENAME_TITLE}    ${new_title}
    Click    ${WIKI_RENAME_CONFIRM}

Copy Current Wiki Page
    [Documentation]    Menu ⋯ da página aberta, "Fazer uma cópia".
    Click    ${WIKI_CURRENT_NODE_MENU}
    Click    ${WIKI_COPY}

Archive Current Wiki Page
    [Documentation]    Menu ⋯ da página aberta, "Arquivar", e confirma sem motivo.
    Click    ${WIKI_CURRENT_NODE_MENU}
    Click    ${WIKI_MENU_ARCHIVE}
    Click    ${WIKI_ARCHIVE_CONFIRM}

Wait For Wiki Page Link Gone
    [Arguments]    ${title}
    ${locator}=    Evaluate    $WIKI_PAGE_LINK_BY_TITLE.replace('{}', $title)
    Wait For Elements State    ${locator}    detached    timeout=30s

Start Editing And Type
    [Documentation]    Editar, clicar na área de texto e digitar, sem salvar (#82).
    [Arguments]    ${text}
    Click    ${WIKI_EDIT}
    Wait For Elements State    ${WIKI_EDITOR}    visible    timeout=30s
    Click    ${WIKI_EDITOR}
    Keyboard Input    type    ${text}

Wait For Wiki Draft Saved
    Wait For Elements State    ${WIKI_DRAFT_STATUS}    visible    timeout=30s

Open Wiki Page Accepting Leave
    [Documentation]    Clica na página da árvore e aceita a pergunta de sair com alterações não salvas.
    [Arguments]    ${title}
    ${locator}=    Evaluate    $WIKI_PAGE_LINK_BY_TITLE.replace('{}', $title)
    ${promise}=    Promise To    Wait For Alert    action=accept    timeout=10s
    Click    ${locator}
    Wait For    ${promise}

Open Wiki Page
    [Arguments]    ${title}
    ${locator}=    Evaluate    $WIKI_PAGE_LINK_BY_TITLE.replace('{}', $title)
    Click    ${locator}

Click Wiki Edit
    Click    ${WIKI_EDIT}

Wait For Wiki Draft Restored
    Wait For Elements State    ${WIKI_DRAFT_RESTORED}    visible    timeout=30s

Get Wiki Editor Text
    ${text}=    Get Text    ${WIKI_EDITOR}
    RETURN    ${text}

Click Wiki Save
    Click    ${WIKI_SAVE}

Get Wiki Draft Restored Count
    ${count}=    Get Element Count    ${WIKI_DRAFT_RESTORED}
    RETURN    ${count}

Select First Paragraph
    [Documentation]    Três cliques selecionam o parágrafo inteiro na leitura da página.
    Click With Options    ${WIKI_READ_PARAGRAPH}    left    clickCount=3

Click Comment Selection
    Wait For Elements State    ${WIKI_COMMENT_SELECTION}    visible    timeout=10s
    Click    ${WIKI_COMMENT_SELECTION}

Get Comment Quote Preview Text
    Wait For Elements State    ${COMMENT_QUOTE_PREVIEW}    visible    timeout=10s
    ${text}=    Get Text    ${COMMENT_QUOTE_PREVIEW}
    RETURN    ${text}

Type Comment
    [Arguments]    ${text}
    Click    ${COMMENT_INPUT}
    Keyboard Input    type    ${text}

Choose First Mention
    Wait For Elements State    ${COMMENT_MENTION_OPTION}    visible    timeout=10s
    Click    ${COMMENT_MENTION_OPTION}

Click Comment Send
    Click    ${COMMENT_SEND}

Get First Comment Text
    Wait For Elements State    ${COMMENT_ITEM}    visible    timeout=30s
    ${text}=    Get Text    ${COMMENT_ITEM}
    RETURN    ${text}

Click Wiki Favorite
    Click    ${WIKI_FAVORITE}

Get Wiki Favorite Text
    ${text}=    Get Text    ${WIKI_FAVORITE}
    RETURN    ${text}

Continue Typing Comment
    [Documentation]    Digita no comentário a partir do cursor (depois de escolher a menção).
    [Arguments]    ${text}
    Keyboard Input    type    ${text}

Start Editing
    Click    ${WIKI_EDIT}
    Wait For Elements State    ${WIKI_EDITOR}    visible    timeout=30s
    Click    ${WIKI_EDITOR}

Choose Editor Style
    [Arguments]    ${style}
    [Documentation]    A lista de estilo devolve o foco ao texto, no mesmo ponto.
    Select Options By    ${RT_STYLE}    value    ${style}

Type In Editor
    [Arguments]    ${text}
    Keyboard Input    type    ${text}

Press Editor Key
    [Arguments]    ${key}
    Keyboard Key    press    ${key}

Click Editor Task List
    Click    ${RT_TASK}

Insert First Emoji
    Click    ${RT_EMOJI}
    Click    ${RT_EMOJI_OPTION}

Save Wiki Edit
    [Arguments]    ${summary}
    Fill Text    ${WIKI_SUMMARY}    ${summary}
    Click    ${WIKI_SAVE}

Get Outline Item Count
    Wait For Elements State    ${DOC_OUTLINE}    visible    timeout=30s
    ${count}=    Get Element Count    ${DOC_OUTLINE_ITEM}
    RETURN    ${count}

Get Read Task Item Count
    ${count}=    Get Element Count    ${WIKI_READ_TASK_ITEM}
    RETURN    ${count}

Choose First Page Icon
    Click    ${WIKI_ICON_BUTTON}
    Click    ${WIKI_ICON_OPTION}

Get Page Icon Text
    ${text}=    Get Text    ${WIKI_ICON_BUTTON}
    RETURN    ${text}

Download Wiki Markdown
    [Documentation]    Clica em "Baixar Markdown" e devolve o nome sugerido do arquivo e o texto baixado.
    ${promise}=    Promise To Wait For Download
    Click    ${WIKI_EXPORT_MD}
    ${file}=    Wait For    ${promise}
    ${text}=    Get File    ${file}[saveAs]    encoding=UTF-8
    RETURN    ${file}[suggestedFilename]    ${text}

Wait For Wiki Print Button
    Wait For Elements State    ${WIKI_PRINT}    visible    timeout=30s
