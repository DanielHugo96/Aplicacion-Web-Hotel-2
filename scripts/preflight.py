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
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent


class Resultado:
    """Una precondición.

    Tres severidades, y solo una detiene la campaña:
      - ok    : todo bien
      - aviso : hay un problema que la campaña resuelve sola. Se avisa, no se para.
      - falla : no hay quien lo resuelva automáticamente. La campaña no arranca.
    """

    def __init__(self, nombre: str, ok: bool, detalle: str,
                 bloqueante: bool = True, aviso: bool = False):
        self.nombre = nombre
        self.ok = ok
        self.detalle = detalle
        self.bloqueante = bloqueante
        self.aviso = aviso

    @property
    def severidad(self) -> str:
        if self.ok:
            return "OK"
        return "AVISO" if self.aviso else "FALLA"


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


# Dónde se instalan los JDK en Windows, y también las carpeta habituales en
# Linux y macOS por si el repo se clona en otro sitio.
CARPETAS_JDK = (
    "C:/Program Files/Eclipse Adoptium",
    "C:/Program Files/Java",
    "C:/Program Files/Microsoft",
    "C:/Program Files/Zulu",
    "C:/Program Files/BellSoft",
    "/Library/Java/JavaVirtualMachines",
    "/usr/lib/jvm",
)


def _version_de_java(ejecutable: Path) -> str | None:
    """Versión que reporta un binario java concreto."""
    try:
        res = subprocess.run([str(ejecutable), "-version"], capture_output=True,
                             text=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        return None
    salida = (res.stderr or "") + (res.stdout or "")
    m = re.search(r'version\s+"([0-9._]+)"', salida)
    return m.group(1) if m else None


def buscar_jdk(major: str) -> str | None:
    """Localiza un JDK instalado cuya versión principal sea `major`.

    Orden de búsqueda: JAVA_HOME, el PATH, y luego las carpetas de instalación
    habituales. Devuelve la raíz del JDK (`.../jdk-25.0.4`), no el binario.

    Existe porque tener el JDK 25 instalado no basta: si el PATH de la sesión
    apunta al 17, `mvnw verify` sigue fallando. La campaña puede arreglarlo sola
    (ver `entorno_campana` en orquestar.py), pero saber dónde está hace el
    diagnóstico mucho más claro.
    """
    candidatos: list[Path] = []

    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        candidatos.append(Path(java_home))

    for carpeta in os.environ.get("PATH", "").split(os.pathsep):
        if not carpeta:
            continue
        p = Path(carpeta)
        if (p / "java.exe").exists() or (p / "java").exists():
            candidatos.append(p.parent)

    for base in CARPETAS_JDK:
        raiz = Path(base)
        if not raiz.is_dir():
            continue
        # /Library/Java/JavaVirtualMachines/*.jdk/Contents/Home
        entradas = sorted(raiz.glob("jdk-*")) + sorted(raiz.glob("temurin-*.jdk"))
        for d in entradas:
            if d.is_dir():
                candidatos.append(d)
                if (d / "Contents" / "Home").is_dir():
                    candidatos.append(d / "Contents" / "Home")
        if (raiz / "latest").is_dir():
            candidatos.append(raiz / "latest")

    vistos: set[str] = set()
    for cand in candidatos:
        clave = str(cand).lower()
        if clave in vistos:
            continue
        vistos.add(clave)
        for nombre in ("java.exe", "java"):
            exe = cand / "bin" / nombre
            if exe.exists():
                version = _version_de_java(exe)
                if version and version.split(".")[0] == major:
                    return str(cand)
                break
    return None


def jdk_correcto_para_ambiente() -> tuple[str | None, str]:
    """(ruta del JDK que el proyecto pide, explicación) para esta máquina."""
    declarada = version_java_declarada()
    if not declarada:
        return None, "services/pom.xml no declara <java.version>"
    major = declarada.split(".")[0]
    return buscar_jdk(major), declarada


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
    #
    # Aquí hay que distinguir tres casos que se confunden:
    #   a) el PATH apunta al JDK correcto              -> todo bien
    #   b) el PATH apunta a otro pero el correcto está  -> se puede arreglar solo
    #      instalado en otra carpeta
    #   c) el JDK correcto no existe                    -> hay que instalarlo
    declarada = version_java_declarada()
    real = version_java_real()
    if declarada is None:
        res.append(Resultado("java fijada", False, "no se pudo leer <java.version> de services/pom.xml"))
    else:
        mayor_pedido = declarada.split(".")[0]
        mayor_real = real.split(".")[0] if real else None

        if mayor_real == mayor_pedido:
            res.append(Resultado("java fijada", True, f"Java {real} (fijada: {declarada})"))
        else:
            encontrado, _ = jdk_correcto_para_ambiente()
            if encontrado:
                # No bloqueante: orquestar.py fuerza este JDK en el entorno del
                # agente y arranca un servidor privado para que llegue al shell.
                res.append(Resultado(
                    "java fijada", False,
                    f"el PATH usa Java {real or 'nada'}, pero el {declarada} sí está instalado en "
                    f"{encontrado}. Las ejecutoras lo resuelven solas con scripts/mvn.sh. "
                    f"Para dejarlo bien en tu máquina, apunta JAVA_HOME ahí y reinicia OpenCode. "
                    f"NUNCA cambies services/pom.xml para que el build pase.",
                    bloqueante=False, aviso=True))
            else:
                res.append(Resultado(
                    "java fijada", False,
                    f"Java {real or 'nada'} en el PATH y no hay ningún JDK {declarada} instalado. "
                    f"Instálalo a mano. NUNCA cambies services/pom.xml para que el build pase."))

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

    # 4. Git. La campaña **empieza** en main: el orquestador hace
    #    `git switch main && git pull` y de ahí saca la rama de cada plan. Por
    #    eso estar en main no es un problema. Lo que sí lo es es quedarse a
    #    medias de un merge o tener cambios sin commitear.
    rama = rama_actual()
    res.append(Resultado("rama", True,
                         f"en {rama} (el orquestador empieza aquí y saca la rama de cada plan)"))

    a_medias = []
    if (RAIZ / ".git" / "MERGE_HEAD").exists():
        a_medias.append("hay un merge a medias")
    if (RAIZ / ".git" / "rebase-merge").is_dir() or (RAIZ / ".git" / "rebase-apply").is_dir():
        a_medias.append("hay un rebase a medias")
    res.append(Resultado("git limpio", not a_medias,
                         "sin operaciones a medias" if not a_medias
                         else f"{', '.join(a_medias)}: termina o aborta eso antes de empezar"))

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

    # 6. Contratos: si el árbol actual rompe un contrato ya publicado, la
    #    campaña no puede ni arrancar. Es la misma comprobación que corre en CI,
    #    puesta aquí para no descubrirlo tres planes después.
    validador = RAIZ / "scripts" / "validar-contratos.py"
    if validador.exists():
        code, out = _cmd([sys.executable, str(validador), "--base", "origin/main"], timeout=120)
        detalle = "los contratos solo se amplian" if code == 0 else _primera_linea(out)
        res.append(Resultado("contratos aditivos", code == 0, detalle))

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
    ap.add_argument("--jdk", action="store_true", help="solo dice dónde está el JDK que pide el proyecto")
    args = ap.parse_args()

    if args.jdk:
        declarado = version_java_declarada()
        if not declarado:
            print("services/pom.xml no declara <java.version>", file=sys.stderr)
            return 1
        encontrado, _ = jdk_correcto_para_ambiente()
        if not encontrado:
            print(f"No hay ningún JDK {declarado} instalado en esta máquina.", file=sys.stderr)
            return 1
        print(encontrado)
        return 0

    res = comprobar()

    print("Precondiciones de la campaña:")
    for r in res:
        sufijo = "" if r.ok else ("  <-- la campaña lo resuelve" if r.aviso else "  <-- bloqueante")
        print(f"  [{r.severidad:<5}] {r.nombre}: {r.detalle}{sufijo}")
    print()

    fallos = [r for r in res if not r.ok and not r.aviso]
    avisos = [r for r in res if not r.ok and r.aviso]

    if avisos:
        print(f"{len(avisos)} aviso(s). La campaña sigue: se resuelven solos.")
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