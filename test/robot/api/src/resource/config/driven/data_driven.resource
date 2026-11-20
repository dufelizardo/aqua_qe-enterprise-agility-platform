*** Settings ***
Documentation       Agregador único de dados de teste: todo TEST importa este arquivo.
# IDENTITY
Resource            ../../data_driven/identity/identity_driven.resource
# ORGANIZATION
Resource            ../../data_driven/organization/organization_driven.resource
# WORKITEM
Resource            ../../data_driven/workitem/workitem_driven.resource
