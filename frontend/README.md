# Frontend do AQuA-QE

SPA em Angular 22 com Angular Material (UX/UI, decisão UX-01). Segue a estrutura e os tokens da [especificação de UX/UI](../doc/UX-UI.md).

O login é conduzido pela API (BFF, [SAD, ADR-16](../doc/SAD.md#10-decisões-de-arquitetura)): o SPA não guarda tokens. Sem sessão, `GET /api/v1/me` responde 401 e o SPA leva o usuário a `/oauth2/authorization/keycloak`.

| Tarefa | Comando |
|---|---|
| Desenvolvimento (com a API em `localhost:8080`) | `npx ng serve` → http://localhost:4200 |
| Build de produção | `npx ng build --configuration production` |
| Testes unitários (Vitest) | `npx ng test --watch=false` |

- `proxy.conf.json` repassa `/api`, `/oauth2`, `/login/oauth2` e `/logout` para a API no `ng serve`.
- `nginx.conf.template` faz o mesmo na imagem de produção; o destino vem da variável `BACKEND_URL`.
- Elementos usados nos testes de interface têm `data-testid` ([QA, seção 8](../doc/QA.md#8-requisitos-para-testabilidade)).
