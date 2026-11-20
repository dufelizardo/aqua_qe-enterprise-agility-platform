#!/usr/bin/env bash
# Execução das suítes Robot no CI, com uma reexecução só dos testes que falharam (doc/QA.md, seção 9).
#   - Tudo passou na primeira: sai com 0.
#   - Algo falhou: reexecuta só esses testes (--rerunfailed) e junta os resultados (rebot --merge).
#   - Teste que falhou na primeira e passou na segunda é instável: o nome vai para results/flaky.txt,
#     e o pipeline abre uma issue para ele. O resultado final vale o da reexecução.
# Endereços por variável de ambiente, como em run_tests.sh.
set -uo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
out="$here/results"
suites=("$here/api/test" "$here/ui/test")
mkdir -p "$out"
: > "$out/flaky.txt"

robot --outputdir "$out" --output first.xml --log NONE --report NONE --name "AQuA-QE" "$@" "${suites[@]}"
rc=$?
if [ "$rc" -eq 0 ]; then
  rebot --outputdir "$out" --output output.xml --name "AQuA-QE" "$out/first.xml" > /dev/null
  exit 0
fi
# 250+ é erro de execução do Robot (sintaxe, argumentos, nenhum teste): reexecutar não ajuda.
if [ "$rc" -ge 250 ]; then
  echo "Erro de execução do Robot (código $rc); sem reexecução."
  exit "$rc"
fi

echo "::warning::${rc} teste(s) falharam; reexecutando só os que falharam."
robot --outputdir "$out" --output rerun.xml --log NONE --report NONE --name "AQuA-QE" \
  --rerunfailed "$out/first.xml" "$@" "${suites[@]}"
rc2=$?
rebot --outputdir "$out" --output output.xml --name "AQuA-QE" --merge "$out/first.xml" "$out/rerun.xml" > /dev/null
python3 "$here/flaky.py" "$out/first.xml" "$out/rerun.xml" > "$out/flaky.txt"
if [ -s "$out/flaky.txt" ]; then
  echo "::warning::Testes instáveis (falharam e depois passaram):"
  cat "$out/flaky.txt"
fi
exit "$rc2"
