#!/usr/bin/env bash
# Runner de la matriz de aceptación F5 (docs/plans/qa-matriz-aceptacion.md).
#
# Uso:
#   scripts/ejecutar-matriz.sh --help
#   scripts/ejecutar-matriz.sh preflight     # plataforma sana antes de sembrar
#   scripts/ejecutar-matriz.sh fixtures      # F1-F3 y F8 por las rutas de la spec
#   scripts/ejecutar-matriz.sh fixtures-avanzados  # F4-F7 y F9
#   scripts/ejecutar-matriz.sh sec           # SEC01-SEC08
#   scripts/ejecutar-matriz.sh lp-crud       # LP01-LP02 y CRUD01
#   scripts/ejecutar-matriz.sh occ-cln       # OCC01-OCC04 y CLN01-CLN03
#   scripts/ejecutar-matriz.sh stk           # STK01-STK07
#   scripts/ejecutar-matriz.sh pay-out       # PAY01-PAY02 y OUT01-OUT06
#   scripts/ejecutar-matriz.sh msg           # MSG01-MSG02
#   scripts/ejecutar-matriz.sh rep           # REP01-REP03
#   scripts/ejecutar-matriz.sh not           # NOT01-NOT03
#   scripts/ejecutar-matriz.sh informe       # tests/evidence/qa-matriz-aceptacion/INFORME.md
#
# Los grupos se ejecutan en el orden de los pasos del plan. Cada caso deja su archivo
# en tests/evidence/qa-matriz-aceptacion/<CASO>.json.
set -euo pipefail

raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$raiz"

grupo="${1:-}"

case "$grupo" in
  ""|-h|--help)
    sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
    exit 0
    ;;
esac

exec python scripts/qa/matriz.py "$@"
