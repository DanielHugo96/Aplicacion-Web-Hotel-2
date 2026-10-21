#!/usr/bin/env bash
# Ejecuta la matriz completa en el orden de los pasos del plan, sobre una demo recién reseteada.
#
# Orden obligado por los propios casos:
#   preflight → rep03 (demo sin ventas) → fixtures F1-F3/F8 → rep01 (77.00 exactos) →
#   F4-F6/F9 → SEC → LP/CRUD → OCC/CLN → STK → PAY/OUT → MSG → NOT → rep02 (corrida
#   completa) → rep03-sin-bootstrap → informe
#
# REP02 levanta una instancia de reporting aislada (sin Eureka, puerto 8096, base
# `reporting_rebuild_db`) y la retira al terminar. REP03-sin-bootstrap hace lo mismo en
# 8097 con `reporting_sin_bootstrap_db` para medir el 503 REPORTING_NOT_READY.
set -euo pipefail

raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$raiz"

mkdir -p tests/evidencia/qa-matriz-aceptacion/_runtime
log="tests/evidencia/qa-matriz-aceptacion/_runtime/corrida.txt"
: > "$log"

paso() {
  local nombre="$1"
  echo "===== $(date -u +%Y-%m-%dT%H:%M:%SZ) $nombre =====" | tee -a "$log"
  if ! python scripts/qa/matriz.py "$nombre" 2>&1 | tee -a "$log"; then
    echo "FALLO en $nombre" | tee -a "$log"
    return 1
  fi
}

paso preflight
paso rep03
paso fixtures
paso rep01
paso fixtures-avanzados
paso sec
paso lp-crud
paso occ-cln
paso stk
paso pay-out
paso msg
paso not
paso rep02
paso rep03-sin-bootstrap
paso informe

echo "matriz completa" | tee -a "$log"
