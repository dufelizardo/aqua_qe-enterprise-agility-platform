*** Comments ***
    Schemas comuns a todos os módulos.

*** Variables ***
${ACTUATOR_HEALTH_SCHEMA}    ${{{'required': ['status'], 'types': {'status': str}, 'enums': {'status': ['UP']}}}}
