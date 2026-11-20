#!/usr/bin/env bash
# Vigilância de cópias: procura, na busca de código pública do GitHub, os marcadores e as frases
# características deste repositório (.github/copy-watch/fingerprints.txt) em OUTROS repositórios.
#
# Limites, que nenhuma ferramenta contorna:
#   - só encontra cópias públicas no GitHub; repositórios privados e outros serviços são invisíveis;
#   - a busca indexa só a branch padrão e, em geral, não indexa forks.
#
# Variáveis de ambiente:
#   GH_TOKEN                  token para abrir e comentar issues (no Actions: github.token)
#   SEARCH_TOKEN              token para a busca de código; se vazio, usa GH_TOKEN ou o login do gh
#   COPY_WATCH_EXTRA_QUERIES  consultas extras, uma por linha (ex.: segredo do repositório, para
#                             impressões digitais que não devem ficar visíveis neste arquivo)
#   COPY_WATCH_ALLOW          repositórios permitidos, separados por espaço (além deste)
#   CREATE_ISSUE              "true" abre ou atualiza a issue de alerta; caso contrário só imprime
#
# Uso local:  bash scripts/copy-watch.sh
set -euo pipefail

FINGERPRINTS="${1:-.github/copy-watch/fingerprints.txt}"
LABEL="copy-watch"
ISSUE_TITLE="Vigilância de cópias: possíveis cópias encontradas"
REPO="${GITHUB_REPOSITORY:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"
ALLOW=" ${REPO} ${COPY_WATCH_ALLOW:-} "
SEARCH_TOKEN="${SEARCH_TOKEN:-${GH_TOKEN:-}}"

search() {
  if [[ -n "$SEARCH_TOKEN" ]]; then
    GH_TOKEN="$SEARCH_TOKEN" gh api -X GET search/code -f q="$1" -f per_page=100 "${@:2}"
  else
    gh api -X GET search/code -f q="$1" -f per_page=100 "${@:2}"
  fi
}

mapfile -t QUERIES < <(
  { grep -vE '^\s*(#|$)' "$FINGERPRINTS"; printf '%s\n' "${COPY_WATCH_EXTRA_QUERIES:-}"; } |
    sed 's/\r$//' | grep -vE '^\s*$'
)

HITS=""
FAILED=0
for q in "${QUERIES[@]}"; do
  if ! out=$(search "$q" --jq '.items[] | [.repository.full_name, .path, .html_url] | @tsv' 2>&1); then
    echo "::warning::falha na consulta ${q}: ${out}"
    FAILED=$((FAILED + 1))
  else
    while IFS=$'\t' read -r repo path url; do
      [[ -z "$repo" || "$ALLOW" == *" $repo "* ]] && continue
      HITS+="${q}"$'\t'"${repo}"$'\t'"${path}"$'\t'"${url}"$'\n'
    done <<< "$out"
  fi
  sleep 7  # a busca de código aceita cerca de 10 consultas por minuto
done

echo "Consultas: ${#QUERIES[@]}; com falha: ${FAILED}"
if (( FAILED == ${#QUERIES[@]} )); then
  echo "::error::todas as consultas falharam; confira o token de busca (SEARCH_TOKEN)"
  exit 1
fi
if [[ -z "$HITS" ]]; then
  echo "Nenhuma cópia encontrada fora de ${REPO}."
  exit 0
fi

table() {
  echo "| Consulta | Repositório | Arquivo |"
  echo "|---|---|---|"
  while IFS=$'\t' read -r q repo path url; do
    [[ -n "$repo" ]] && echo "| \`${q//|/\\|}\` | [${repo}](https://github.com/${repo}) | [${path}](${url}) |"
  done <<< "$1"
}

echo "Possíveis cópias encontradas:"
table "$HITS"

[[ "${CREATE_ISSUE:-false}" == "true" ]] || exit 0

gh label create "$LABEL" --repo "$REPO" --color B60205 \
  --description "Possível cópia do conteúdo deste repositório" 2>/dev/null || true

ISSUE=$(gh issue list --repo "$REPO" --label "$LABEL" --state open --limit 1 --json number -q '.[0].number // empty')
if [[ -z "$ISSUE" ]]; then
  BODY=$(printf '%s\n\n%s\n\n%s\n' \
    "A vigilância semanal encontrou o conteúdo deste repositório em outros repositórios públicos." \
    "$(table "$HITS")" \
    "Verifique cada caso. Feche a issue quando terminar; novos achados abrem outra.")
  gh issue create --repo "$REPO" --title "$ISSUE_TITLE" --label "$LABEL" --body "$BODY"
  exit 0
fi

# Issue já aberta: comenta só os achados cujo link ainda não aparece nela.
KNOWN=$(gh issue view "$ISSUE" --repo "$REPO" --json body,comments -q '.body, .comments[].body')
NEW=""
while IFS=$'\t' read -r q repo path url; do
  [[ -n "$url" && "$KNOWN" != *"$url"* ]] && NEW+="${q}"$'\t'"${repo}"$'\t'"${path}"$'\t'"${url}"$'\n'
done <<< "$HITS"
if [[ -n "$NEW" ]]; then
  gh issue comment "$ISSUE" --repo "$REPO" --body "$(printf 'Novos achados:\n\n%s\n' "$(table "$NEW")")"
else
  echo "Nenhum achado novo em relação à issue #${ISSUE}."
fi
