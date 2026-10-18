#!/usr/bin/env python3
"""Bucle exterior de la campaña automática de planes.

Un `opencode run` por plan, no una sesión para los veinte. Esa es la decisión que
hace la campaña reanudable: si se corta por cuota, por un fallo o porque alguien
la para, el siguiente `python scripts/orquestar.py` retoma exactamente donde se
quedó, sin repetir nada.

El bucle no decide el orden. Eso lo hace `scripts/proximo-plan.py`, leyendo el
`Estado` y el `Depende de` de los planes. Aquí solo se orquesta lo que ya está
calculado.

Antes de cada plan corre `scripts/preflight.py`. Si una precondición no se
cumple, la campaña para. Ninguna de ellas se arregla desde un agente.

Uso:
    python scripts/orquestar.py                  # ejecuta el siguiente plan listo
    python scripts/orquestar.py <plan>           # ejecuta un plan concreto
    python scripts/orquestar.py --todos          # sigue plan tras plan hasta terminar
    python scripts/orquestar.py --todos --max 3  # como --todos, con tope de 3 planes
    python scripts/orquestar.py --seco           # no lanza nada, solo enseña qué haría
    python scripts/orquestar.py --sin-preflight  # solo para depurar
"""

from __future__ import annotations

import argparse
import datetime as dt
import subprocess
import sys
import time
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
PROXIMO = RAIZ / "scripts" / "proximo-plan.py"
PREFLIGHT = RAIZ / "scripts" / "preflight.py"

# Techo de seguridad por plan. Un plan grande (19 pasos con build de 9 módulos
# Maven y pruebas Angular) puede tardar horas; seis es amplio para el más lento
# que hay y existe para que un agente en bucle no se quede colgado para siempre.
TIMEOUT_POR_PLAN = 6 * 3600

AGENTE = "orquestador"

# El modelo se pasa por flag, no se deja al agente. Verificado en opencode
# v2.0.32: el campo `model:` del frontmatter de un agente y el bloque `agents`
# de opencode.jsonc NO se aplican cuando la sesión tiene su propio modelo, que es
# lo que pasa con `opencode run` sin `--model`. Con `--model` sí se respeta, y el
# agente conserva su identidad y sus permisos.
#
# Los subagentes heredan el modelo de la sesión, así que las ejecutoras y el
# revisor trabajan también en este nivel. Los `model:` de los .opencode/agents/
# se dejan escritos: son la declaración de intención y valen en cuanto la sesión
# no imponga modelo.
MODELO = "opencode/space-bunny-free#xhigh"


def _py(script: Path) -> list[str]:
    return [sys.executable, str(script)]


def log(mensaje: str) -> None:
    hora = dt.datetime.now().strftime("%H:%M:%S")
    print(f"[{hora}] {mensaje}", flush=True)


def preflight(saltar: bool) -> bool:
    if saltar:
        log("preflight saltado por --sin-preflight (solo depuración)")
        return True
    log("Comprobando precondiciones...")
    res = subprocess.run(_py(PREFLIGHT) + ["--aviso"], cwd=RAIZ)
    if res.returncode != 0:
        log("PRECONDICIONES SIN CUMPLIR. La campaña no arranca. Ninguna se arregla desde un agente.")
        return False
    return True


def siguiente_plan(sin_remoto: bool) -> str | None:
    args = _py(PROXIMO)
    if sin_remoto:
        args.append("--sin-remoto")
    res = subprocess.run(args, cwd=RAIZ, capture_output=True, text=True)
    if res.returncode != 0:
        log(f"proximo-plan.py falló:\n{res.stderr.strip()}")
        return None
    plan = res.stdout.strip()
    return plan or None


def ejecutar_plan(plan: str, timeout: int, seco: bool, modelo: str) -> bool:
    """Devuelve True si el plan quedó CERRADO."""
    if seco:
        log(f"[--seco] opencode run --agent {AGENTE} --model {modelo} "
            f"\"Ejecuta y cierra el plan {plan}.\"")
        return True

    log(f"Lanzando {AGENTE} para {plan} (modelo {modelo}, máximo {timeout // 3600} h)...")
    cmd = [
        "opencode", "run",
        "--auto",
        "--agent", AGENTE,
        "--model", modelo,
        "--title", f"orquestacion:{plan}",
        f"Ejecuta y cierra el plan {plan}.",
    ]
    inicio = time.monotonic()
    try:
        res = subprocess.run(cmd, cwd=RAIZ, timeout=timeout)
        codigo = res.returncode
    except subprocess.TimeoutExpired:
        log(f"TIMEOUT: {plan} superó las {timeout // 3600} h. Se para aquí; se puede reanudar.")
        return False
    except FileNotFoundError:
        log("No se encuentra el ejecutable 'opencode' en el PATH.")
        return False

    transcurrido = int(time.monotonic() - inicio)
    log(f"{plan} terminó en {transcurrido // 60} min {transcurrido % 60} s (código {codigo}).")

    if codigo != 0:
        log(f"{plan} terminó con error. La campaña se detiene; revisa la sesión antes de seguir.")
        return False

    # El orquestador responde en su última línea. Comprobamos el marcador.
    estado = subprocess.run(
        _py(PROXIMO) + ["--explain", plan],
        cwd=RAIZ, capture_output=True, text=True,
    )
    if "Estado:    cerrado" in estado.stdout:
        log(f"{plan}: CERRADO.")
        return True

    log(f"{plan}: NO quedó cerrado. Quedó en curso o bloqueado; revisa "
        f"docs/implementacion/decisiones/ antes de relanzar.")
    return False


def main() -> int:
    ap = argparse.ArgumentParser(description="Bucle exterior de la campaña de planes.")
    ap.add_argument("plan", nargs="?", help="plan concreto a ejecutar")
    ap.add_argument("--todos", action="store_true", help="sigue plan tras plan hasta que no quede ninguno")
    ap.add_argument("--max", type=int, default=0, metavar="N", help="tope de planes en esta invocación")
    ap.add_argument("--timeout", type=int, default=TIMEOUT_POR_PLAN, metavar="SEG", help="segundos por plan")
    ap.add_argument("--modelo", default=MODELO, help="modelo de la campaña (proveedor/modelo#variante)")
    ap.add_argument("--seco", action="store_true", help="no lanza nada, solo enseña qué haría")
    ap.add_argument("--sin-preflight", action="store_true", help="salta las precondiciones (depuración)")
    ap.add_argument("--sin-remoto", action="store_true", help="no consulta las ramas de origin")
    args = ap.parse_args()

    if not preflight(args.sin_preflight):
        return 1

    ejecutados = 0
    while True:
        plan = args.plan or siguiente_plan(args.sin_remoto)

        if not plan:
            log("No queda ningún plan listo. Campaña terminada.")
            return 0

        log(f"Plan de este turno: {plan}")

        if not ejecutar_plan(plan, args.timeout, args.seco, args.modelo):
            return 2

        ejecutados += 1
        if args.plan:
            return 0
        if not args.todos:
            return 0
        if args.max and ejecutados >= args.max:
            log(f"Tope de {args.max} planes alcanzado. Relanza para seguir.")
            return 0


if __name__ == "__main__":
    sys.exit(main())