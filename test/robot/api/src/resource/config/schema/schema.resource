*** Settings ***
Documentation       Agregador único de schemas: todo FLOW importa este arquivo, nunca os schemas por módulo diretamente.
# COMMON
Resource            ../../schema/common/common_schema.resource
# IDENTITY
Resource            ../../schema/identity/identity_schema.resource
# ORGANIZATION
Resource            ../../schema/organization/organization_schema.resource
# WORKITEM
Resource            ../../schema/workitem/workitem_schema.resource
