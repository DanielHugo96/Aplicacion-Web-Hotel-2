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
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RAIZ / "scripts"))
import preflight  # noqa: E402  (necesita RAIZ ya calculado)

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


def entorno_campana() -> dict[str, str]:
    """Entorno para lanzar el agente, con el JDK que pide el proyecto.

    Tener el JDK 25 instalado no basta: si el PATH de esta sesión apunta al 17,
    `mvnw verify` falla igual. Aquí se busca el JDK correcto entre los instalados
    y se antepone a JAVA_HOME y al PATH del hijo.

    Por sí solo no alcanzaría: el shell que ejecutan las ejecutoras corre en el
    servidor de OpenCode, que es un proceso aparte con su propio entorno. Por eso
    el comando se lanza con `--standalone`, que arranca un servidor privado que sí
    hereda este entorno. Así la campaña usa el JDK correcto sin que nadie tenga que
    cambiar la configuración de su máquina.
    """
    env = os.environ.copy()
    declarada = preflight.version_java_declarada()
    if not declarada:
        return env

    major_pedido = declarada.split(".")[0]
    real = preflight.version_java_real()
    if real and real.split(".")[0] == major_pedido:
        return env  # el PATH ya es correcto, no se toca nada

    jdk = preflight.buscar_jdk(major_pedido)
    if not jdk:
        return env

    log(f"PATH usa Java {real or 'nada'}; la campaña forzará el JDK {declarada} de {jdk}")
    env["JAVA_HOME"] = jdk
    env["PATH"] = os.pathsep.join([str(Path(jdk) / "bin"), env.get("PATH", "")])
    return env


def ruta_opencode() -> str | None:
    """Ruta absoluta del ejecutable de opencode, o None si no se encuentra.

    En Windows lo que hay en el PATH es un shim de npm: `opencode` y
    `opencode.cmd`, que son scripts. CreateProcess no los ejecuta, así que
    `subprocess.run(["opencode", ...])` falla con FileNotFoundError aunque el
    comando funcione bien desde una terminal. Detrás del shim sí hay un
    `opencode.exe` de verdad, en el paquete de npm. Este resolves es el que lo
    encuentra.
    """
    directo = shutil.which("opencode.exe")
    if directo:
        return directo

    candidatos = []
    appdata = os.environ.get("APPDATA")
    if appdata:
        candidatos.append(Path(appdata) / "npm" / "node_modules" / "@opencode" / "cli"
                          / "bin" / "opencode.exe")
    home = Path.home()
    candidatos.append(home / ".opencode" / "bin" / "opencode.exe")
    candidatos.append(home / ".bun" / "bin" / "opencode.exe")

    for c in candidatos:
        if c.exists():
            return str(c)

    # Último recurso: si lo que hay en el PATH es un binario de verdad (Linux,
    # macOS o una instalación con .exe en el PATH), usarlo tal cual.
    en_path = shutil.which("opencode")
    if en_path and not en_path.lower().endswith((".cmd", ".bat", ".ps1")):
        return en_path
    return None


def _comando_opencode(args: list[str]) -> list[str]:
    exe = ruta_opencode()
    if not exe:
        return ["opencode", *args]
    return [exe, *args]


def _py(script: Path) -> list[str]:
    return [sys.executable, str(script)]


def log(mensaje: str) -> None:
    hora = dt.datetime.now().strftime("%H:%M:%S")
    print(f"[{hora}] {mensaje}", flush=True)


def comprobar_precondiciones(saltar: bool) -> bool:
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


def _formatear_evento(evento: dict) -> list:
    """Convierte un evento de `opencode run --format json` en lineas legibles."""
    tipo = evento.get("type")
    parte = evento.get("part") or {}

    if tipo == "reasoning" and parte.get("text"):
        txt = " ".join(str(parte["text"]).split())
        return [f"PIENSA  {txt[:240]}{'...' if len(txt) > 240 else ''}"]

    if tipo == "text" and parte.get("text"):
        txt = " ".join(str(parte["text"]).split())
        return [f"DICE    {txt[:400]}{'...' if len(txt) > 400 else ''}"]

    if tipo == "tool_use":
        nombre = parte.get("tool")
        estado = (parte.get("state") or {}).get("status")
        entrada = (parte.get("state") or {}).get("input") or {}
        if nombre == "subagent":
            agente = entrada.get("agent", "?")
            tarea = " ".join(str(entrada.get("description", "")).split())
            return [f"DELEGA a {agente}: {tarea}"]
        detalle = entrada.get("command") or entrada.get("filePath") or entrada.get("pattern") or ""
        return [f"CMD     {nombre} {str(detalle)[:150]}  [{estado}]"]

    return []


def _seguir(cmd, timeout: int, entorno: dict) -> int:
    """Ejecuta opencode en modo json y va imprimiendo los eventos en claro.

    Sin esto la terminal muestra lo que dice el orquestador pero no lo que
    piensa. Y de las ejecutoras solo se ve la respuesta final: cada una corre en
    su propia sesion hija y su razonamiento no llega al flujo del padre.

    `opencode run` en modo texto no da mas de eso. Con `--format json` llega
    una linea por evento, y esas si se pueden filtrar.
    """
    proceso = subprocess.Popen(cmd, cwd=RAIZ, env=entorno,
                               stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                               text=True, encoding="utf-8", errors="replace",
                               bufsize=1)
    inicio = time.monotonic()
    try:
        for linea in proceso.stdout:
            linea = linea.strip()
            if not linea:
                continue
            transcurrido = int(time.monotonic() - inicio)
            reloj = f"[{transcurrido // 60:>3}m {transcurrido % 60:>2}s]"
            if not linea.startswith("{"):
                print(f"{reloj} {linea}", flush=True)
                continue
            try:
                evento = json.loads(linea)
            except json.JSONDecodeError:
                continue
            for trozo in _formatear_evento(evento):
                print(f"{reloj} {trozo}", flush=True)
        proceso.wait()
        return proceso.returncode
    except KeyboardInterrupt:
        print()
        log("Interrumpido con Ctrl+C. El plan queda en curso y se puede retomar.")
        proceso.kill()
        return 130


def ejecutar_plan(plan: str, timeout: int, seco: bool, modelo: str,
                   seguir: bool = False) -> bool:
    """Devuelve True si el plan quedó CERRADO."""
    if seco:
        log(f"[--seco] opencode run --agent {AGENTE} --model {modelo} "
            f"\"Ejecuta y cierra el plan {plan}.\"")
        return True

    log(f"Lanzando {AGENTE} para {plan} (modelo {modelo}, máximo {timeout // 3600} h)...")
    cmd = _comando_opencode([
        "run",
        "--auto",
        # Servidor privado: es lo que hace que JAVA_HOME y el PATH corregidos
        # lleguen al shell que usan las ejecutoras.
        "--standalone",
        "--agent", AGENTE,
        "--model", modelo,
        "--title", f"orquestacion:{plan}",
        f"Ejecuta y cierra el plan {plan}.",
    ])
    entorno = entorno_campana()
    inicio = time.monotonic()
    if seguir:
        codigo = _seguir(cmd, timeout, entorno)
        transcurrido = int(time.monotonic() - inicio)
        log(f"{plan} terminó en {transcurrido // 60} min {transcurrido % 60} s (código {codigo}).")
        if codigo == 0 and "Estado:    cerrado" in subprocess.run(
                _py(PROXIMO) + ["--explain", plan], cwd=RAIZ,
                capture_output=True, text=True).stdout:
            log(f"{plan}: CERRADO.")
            return True
        log(f"{plan}: NO quedó cerrado. Revisa docs/implementacion/decisiones/ antes de relanzar.")
        return False
    try:
        res = subprocess.run(cmd, cwd=RAIZ, timeout=timeout, env=entorno)
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
    ap.add_argument("--seguir", action="store_true",
                    help="muestra en vivo lo que piensa el orquestador y a quién delega")
    ap.add_argument("--sin-preflight", action="store_true", help="salta las precondiciones (depuración)")
    ap.add_argument("--sin-remoto", action="store_true", help="no consulta las ramas de origin")
    args = ap.parse_args()

    if not comprobar_precondiciones(args.sin_preflight):
        return 1

    ejecutados = 0
    while True:
        plan = args.plan or siguiente_plan(args.sin_remoto)

        if not plan:
            log("No queda ningún plan listo. Campaña terminada.")
            return 0

        log(f"Plan de este turno: {plan}")

        if not ejecutar_plan(plan, args.timeout, args.seco, args.modelo, args.seguir):
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