*** Comments ***
    Schemas do módulo workitem e da configuração padrão (SSD 3.3 e 3.4).

*** Variables ***
${WORKITEM_ARTIFACT_TYPE_SCHEMA}    ${{{'required': ['key', 'level', 'name', 'keyPrefix', 'parentRequired', 'parentTypes'], 'types': {'key': str, 'level': str, 'name': str, 'keyPrefix': str, 'parentRequired': bool, 'parentTypes': list}, 'enums': {'level': ['FL1', 'FL2', 'FL3']}}}}
${WORKITEM_ARTIFACT_SCHEMA}    ${{{'required': ['id', 'key', 'type', 'level', 'title', 'parent', 'orgUnit', 'status', 'size', 'customFields', 'childCount', 'archived', 'version', 'createdAt', 'updatedAt'], 'types': {'id': str, 'key': str, 'type': str, 'level': str, 'title': str, 'orgUnit': dict, 'status': dict, 'customFields': dict, 'childCount': int, 'archived': bool, 'version': int}, 'enums': {'level': ['FL1', 'FL2', 'FL3'], 'size': ['XS', 'S', 'M', 'L', 'XL', None]}}}}
