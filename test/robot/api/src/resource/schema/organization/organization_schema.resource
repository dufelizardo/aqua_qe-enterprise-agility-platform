*** Comments ***
    Schemas do módulo organization (SSD, seção 3.2).

*** Variables ***
${ORGANIZATION_LEVEL_SCHEMA}    ${{{'required': ['key', 'name', 'position', 'flightLevel'], 'types': {'key': str, 'name': str, 'position': int, 'flightLevel': str}, 'enums': {'flightLevel': ['FL1', 'FL2', 'FL3', None]}}}}
${ORGANIZATION_UNIT_DETAIL_SCHEMA}    ${{{'required': ['id', 'name', 'level', 'parentId', 'active', 'version', 'path', 'links'], 'types': {'id': str, 'name': str, 'level': dict, 'parentId': str, 'active': bool, 'version': int, 'path': list, 'links': dict}, 'enums': {}}}}
${ORGANIZATION_ERROR_SCHEMA}    ${{{'required': ['status', 'code', 'detail', 'errors'], 'types': {'status': int, 'code': str, 'detail': str, 'errors': list}, 'enums': {}}}}
