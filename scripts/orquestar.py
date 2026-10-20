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

Si el modelo principal se queda sin cuota o sin créditos, el plan se relanza con el
modelo de respaldo (MiniMax M3.1 Flash Free) y la campaña sigue con él.
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import queue
import shutil
import subprocess
import sys
import threading
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
# Los subagentes heredan el modelo de la sesión, así que las ejecutoras trabajan
# también en este nivel. Los `model:` de los .opencode/agents/ se dejan escritos:
# son la declaración de intención y valen en cuanto la sesión no imponga modelo.
#
# DeepSeek V4.1 Flash (OpenCode Go) admite variantes de esfuerzo (low/high/max).
# No se fija ninguna: se usa la variante por defecto. Si se quiere fijar, se
# escribe aquí con el sufijo `#high` (o el que sea) y en el frontmatter de los
# agentes. Los subagentes heredan el modelo de esta sesión.
MODELO = "opencode-go/deepseek-v4.1-flash"

# Modelo de respaldo: el mismo MiniMax M3.1 Flash Free que usa el agente
# `ejecutor` de .opencode/agents/ejecutor.md. Entra cuando el principal se
# queda sin cuota o sin créditos.
#
# Relanzar la campaña con este modelo pone a TODO el equipo en MiniMax, porque
# los subagentes heredan el modelo de la sesión: no solo el orquestador, sino
# también las ejecutoras que llama.
#
# La variante de esfuerzo es `high` a propósito. Con `low` el modelo se pierde
# en planes largos; con `xhigh` o `max` el servicio gratuito se satura y la
# campaña se para por el otro extremo. `high` es el punto en el que todavía
# razona lo suficiente para seguir pasos en orden y verificar un build.
MODELO_RESPALDO = "opencode-go/minimax-m3.1-flash-free#high"

# Marcas que delatan un modelo sin cuota. La salida de `opencode run` es texto
# libre, así que se busca por palabras: "quota", "insufficient credits", "429".
# Un acierto falso solo cuesta un relanzamiento con el modelo de respaldo; un
# acierto omitido deja la campaña parada hasta que alguien intervenga.
MARCAS_CUOTA = (
    "quota",
    "insufficient",
    "credit",
    "429",
    "rate limit",
    "rate_limit",
    "too many requests",
    "payment required",
    "billing",
)


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


def _arrancar(cmd, entorno: dict) -> subprocess.Popen:
    """Arranca `opencode run` con su salida capturada."""
    return subprocess.Popen(cmd, cwd=RAIZ, env=entorno,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            text=True, encoding="utf-8", errors="replace",
                            bufsize=1)


def _bucle(proceso, timeout: int, eventos: bool) -> tuple[int, str]:
    """Lee la salida del proceso, la va enseñando y devuelve (código, salida).

    La lectura va en un hilo aparte porque `readline()` se queda bloqueada
    cuando el proceso no escribe nada, y el timeout tiene que seguir contando
    mientras tanto. La salida se acumula para poder buscar marcas de cuota.
    """
    cola: queue.Queue = queue.Queue()

    def _lector() -> None:
        for linea in proceso.stdout:
            cola.put(linea)
        cola.put(None)

    threading.Thread(target=_lector, daemon=True).start()

    inicio = time.monotonic()
    salida: list[str] = []

    def _volcar(linea: str) -> None:
        if not eventos:
            print(linea, flush=True)
            return
        transcurrido = int(time.monotonic() - inicio)
        reloj = f"[{transcurrido // 60:>3}m {transcurrido % 60:>2}s]"
        if linea.startswith("{"):
            try:
                evento = json.loads(linea)
            except json.JSONDecodeError:
                evento = None
            if evento is not None:
                for trozo in _formatear_evento(evento):
                    print(f"{reloj} {trozo}", flush=True)
                return
        print(f"{reloj} {linea}", flush=True)

    try:
        while True:
            if time.monotonic() - inicio > timeout:
                proceso.kill()
                return 124, "\n".join(salida)
            try:
                linea = cola.get(timeout=1.0)
            except queue.Empty:
                if proceso.poll() is not None:
                    break
                continue
            if linea is None:
                break
            texto = linea.rstrip("\n")
            if not texto:
                continue
            salida.append(texto)
            if len(salida) > 5000:
                del salida[:2500]
            _volcar(texto)
    except KeyboardInterrupt:
        print()
        log("Interrumpido con Ctrl+C. El plan queda en curso y se puede retomar.")
        proceso.kill()
        return 130, "\n".join(salida)

    proceso.wait()
    return proceso.returncode, "\n".join(salida)


def _seguir(cmd, timeout: int, entorno: dict) -> tuple[int, str]:
    """Ejecuta opencode y va imprimiendo los eventos en claro.

    Sin esto la terminal muestra lo que dice el orquestador pero no lo que
    piensa. Y de las ejecutoras solo se ve la respuesta final: cada una corre en
    su propia sesion hija y su razonamiento no llega al flujo del padre.

    `opencode run` en modo texto no da mas de eso. Con `--format json` llega
    una linea por evento, y esas si se pueden filtrar.
    """
    proceso = _arrancar(cmd, entorno)
    return _bucle(proceso, timeout, eventos=True)


def _es_fallo_de_cuota(salida: str) -> bool:
    """True si la salida delata que el modelo se quedó sin cuota o sin créditos."""
    bajo = salida.lower()
    return any(marca in bajo for marca in MARCAS_CUOTA)


def ejecutar_plan(plan: str, timeout: int, seco: bool, modelo: str,
                   seguir: bool = False, modelo_respaldo: str | None = None,
                   ) -> tuple[bool, str]:
    """Ejecuta un plan y devuelve (quedó cerrado, modelo con el que se corrió).

    Si el modelo se queda sin cuota o sin créditos y hay modelo de respaldo, el
    plan se relanza con ese y se devuelve el modelo que acabó usándose.
    """
    if seco:
        log(f"[--seco] opencode run --agent {AGENTE} --model {modelo} "
            f"\"Ejecuta y cierra el plan {plan}.\"")
        if modelo_respaldo:
            log(f"[--seco] (respaldo: --model {modelo_respaldo} "
                f"si {modelo} se queda sin cuota)")
        return True, modelo

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
    try:
        proceso = _arrancar(cmd, entorno)
    except FileNotFoundError:
        log("No se encuentra el ejecutable 'opencode' en el PATH.")
        return False, modelo

    codigo, salida = _bucle(proceso, timeout, eventos=seguir)
    transcurrido = int(time.monotonic() - inicio)
    log(f"{plan} terminó en {transcurrido // 60} min {transcurrido % 60} s (código {codigo}).")

    if codigo == 124:
        log(f"TIMEOUT: {plan} superó las {timeout // 3600} h. Se para aquí; se puede reanudar.")
        return False, modelo

    if codigo != 0:
        if modelo_respaldo and _es_fallo_de_cuota(salida):
            log(f"El modelo {modelo} no tiene cuota ni créditos. "
                f"Se relanza {plan} con {modelo_respaldo}.")
            return ejecutar_plan(plan, timeout, seco, modelo_respaldo, seguir, None)
        log(f"{plan} terminó con error. La campaña se detiene; revisa la sesión antes de seguir.")
        return False, modelo

    # El orquestador responde en su última línea. Comprobamos el marcador.
    estado = subprocess.run(
        _py(PROXIMO) + ["--explain", plan],
        cwd=RAIZ, capture_output=True, text=True,
    )
    if "Estado:    cerrado" in estado.stdout:
        log(f"{plan}: CERRADO.")
        return True, modelo

    log(f"{plan}: NO quedó cerrado. Quedó en curso o bloqueado; revisa "
        f"docs/implementacion/decisiones/ antes de relanzar.")
    return False, modelo


def main() -> int:
    ap = argparse.ArgumentParser(description="Bucle exterior de la campaña de planes.")
    ap.add_argument("plan", nargs="?", help="plan concreto a ejecutar")
    ap.add_argument("--todos", action="store_true", help="sigue plan tras plan hasta que no quede ninguno")
    ap.add_argument("--max", type=int, default=0, metavar="N", help="tope de planes en esta invocación")
    ap.add_argument("--timeout", type=int, default=TIMEOUT_POR_PLAN, metavar="SEG", help="segundos por plan")
    ap.add_argument("--modelo", default=MODELO, help="modelo de la campaña (proveedor/modelo#variante)")
    ap.add_argument("--modelo-respaldo", default=MODELO_RESPALDO,
                    help="modelo al que se cae cuando el principal se queda sin cuota")
    ap.add_argument("--sin-respaldo", action="store_true",
                    help="no relanza con el modelo de respaldo si el principal falla por cuota")
    ap.add_argument("--seco", action="store_true", help="no lanza nada, solo enseña qué haría")
    ap.add_argument("--seguir", action="store_true",
                    help="muestra en vivo lo que piensa el orquestador y a quién delega")
    ap.add_argument("--sin-preflight", action="store_true", help="salta las precondiciones (depuración)")
    ap.add_argument("--sin-remoto", action="store_true", help="no consulta las ramas de origin")
    args = ap.parse_args()

    if not comprobar_precondiciones(args.sin_preflight):
        return 1

    ejecutados = 0
    # El modelo con el que se sigue la campaña. Si el principal se queda sin
    # cuota, el plan se relanza con el de respaldo y la campaña continúa con él.
    modelo = args.modelo
    while True:
        plan = args.plan or siguiente_plan(args.sin_remoto)

        if not plan:
            log("No queda ningún plan listo. Campaña terminada.")
            return 0

        log(f"Plan de este turno: {plan}")

        cerrado, modelo = ejecutar_plan(
            plan, args.timeout, args.seco, modelo, args.seguir,
            None if args.sin_respaldo else args.modelo_respaldo,
        )
        if not cerrado:
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