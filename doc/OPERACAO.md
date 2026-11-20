# Operação — ambientes, pipeline e servidor

| | |
|---|---|
| **Versão** | 1.0 |
| **Data** | 2026-11-20 |
| **Status** | Rascunho para revisão |
| **Decisões** | [SAD, ADR-15 e ADR-16](SAD.md#10-decisões-de-arquitetura) |

Como os ambientes do AQuA-QE são publicados e o que precisa ser feito uma única vez no GitHub e no servidor. O desenho reaproveita a infraestrutura do Mais Saúde Pública: o mesmo servidor `projetos-server`, com K3s, MetalLB, Traefik, ArgoCD Core e nomes `.local` anunciados por mDNS.

---

## 1. Ambientes

| Ambiente | Branch | Endereço | Namespace | Acesso | Situação |
|---|---|---|---|---|---|
| Desenvolvimento | `developer` | http://aquaqe-dev.local | `aqua-qe-agility-platform-dev` | Sem senha: entra como persona e troca pelo menu do avatar (ADR-17) | No ar desde 2026-11-20 |
| Produção | `main` | http://aquaqe.local | `aqua-qe-agility-platform-prod` | Login pelo Keycloak, realm `aquaqe-prod`, com "Lembrar-me" de 30 dias | Adiado: falta memória no servidor |
| Identidade | `main` | http://aquaqe-auth.local | `aqua-qe-agility-platform-auth` | Console do Keycloak em `/admin` | Adiado, junto com a produção |

Os endereços abrem em qualquer aparelho conectado à rede de casa, sem configurar nada no aparelho: o servidor anuncia os nomes por mDNS ([seção 2](#2-nomes-na-rede-mdns)). Fora da rede, eles não são acessíveis.

O Keycloak atende só a produção. O dev roda sem senha (ADR-17): a API age como uma das personas de teste, e a trava da API impede esse modo em produção. Os testes de login do CI usam o Keycloak do `compose.yaml`, com o realm `aquaqe-dev`.

## 2. Nomes na rede (mDNS)

### 2.1 Como funciona

```text
aparelho no Wi-Fi ── "quem é aquaqe-dev.local?" (multicast 224.0.0.251:5353) ──▶ servidor
                  ◀─ "192.168.0.200" ─────────────────────── pod mdns do namespace aqua-qe-agility-platform-dev
aparelho ── http://aquaqe-dev.local ──▶ 192.168.0.200 (Traefik, MetalLB) ── Ingress pelo host ──▶ frontend
```

Cada namespace tem um pod `mdns` (componente [k8s/components/mdns](../k8s/components/mdns/)) que responde por um único nome. O script é o mesmo do Mais Saúde Pública (ADR-0169): só biblioteca padrão do Python, sem Avahi no servidor, sem privilégios e com `hostNetwork`, porque o mDNS só circula na rede física. Os anunciadores dos dois projetos dividem a porta 5353 do servidor (`SO_REUSEPORT`) e cada um responde só pelo próprio nome, então não há conflito.

### 2.2 Configuração

Tudo fica no ConfigMap `mdns-config` de cada ambiente:

| Ambiente | Arquivo | `MDNS_NOME` | `MDNS_IP` |
|---|---|---|---|
| dev | [k8s/apps/overlays/dev/kustomization.yaml](../k8s/apps/overlays/dev/kustomization.yaml) | `aquaqe-dev.local` | `192.168.0.200` |
| prod | [k8s/apps/overlays/prod/kustomization.yaml](../k8s/apps/overlays/prod/kustomization.yaml) | `aquaqe.local` | `192.168.0.200` |
| auth | [k8s/auth/kustomization.yaml](../k8s/auth/kustomization.yaml) | `aquaqe-auth.local` | `192.168.0.200` |

Regras:
- `MDNS_IP` é o IP externo do Traefik no MetalLB (pool `192.168.0.200-210`), não o IP do servidor. Confira com `kubectl -n kube-system get svc traefik`. Se ele mudar, troque nos três arquivos.
- O nome tem **um único rótulo** antes de `.local` (`aquaqe-dev.local`, nunca `dev.aquaqe.local`): o mDNS não resolve nomes com dois níveis.
- O mesmo nome precisa aparecer em três lugares: `MDNS_NOME`, o host do Ingress (patch do overlay) e, para os ambientes de aplicação, a URL do realm de prod no Keycloak (`AQUAQE_PROD_URL` em [k8s/auth/keycloak.yaml](../k8s/auth/keycloak.yaml)). O nome do Keycloak também está em `KC_HOSTNAME` e no `AUTH_PUBLIC_URL` dos overlays.
- **Na prod, o aparelho precisa resolver dois nomes**: `aquaqe.local` e `aquaqe-auth.local`, porque o login leva o navegador ao Keycloak. Se só um resolver, a página abre mas o login falha. O dev, sem senha, só precisa de `aquaqe-dev.local`.

### 2.3 Verificação

| Onde | Comando | Esperado |
|---|---|---|
| Cluster | `kubectl -n aqua-qe-agility-platform-dev logs deploy/mdns` | `Anunciando aquaqe-dev.local -> 192.168.0.200 pela interface 192.168.0.50` |
| Windows | `Resolve-DnsName aquaqe-dev.local` ou `ping aquaqe-dev.local` | `192.168.0.200` |
| Linux / Mac | `avahi-resolve -n aquaqe-dev.local` / `dns-sd -G v4 aquaqe-dev.local` | `192.168.0.200` |
| Navegador | http://aquaqe-dev.local | Página de login do Keycloak em `aquaqe-auth.local` e, após entrar, Meu trabalho |

Repita para `aquaqe.local` e `aquaqe-auth.local`.

### 2.4 Limites e alternativa

- Só funciona dentro da rede de casa. Acesso de fora exige túnel ou domínio próprio; fica fora deste escopo.
- Android antigo pode não resolver `.local`, e alguns roteadores bloqueiam multicast entre aparelhos (isolamento de clientes no Wi-Fi). O anunciador responde só IPv4.
- Alternativa quando o mDNS não funcionar num aparelho: uma linha no arquivo `hosts` (no Windows, `C:\Windows\System32\drivers\etc\hosts`, editado como administrador):

  ```text
  192.168.0.200  aquaqe-dev.local aquaqe.local aquaqe-auth.local
  ```

## 3. Fluxo de promoção

A promoção é automática: o que chega na `developer` vai para a `main` (prod) sozinho, desde que todos os testes fiquem verdes.

```text
feature/* ──PR──▶ developer ──(pipeline verde)──▶ PR automático ──(aceitação verde)──▶ main
                     │                                                               │
          imagem :developer + SHA                                    imagem :main + SHA, release
                     │                                                               │
              ArgoCD → aqua-qe-agility-platform-dev                                             ArgoCD → aqua-qe-agility-platform-prod
```

| Momento | O que roda |
|---|---|
| Push na `developer` e qualquer PR | `pipeline.yml`: backend (JUnit, Testcontainers, Spring Modulith), frontend (build e testes), Robot `--dryrun`, CodeQL. |
| Push na `developer` | `publish-image.yml`: imagens no ghcr.io e SHA gravado em `k8s/apps/overlays/dev`; o ArgoCD atualiza o dev. |
| Pipeline verde num push da `developer` | `promote.yml`: abre o PR `developer` → `main`, ou reaproveita o que já está aberto. |
| PR `developer` → `main` | `pipeline.yml` com a aceitação Robot (API e interface) contra a pilha do `compose.yaml`; `auto-merge.yml` faz o merge quando `backend`, `frontend`, `robot-dryrun` e `acceptance` passam. Se algum falhar, o PR fica aberto e nada vai para a prod. |
| Push na `main` | `publish-image.yml`: imagens, SHA gravado em `k8s/apps/overlays/prod` e release da versão do `pom.xml`; o ArgoCD atualiza a prod. |

Para segurar uma promoção, feche o PR automático: ele é reaberto no próximo push verde da `developer`.

**Quando algo falha** (ADR-18): passos de rede tentam até 3 vezes; na aceitação, os testes que falham rodam mais uma vez e, se passarem, viram issue com a label `teste instável`; se o job de aceitação inteiro falhar, o `retry-acceptance.yml` o reexecuta até 2 vezes e comenta no PR. Falhas de backend, frontend ou dry-run não são retentadas. Esgotadas as tentativas, o PR fica aberto e nada vai para a prod.

## 4. Configuração única no GitHub

1. **Branch `developer`** criada a partir da `main` e definida como branch padrão de trabalho.
2. **Segredo `ENTERPRISE_AGILITY_PLATAFORM`**: token pessoal fine-grained, só deste repositório, com `Contents` e `Pull requests` em leitura e escrita (sem `Administration`, como o `WOLK_KEY` do LoneWolf). É usado para abrir o PR de promoção, fazer o merge e gravar o SHA nos overlays. Merges e pushes feitos com o token padrão do workflow não disparam outros workflows, e a esteira pararia no meio.
3. **Proteção da `main`** (aplicada em 2026-11-20): exige PR, sem aprovação obrigatória, porque o auto-merge mescla sozinho; exige os checks `backend`, `frontend`, `robot-dryrun` e `acceptance`; bloqueia force push e exclusão. `enforce_admins` fica **desligado**, senão o commit de pin feito com o PAT é recusado (lição da ADR-0015 do Mais Saúde Pública).
4. **Pacotes do ghcr.io públicos**: depois do primeiro `publish-image.yml`, em *Packages*, tornar `aqua-qe-platform` e `aqua-qe-platform-frontend` públicos. Pacotes novos nascem privados, e o K3s não consegue baixar a imagem sem credencial.

## 5. Configuração única no servidor

Executar no `projetos-server` (ou em qualquer máquina com `kubectl` apontando para o cluster). As senhas são exemplos de formato; gere as suas.

```bash
# Namespaces
kubectl create namespace aqua-qe-agility-platform-auth
kubectl create namespace aqua-qe-agility-platform-dev
kubectl create namespace aqua-qe-agility-platform-prod

# Identidade (Keycloak da prod)
kubectl -n aqua-qe-agility-platform-auth create secret generic keycloak-db --from-literal=password="$(openssl rand -hex 16)"
kubectl -n aqua-qe-agility-platform-auth create secret generic keycloak-admin \
  --from-literal=username=admin --from-literal=password="<senha do console do Keycloak>"
PROD_WEB_SECRET="$(openssl rand -hex 24)"
kubectl -n aqua-qe-agility-platform-auth create secret generic keycloak-realms --from-literal=prod-web-secret="$PROD_WEB_SECRET"

# Ambientes. O dev, sem senha, só precisa do banco; a prod usa o mesmo segredo de cliente do realm.
kubectl -n aqua-qe-agility-platform-dev create secret generic db-credentials --from-literal=password="$(openssl rand -hex 16)"
kubectl -n aqua-qe-agility-platform-prod create secret generic db-credentials --from-literal=password="$(openssl rand -hex 16)"
kubectl -n aqua-qe-agility-platform-prod create secret generic auth-client --from-literal=secret="$PROD_WEB_SECRET"

# Aplicações do ArgoCD (dev acompanha a developer; prod e auth, a main)
kubectl apply -f k8s/argocd/            # todas; ou só uma, ex.: kubectl apply -f k8s/argocd/dev.yaml
```

**Primeiro acesso à produção.** O realm `aquaqe-prod` nasce sem usuários. Entre em http://aquaqe-auth.local/admin com o usuário do segredo `keycloak-admin`, escolha o realm `aquaqe-prod` e crie os usuários, com nome, sobrenome e e-mail preenchidos. Antes do primeiro login, indique o administrador da plataforma no overlay de prod (`AQUAQE_ADMIN_USERNAME=<usuário do Keycloak>` no `app-config`): ele recebe `PLATFORM_ADMIN` na unidade raiz no primeiro acesso e, a partir daí, cria a estrutura organizacional e atribui os demais papéis.

**Senha do banco já criada.** O PostgreSQL só lê `POSTGRES_PASSWORD` na primeira subida. Trocar o segredo depois disso não troca a senha do banco.

## 6. Recursos no servidor

Pedidos de memória (requests) por componente, para conferir com a folga do servidor (6,7 GB reais, compartilhados com o Mais Saúde Pública):

| Namespace | Componentes | Memória pedida | Limite |
|---|---|---|---|
| `aqua-qe-agility-platform-auth` | Keycloak, PostgreSQL, mDNS | ~656 Mi | ~1,2 Gi |
| `aqua-qe-agility-platform-dev` | API, PostgreSQL, frontend, mDNS | ~560 Mi | ~1 Gi |
| `aqua-qe-agility-platform-prod` | API, PostgreSQL, frontend, mDNS | ~560 Mi | ~1 Gi |
| **Total** | | **~1,8 Gi** | **~3,2 Gi** |

Antes de aplicar, confira a folga atual com `free -h` e `kubectl top pods -A`. Se faltar memória, a primeira alavanca é desligar o dev quando não estiver em uso (`kubectl -n aqua-qe-agility-platform-dev scale deploy --all --replicas=0`), porque o ArgoCD com `selfHeal` religa; nesse caso, pause a sincronização da aplicação `aqua-qe-agility-platform-dev` antes.

## 7. Desenvolvimento local

```bash
docker compose up -d --build     # PostgreSQL, Keycloak, API e frontend
# http://localhost:4200  (usuários robot.<persona>, senha Robot#Teste123)
# http://localhost:8180/admin  (console do Keycloak: admin / admin)

docker compose up -d postgres keycloak   # só a infraestrutura
./mvnw spring-boot:run                    # API em http://localhost:8080
cd frontend && npx ng serve               # front em http://localhost:4200, com proxy para a API

bash test/robot/run_tests.sh api          # aceitação de API (pip install -r test/robot/requirements.txt)
bash test/robot/run_tests.sh ui           # interface (rfbrowser init chromium uma vez)
```

A primeira subida do Keycloak leva alguns minutos. Os testes unitários do frontend (`ng test`) rodam no CI e em Linux; nesta máquina Windows o Vitest não inicia o processo de teste a tempo.
