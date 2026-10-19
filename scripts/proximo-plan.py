#!/usr/bin/env python3
"""Calcula cuál es el siguiente plan de docs/plans que se puede ejecutar.

Es el selector determinista de la campaña automática. El agente orquestador NO
decide el orden: este script se lo da. Así ninguna decisión de secuenciación
depende de un modelo.

Fuente del orden: la tabla de tandas de docs/implementacion/orden.md §2. Esa tabla
se copia abajo como CALENDARIO. Si algún día cambia orden.md, hay que actualizar
esta lista (--comprobar avisa si ya no cuadra con los archivos del disco).

Un plan está listo (docs/implementacion/README.md §2) cuando:

  - su Estado en el archivo es `propuesto` o `en curso`,
  - todos sus `Depende de` tienen Estado `cerrado`,
  - no existe una rama remota con su nombre (salvo que sea la suya, en cuyo caso
    se reanuda),
  - y la zona que toca está libre.

Los planes de arreglo (`<ámbito>-arreglo-<tema>`, norma §6.11.5) no están en el
calendario. Se devuelven **antes** que los normales: si `main` está roto, el
arreglo va primero. Cuando se cierran, la campaña retoma el plan que esperaba.

La campaña es secuencial, así que la zona se comprueba solo: si un plan quedó
`en curso` se devuelve primero, y si no, se devuelve el primero listo del
calendario. Nunca habrá dos planes abiertos a la vez.

Uso:
    python scripts/proximo-plan.py               # imprime el plan listo, o nada
    python scripts/proximo-plan.py --listar      # tabla de estado de los planes
    python scripts/proximo-plan.py --explain <plan>
    python scripts/proximo-plan.py --comprobar   # ¿el calendario sigue cuadrando?
    python scripts/proximo-plan.py --sin-remoto  # no consulta origin (offline)
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
PLANES = RAIZ / "docs" / "plans"
ORIGEN = "origin"

# Ficheros de docs/plans/ que no son planes.
NO_PLANES = {"README.md", "catalogo.md", "_plantilla.md", "_prompts.md"}

# Orden canónico de docs/implementacion/orden.md §2, de T0 a T14.
CALENDARIO = [
    # T0
    "infra-monorepo",
    # T1
    "infra-compose",
    # T2
    "back-plataforma-comunes",
    "infra-gateway-eureka",
    # T3
    "back-plataforma-mensajeria",
    # T4
    "back-identity-auth",
    "back-notification-worker",
    # T5
    "back-hotel-catalogo",
    "front-admin-web-base",
    # T6
    "back-identity-personas",
    "back-inventory-stock",
    "front-public-web-landing",
    # T7
    "back-reception-operacion",
    "front-admin-web-catalogo",
    # T8
    "back-hotel-operaciones",
    "back-sales-ventas",
    # T9
    "back-reception-cierre",
    "front-admin-web-area-cliente",
    # T10
    "back-reporting-proyecciones",
    "front-admin-web-recepcion",
    # T11
    "qa-runbooks",
    "front-admin-web-ventas",
    # T12
    "front-admin-web-cierre",
    # T13
    "front-admin-web-reportes",
    # T14
    "qa-matriz-aceptacion",
]

RE_CAMPO = re.compile(r"^\|\s*(?P<clave>[^|]+?)\s*\|\s*(?P<valor>.*?)\s*\|\s*$")
RE_BACKTICK = re.compile(r"`([^`]+)`")
RE_ESTADO = re.compile(r"`(propuesto|en curso|cerrado)`")
# Un plan de arreglo no está en el calendario y tiene prioridad sobre los normales.
RE_ARREGLO = re.compile(r"^(?:back|front|infra|qa)-arreglo-[a-z0-9-]+$")
# El punto tras el número es opcional: hay planes con "## 4 Pasos" y planes con
# "## 4. Pasos". Los dos formatos tienen que contar igual.
RE_SECCION = re.compile(r"^##\s+(?:\d+\.?\s*)?(?P<titulo>[^#]+?)\s*$")
RE_PASO = re.compile(r"^- \[(?P<marca>[ xX])\]")


class Plan:
    def __init__(self, nombre: str, archivo: Path):
        self.nombre = nombre
        self.archivo = archivo
        self.estado = "?"
        self.ambito = "?"
        self.dependencias: list[str] = []
        # Progreso de la sección 4. Pasos.
        self.pasos = 0
        self.pasos_hechos = 0
        # Progreso de la sección 8. Criterio de terminado.
        self.criterio = 0
        self.criterio_hecho = 0

    @property
    def reabierto(self) -> bool:
        return self.estado == "en curso"

    def __repr__(self) -> str:  # pragma: no cover - ayuda en depuración
        return f"<Plan {self.nombre} {self.estado}>"


def leer_plan(archivo: Path) -> Plan:
    """Extrae los campos de la cabecera y el progreso de las casillas del plan.

    Las casillas se cuentan por sección, porque el plan tiene dos listas distintas
    y no son lo mismo: la 4 (Pasos, el trabajo) y la 8 (Criterio de terminado, la
    condición de cierre). La sección 4 es la que gobierna la campaña; la 8 se
    muestra aparte porque sirve para detectar planes cerrados con el criterio a
    medias.
    """
    plan = Plan(archivo.stem, archivo)
    texto = archivo.read_text(encoding="utf-8")
    lineas = texto.split("\n")

    # La cabecera es la primera tabla del archivo: se para en el primer título.
    for linea in lineas:
        if linea.startswith("## "):
            break
        m = RE_CAMPO.match(linea)
        if not m:
            continue
        clave = m.group("clave").strip()
        valor = m.group("valor")
        if clave == "Estado":
            e = RE_ESTADO.search(valor)
            if e:
                plan.estado = e.group(1)
        elif clave == "Ámbito":
            plan.ambito = valor.strip("` ")
        elif clave == "Depende de":
            plan.dependencias = [d for d in RE_BACKTICK.findall(valor) if d != "—"]

    # Recuento de casillas por sección.
    seccion = ""
    for linea in lineas:
        m = RE_SECCION.match(linea)
        if m:
            seccion = m.group("titulo").strip().lower()
            continue
        m = RE_PASO.match(linea)
        if not m:
            continue
        hecha = m.group("marca").lower() == "x"
        if seccion.startswith("pasos"):
            plan.pasos += 1
            plan.pasos_hechos += hecha
        elif seccion.startswith("criterio"):
            plan.criterio += 1
            plan.criterio_hecho += hecha

    return plan


def es_arreglo(nombre: str) -> bool:
    """True si el nombre corresponde a un plan de arreglo (norma §6.11.5)."""
    return bool(RE_ARREGLO.fullmatch(nombre))


def cargar_planes() -> dict[str, Plan]:
    planes: dict[str, Plan] = {}
    for archivo in sorted(PLANES.glob("*.md")):
        if archivo.name in NO_PLANES:
            continue
        plan = leer_plan(archivo)
        planes[plan.nombre] = plan
    return planes


def rama_remota(nombre: str) -> bool:
    """True si existe la rama en origin. No lanza error si no hay red."""
    try:
        res = subprocess.run(
            ["git", "ls-remote", "--heads", ORIGEN, nombre],
            cwd=RAIZ,
            capture_output=True,
            text=True,
            timeout=30,
        )
    except (OSError, subprocess.SubprocessError):
        return False
    return res.returncode == 0 and bool(res.stdout.strip())


def dependencias_satisfechas(plan: Plan, planes: dict[str, Plan]) -> tuple[bool, list[str]]:
    faltan = []
    for dep in plan.dependencias:
        otro = planes.get(dep)
        if otro is None:
            faltan.append(f"{dep} (no existe el archivo)")
        elif otro.estado != "cerrado":
            faltan.append(f"{dep} ({otro.estado})")
    return (not faltan), faltan


def comprobar_calendario(planes: dict[str, Plan]) -> list[str]:
    problemas = []
    en_disco = set(planes)
    en_calendario = set(CALENDARIO)
    # Los planes de arreglo viven en docs/plans/ pero no ocupan tanda: su orden
    # lo fija su prioridad y sus dependencias, no el calendario.
    arreglos = {n for n in en_disco if es_arreglo(n)}
    for sobra in sorted(en_calendario - en_disco):
        problemas.append(f"{sobra}: está en el CALENDARIO de este script pero no en docs/plans/")
    for falta in sorted(en_disco - en_calendario - arreglos):
        problemas.append(f"{falta}: está en docs/plans/ pero no en el CALENDARIO de este script")
    return problemas


def elegir(planes: dict[str, Plan], consultar_remoto: bool) -> tuple[str | None, list[str]]:
    """Devuelve (plan_elegido, notas). ``None`` si no hay nada listo."""
    notas: list[str] = []
    arreglos = sorted(n for n in planes if es_arreglo(n))

    def listo(nombre: str, estado: str) -> str | None:
        plan = planes.get(nombre)
        if plan is None or plan.estado != estado:
            return None
        ok, faltan = dependencias_satisfechas(plan, planes)
        if not ok:
            notas.append(f"{nombre}: bloqueado por {', '.join(faltan)}")
            return None
        if consultar_remoto and rama_remota(nombre):
            notas.append(f"{nombre}: ya existe su rama en {ORIGEN}, alguien lo tomó antes")
            return None
        return nombre

    # 1. Planes de arreglo primero, siempre. Si `main` está roto, el arreglo va
    #    antes que cualquier plan normal. Primero el que quedó a medias.
    for estado in ("en curso", "propuesto"):
        for nombre in arreglos:
            elegido = listo(nombre, estado)
            if elegido:
                notas.append(f"{nombre}: plan de arreglo, va antes que los normales")
                return elegido, notas

    # 2. Reanudar cualquier plan normal que quedara a medias. Es lo que hace la
    #    campaña reanudable: si se corta a mitad, el plan se devuelve primero.
    for nombre in CALENDARIO:
        plan = planes.get(nombre)
        if plan and plan.reabierto:
            notas.append(f"{nombre}: ESTADO 'en curso', se reanuda este primero")
            return nombre, notas

    # 3. Primer plan propuesto cuyas dependencias estén cerradas y libre de rival.
    for nombre in CALENDARIO:
        elegido = listo(nombre, "propuesto")
        if elegido:
            return elegido, notas

    return None, notas


def main() -> int:
    ap = argparse.ArgumentParser(description="Selector de plan de la campaña automática.")
    ap.add_argument("--listar", action="store_true", help="tabla de estado de todos los planes")
    ap.add_argument("--explain", metavar="PLAN", help="detalle de un plan")
    ap.add_argument("--comprobar", action="store_true", help="comprueba el CALENDARIO contra docs/plans/")
    ap.add_argument("--sin-remoto", action="store_true", help="no consulta si la rama existe en origin")
    ap.add_argument("--notas", action="store_true", help="imprime también el por qué de cada salto")
    args = ap.parse_args()

    planes = cargar_planes()

    if args.comprobar:
        problemas = comprobar_calendario(planes)
        if not problemas:
            arreglos = [n for n in planes if es_arreglo(n)]
            extra = f" y {len(arreglos)} plan(es) de arreglo" if arreglos else ""
            print(f"OK: el CALENDARIO cuadra con los {len(planes) - len(arreglos)} planes "
                  f"de docs/plans/{extra}")
            return 0
        print("DESAJUSTE entre el CALENDARIO de proximo-plan.py y docs/plans/:", file=sys.stderr)
        for p in problemas:
            print(f"  - {p}", file=sys.stderr)
        print("Actualiza CALENDARIO en scripts/proximo-plan.py (fuente: docs/implementacion/orden.md §2).",
              file=sys.stderr)
        return 1

    if args.listar:
        orden = CALENDARIO + sorted(n for n in planes if es_arreglo(n))
        ancho = max(len(p) for p in orden)
        print(f"{'plan'.ljust(ancho)}  {'ambito':<7}  {'estado':<9}  {'pasos':<8} {'criterio':<9} depende de")
        for nombre in orden:
            plan = planes.get(nombre)
            if plan is None:
                print(f"{nombre.ljust(ancho)}  <falta el archivo>")
                continue
            deps = ", ".join(plan.dependencias) or "—"
            print(f"{plan.nombre.ljust(ancho)}  {plan.ambito:<7}  {plan.estado:<9} "
                  f"{f'{plan.pasos_hechos}/{plan.pasos}':<8} "
                  f"{f'{plan.criterio_hecho}/{plan.criterio}':<9} {deps}")
        return 0

    if args.explain:
        plan = planes.get(args.explain)
        if plan is None:
            print(f"No existe el plan {args.explain}", file=sys.stderr)
            return 1
        ok, faltan = dependencias_satisfechas(plan, planes)
        print(f"Plan:      {plan.nombre}")
        print(f"Ámbito:    {plan.ambito}")
        print(f"Estado:    {plan.estado}")
        print(f"Pasos:     {plan.pasos_hechos}/{plan.pasos}")
        print(f"Criterio:  {plan.criterio_hecho}/{plan.criterio}")
        print(f"Depende de {', '.join(plan.dependencias) or '—'}")
        print(f"Listo:     {'sí' if ok else 'no'}")
        if faltan:
            print("Bloqueado por:")
            for f in faltan:
                print(f"  - {f}")
        return 0

    elegido, notas = elegir(planes, consultar_remoto=not args.sin_remoto)

    if args.notas:
        for n in notas:
            print(f"# {n}", file=sys.stderr)

    if elegido is None:
        if notas:
            print("Ningún plan listo.", file=sys.stderr)
        else:
            print("Campaña completa: no queda ningún plan propuesto.", file=sys.stderr)
        return 0

    print(elegido)
    return 0


if __name__ == "__main__":
    sys.exit(main())