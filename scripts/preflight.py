#!/usr/bin/env python3
"""Precondiciones de la campaña automática de planes.

Se ejecuta antes de cada plan. Si algo falla, la campaña no arranca.

Existe por un motivo concreto: el proyecto fija versiones exactas (Java 25, Spring
Boot 4.1.1, Angular 22.2.2...) y el toolchain de la máquina es cosa del humano.
Si el build falla porque falta el JDK correcto, una IA muy lista lo lee como
"el proyecto está mal" y baja `<release>25</release>` a 17 para que su
verificación dé verde. Eso destruye el stack fijado sin que nada avise.

Este script no adivina: comprueba la máquina contra lo que el propio repositorio
declara, y corta antes de que un agente toque nada.

Uso:
    python scripts/preflight.py            # comprueba y sale con código 1 si falla
    python scripts/preflight.py --aviso    # solo imprime, no corta
"""

from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent


class Resultado:
    def __init__(self, nombre: str, ok: bool, detalle: str, bloqueante: bool = True):
        self.nombre = nombre
        self.ok = ok
        self.detalle = detalle
        self.bloqueante = bloqueante


def _cmd(args: list[str], timeout: int = 60) -> tuple[int, str]:
    try:
        res = subprocess.run(args, cwd=RAIZ, capture_output=True, text=True, timeout=timeout)
    except (OSError, subprocess.SubprocessError) as exc:
        return 1, str(exc)
    return res.returncode, (res.stdout or "") + (res.stderr or "")


def _primera_linea(texto: str) -> str:
    for linea in texto.splitlines():
        if linea.strip():
            return linea.strip()
    return ""


# --- Lo que el repositorio declara como fijo -------------------------------

def version_java_declarada() -> str | None:
    """Lee <java.version> de services/pom.xml, la versión fijada de verdad."""
    pom = RAIZ / "services" / "pom.xml"
    if not pom.exists():
        return None
    m = re.search(r"<java\.version>\s*([^<]+?)\s*</java\.version>", pom.read_text(encoding="utf-8"))
    return m.group(1) if m else None


def engines_node_declarados() -> dict[str, str]:
    """Lee engines.node de cada apps/<app>/package.json."""
    engines: dict[str, str] = {}
    apps = RAIZ / "apps"
    if not apps.is_dir():
        return engines
    for pkg in sorted(apps.glob("*/package.json")):
        try:
            datos = json.loads(pkg.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        node = (datos.get("engines") or {}).get("node")
        if node:
            engines[pkg.parent.name] = node
    return engines


def java_instalado() -> str | None:
    code, out = _cmd(["java", "-version"])
    if code != 0:
        return None
    return _primera_linea(out)


def version_java_real() -> str | None:
    linea = java_instalado()
    if not linea:
        return None
    m = re.search(r'version\s+"([0-9._]+)"', linea)
    return m.group(1) if m else None


def docker_listo() -> tuple[bool, str]:
    code, out = _cmd(["docker", "info", "--format", "{{.ServerVersion}}"], timeout=30)
    if code != 0:
        primera = next((l for l in out.splitlines() if l.strip()), "")
        return False, f"el daemon no responde: {primera[:110]}"
    return True, f"Docker {out.strip()}"


def rama_actual() -> str:
    code, out = _cmd(["git", "rev-parse", "--abbrev-ref", "HEAD"])
    return out.strip() if code == 0 else "?"


def arbol_sucio() -> bool:
    code, out = _cmd(["git", "status", "--porcelain"])
    return code != 0 or bool(out.strip())


def version_opencode() -> str | None:
    exe = shutil.which("opencode")
    if not exe:
        return None
    code, out = _cmd([exe, "--version"])
    return out.strip() if code == 0 else None


# --- Las comprobaciones ----------------------------------------------------

def comprobar() -> list[Resultado]:
    res: list[Resultado] = []

    # 1. Java. Bloqueante: es el fallo silencioso que destruye las versiones fijadas.
    declarada = version_java_declarada()
    real = version_java_real()
    if declarada is None:
        res.append(Resultado("java fijada", False, "no se pudo leer <java.version> de services/pom.xml"))
    elif real is None:
        res.append(Resultado("java fijada", False, "no hay java en el PATH"))
    else:
        mayor_real = real.split(".")[0]
        mayor_pedido = declarada.split(".")[0]
        if mayor_real == mayor_pedido:
            res.append(Resultado("java fijada", True, f"Java {real} (fijada: {declarada})"))
        else:
            res.append(Resultado(
                "java fijada", False,
                f"Java {real} instalada pero el proyecto fija {declarada}. "
                f"Instala el JDK {declarada} y apunta JAVA_HOME a él. "
                f"NUNCA cambies services/pom.xml para que el build pase."))

    # 2. Node, contra engines de las apps.
    engines = engines_node_declarados()
    code, out = _cmd(["node", "-v"])
    node = out.strip() if code == 0 else None
    if not engines:
        res.append(Resultado("node", True, "sin engines declarados en apps/", bloqueante=False))
    elif not node:
        res.append(Resultado("node", False, "no hay node en el PATH"))
    elif not _node_cumple(node.lstrip("v"), sorted(set(engines.values()))[0]):
        res.append(Resultado(
            "node", False,
            f"Node {node} no cumple {sorted(set(engines.values()))[0]} "
            f"(apps/{', '.join(engines)})"))
    else:
        res.append(Resultado("node", True, f"Node {node} cumple {engines}"))

    # 3. Docker. Bloqueante para cualquier plan con verificación de infraestructura.
    ok, detalle = docker_listo()
    res.append(Resultado("docker", ok, detalle if ok else
                         f"{detalle}. Arranca Docker Desktop antes de la campaña."))

    # 4. Git: no trabajar sobre main, ni con cambios sin commitear.
    rama = rama_actual()
    res.append(Resultado("rama", rama != "main",
                         f"en {rama}" if rama != "main" else "está en main: nadie trabaja aquí"))

    sucio = arbol_sucio()
    res.append(Resultado("árbol limpio", not sucio,
                         "sin cambios sin commitear" if not sucio else
                         "hay cambios sin commitear; confírmalos o descártalos antes de empezar"))

    # 5. Herramientas de la campaña.
    oc = version_opencode()
    res.append(Resultado("opencode", oc is not None, oc or "no está en el PATH"))

    code, out = _cmd(["gh", "auth", "status"])
    res.append(Resultado("gh auth", code == 0,
                         "autenticado" if code == 0 else f"sin sesión de gh: {_primera_linea(out)}"))

    return res


def _node_cumple(version: str, rango: str) -> bool:
    """Cumple un rango de engines tipo '^22.22.3 || ^24.15.0 || ^26.0.0'."""
    partes = [p.strip() for p in rango.split("||") if p.strip()]
    if not partes:
        return True

    def cumple_una(parte: str) -> bool:
        operador = "^" if parte.startswith("^") else (">=" if parte.startswith(">=") else "==")
        limpio = parte.lstrip("^>=~ ")
        base = [int(x) for x in limpio.split(".")[:3] if x.isdigit()]
        real = [int(x) for x in version.split(".")[:3] if x.isdigit()]
        if not base or not real:
            return True
        while len(base) < 3:
            base.append(0)
        while len(real) < 3:
            real.append(0)
        if operador == ">=":
            return real >= base
        if operador == "==":
            return real[: len(base)] == base[: len(real)]
        return real[0] == base[0] and real >= base  # ^ = mismo major, >= base

    return any(cumple_una(p) for p in partes)


def main() -> int:
    ap = argparse.ArgumentParser(description="Precondiciones de la campaña de planes.")
    ap.add_argument("--aviso", action="store_true", help="imprime el resultado pero no corta")
    args = ap.parse_args()

    res = comprobar()

    print("Precondiciones de la campaña:")
    for r in res:
        marca = "OK  " if r.ok else "FALLA"
        sufijo = "" if r.ok else "  <-- bloqueante"
        print(f"  [{marca}] {r.nombre}: {r.detalle}{sufijo}")
    print()

    fallos = [r for r in res if not r.ok]
    if not fallos:
        print("Todo en orden. La campaña puede arrancar.")
        return 0
    if args.aviso:
        print(f"{len(fallos)} precondición(es) sin cumplir; se continúa porque pediste --aviso.")
        return 0
    print(f"{len(fallos)} precondición(es) sin cumplir. La campaña NO arranca.")
    print("Corrígelas a mano. Ninguna de ellas se arregla desde un agente.")
    return 1


if __name__ == "__main__":
    sys.exit(main())