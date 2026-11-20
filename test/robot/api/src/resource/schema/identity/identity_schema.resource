*** Comments ***
    Schemas do módulo identity. GET /api/v1/me segue o SSD, seção 3.1.

*** Variables ***
${IDENTITY_ME_SCHEMA}    ${{{'required': ['id', 'username', 'name', 'email', 'assignments'], 'types': {'id': str, 'username': str, 'name': str, 'email': str, 'assignments': list}, 'enums': {}}}}
