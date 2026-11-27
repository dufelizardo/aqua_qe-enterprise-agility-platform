*** Settings ***
Documentation       Agregador único de dados de teste: todo TEST importa este arquivo.
# IDENTITY
Resource            ../../data_driven/identity/identity_driven.resource
# ORGANIZATION
Resource            ../../data_driven/organization/organization_driven.resource
# WORKITEM
Resource            ../../data_driven/workitem/workitem_driven.resource
# BOARD
Resource            ../../data_driven/board/board_driven.resource
# WORKFLOW
Resource            ../../data_driven/workflow/workflow_driven.resource
# AUDIT
Resource            ../../data_driven/audit/audit_driven.resource
# KNOWLEDGE
Resource            ../../data_driven/knowledge/knowledge_driven.resource
# INÍCIO (HOME)
Resource            ../../data_driven/inicio/inicio_driven.resource
# BUSCA
Resource            ../../data_driven/busca/busca_driven.resource
# GOVERNANCE
Resource            ../../data_driven/governance/governance_driven.resource
# CONFIGURATION
Resource            ../../data_driven/configuration/configuration_driven.resource
