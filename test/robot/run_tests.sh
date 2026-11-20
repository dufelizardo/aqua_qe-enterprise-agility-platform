#!/usr/bin/env bash
# Roda as suítes Robot Framework e grava os relatórios em test/robot/results/.
#   ./run_tests.sh              API e interface
#   ./run_tests.sh api          só API
#   ./run_tests.sh ui           só interface (exige `rfbrowser init chromium` uma vez)
#   ./run_tests.sh api --dryrun só checagem de sintaxe e imports
# Endereços por variável de ambiente: AQUA_HOST_URL, AQUA_AUTH_URL, AQUA_UI_URL (padrão: docker compose local).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"

suites=()
case "${1:-all}" in
  api) suites=("$here/api/test"); shift ;;
  ui) suites=("$here/ui/test"); shift ;;
  all) suites=("$here/api/test" "$here/ui/test"); [[ $# -gt 0 ]] && shift ;;
  *) suites=("$here/api/test" "$here/ui/test") ;;
esac

robot --outputdir "$here/results" --name "AQuA-QE" "$@" "${suites[@]}"
