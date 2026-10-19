#!/usr/bin/env python3
"""Valida el catálogo y los planes de docs/plans/ según la norma de docs/plans/README.md."""

import re
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
PLANES = RAIZ / "docs" / "plans"
ESPEC = RAIZ / "docs" / "especificacion"
ERRORES = RAIZ / "docs" / "contracts" / "errors.md"

FASES = ("F0", "F1", "F2", "F3", "F4", "F5")
AMBITOS = ("back", "front", "infra", "qa")
MODOS = ("directo", "openspec")
ESTADOS = ("propuesto", "en curso", "cerrado")
OBLIGATORIOS = ("Plan", "Ámbito", "Componente", "Fase", "Modo", "Estado", "Depende de", "Spec", "Casos")
SECCIONES = ("Objetivo", "Alcance", "Contexto", "Pasos", "Contratos afectados",
             "Pruebas", "Preguntas abiertas", "Criterio de terminado")
MAX_PASOS = 20
PREF = "SEC|LP|CRUD|OCC|CLN|STK|PAY|OUT|MSG|REP|NOT"
RE_CASO = re.compile(rf"\b({PREF})(\d{{2}})(?:[\u2013-](?:({PREF}))?(\d{{2}}))?\b")
RE_CASO_SUELTO = re.compile(rf"\b(?:{PREF})\d{{2}}\b")
RE_NOMBRE_PLAN = re.compile(r"(back|front|infra|qa)-[a-z0-9-]+")
# Los planes de arreglo (norma §6.11.5) no entran en los rangos de fase: son
# correcciones puntuales, no dependencias de los planes del calendario.
RE_ARREGLO = re.compile(r"(?:back|front|infra|qa)-arreglo-[a-z0-9-]+")
RE_PLAN_EN_TEXTO = re.compile(r"`((?:back|front|infra|qa)-[a-z0-9*-]+)`")
RE_FILA_PLAN = re.compile(
    r"^\|\s*(F[0-5])\s*\|\s*`([a-z0-9-]+)`\s*\|\s*(back|front|infra|qa)\s*\|"
    r"\s*`(directo|openspec)`\s*\|\s*(.*?)\s*\|\s*(.*?)\s*\|\s*$"
)
RE_FILA_DOS = re.compile(r"^\|\s*`([a-z0-9-]+)`\s*\|\s*(.*?)\s*\|\s*$")
RE_CAMPO = re.compile(r"^\|\s*([^|]+?)\s*\|\s*(.*?)\s*\|\s*$")
RE_RUTA = re.compile(r"^(?:\|\s*|- )(?:GET|POST|PUT|DELETE|PATCH) (\S+)")

errores = []
avisos = []


def error(donde, msg):
    linea = f"ERROR  {donde}: {msg}"
    if linea not in errores:
        errores.append(linea)


def aviso(donde, msg):
    linea = f"AVISO  {donde}: {msg}"
    if linea not in avisos:
        avisos.append(linea)


def leer(ruta):
    return ruta.read_text(encoding="utf-8")


def normalizar(ruta):
    ruta = ruta.split("?")[0].rstrip(":,;.")
    return re.sub(r"\{[^}]*\}", "{}", ruta)


def expandir(texto):
    ids = set()
    for m in RE_CASO.finditer(texto):
        pref, ini, fin = m.group(1), int(m.group(2)), m.group(4)
        if fin is None:
            ids.add(f"{pref}{ini:02d}")
        else:
            for i in range(ini, int(fin) + 1):
                ids.add(f"{m.group(3) or pref}{i:02d}")
    return ids


def secciones(texto):
    partes = re.split(r"^## ", texto, flags=re.M)[1:]
    return {p.split("\n", 1)[0].strip(): p for p in partes}


def seccion(mapa, inicio):
    return next((v for k, v in mapa.items() if k.startswith(inicio)), "")


def cuerpo(texto, titulo):
    dentro, lineas = False, []
    for linea in texto.splitlines():
        if linea.startswith("#"):
            if dentro:
                break
            dentro = titulo.lower() in linea.lower()
        elif dentro:
            lineas.append(linea)
    return "\n".join(lineas)


def casos_matriz():
    ids = set()
    for linea in leer(ESPEC / "06-migracion-y-pruebas.md").splitlines():
        m = re.match(rf"^\|\s*((?:{PREF})\d{{2}})\s*\|", linea)
        if m:
            ids.add(m.group(1))
    return ids


def mensajeria():
    topics, grupos, comandos = set(), set(), set()
    for linea in leer(ESPEC / "05-mensajeria.md").splitlines():
        if m := re.match(r"^\|\s*([a-z]+\.[a-z-]+\.v1)\s*\|", linea):
            topics.add(m.group(1))
        elif m := re.match(r"^\|\s*`([a-z]+(?:-[a-z]+)*-v1)`\s*\|", linea):
            grupos.add(m.group(1))
        elif m := re.match(r"^\|\s*([a-z]+(?:\.[a-z-]+)+)\s*\|\s*\w+ → \w+\s*\|", linea):
            comandos.add(m.group(1))
    return topics, grupos, comandos


def rutas_api():
    rutas = []
    for archivo in sorted((ESPEC / "microservicios").glob("*/api.md")):
        for linea in leer(archivo).splitlines():
            if m := RE_RUTA.match(linea):
                rutas.append(normalizar(m.group(1)))
    return rutas


def dueno(ruta, prefijos):
    candidatos = [p for p in prefijos if ruta == p or ruta.startswith(p + "/")]
    return max(candidatos, key=len) if candidatos else None


def leer_planes(texto, donde):
    planes = {}
    for linea in texto.splitlines():
        m = RE_FILA_PLAN.match(linea)
        if not m:
            continue
        fase, nombre, ambito, modo, deps_txt, _ = m.groups()
        if nombre in planes:
            error(donde, f"plan duplicado: {nombre}")
        rango = re.search(r"F([1-5])\u2013F([1-5])", deps_txt)
        planes[nombre] = {
            "fase": fase,
            "ambito": ambito,
            "modo": modo,
            "deps": set(re.findall(r"`([a-z0-9-]+)`", deps_txt)),
            "rango": (int(rango.group(1)), int(rango.group(2))) if rango else None,
        }
    for nombre, p in planes.items():
        if p["rango"]:
            a, b = p["rango"]
            p["deps"] |= {n for n, q in planes.items()
                          if a <= int(q["fase"][1]) <= b and n != nombre
                          and not RE_ARREGLO.fullmatch(n)}
        del p["rango"]
    return planes


def leer_mapa(texto, planes, donde):
    mapa = {}
    for linea in texto.splitlines():
        m = RE_FILA_DOS.match(linea)
        if not m:
            continue
        plan, col = m.groups()
        if plan not in planes:
            error(donde, f"{plan} no está en la sección de planes")
        for ruta in re.findall(r"`(/[^`]+)`", col):
            ruta = normalizar(ruta)
            if ruta in mapa:
                error(donde, f"prefijo duplicado: {ruta}")
            mapa[ruta] = plan
    return mapa


def hay_ciclo(planes):
    estado = {}

    def visita(n):
        if estado.get(n) == "visitando":
            return True
        if estado.get(n) == "hecho":
            return False
        estado[n] = "visitando"
        if any(visita(d) for d in planes[n]["deps"] if d in planes):
            return True
        estado[n] = "hecho"
        return False

    return any(visita(n) for n in planes)


def enlaces_rotos(archivo, texto):
    for destino in re.findall(r"\]\(([^)\s]+)\)", texto):
        if destino.startswith(("http://", "https://", "mailto:", "#")):
            continue
        ruta = destino.split("#")[0]
        if ruta and not (archivo.parent / ruta).exists():
            error(archivo.name, f"enlace roto: {destino}")


def cabecera(texto):
    campos = {}
    for linea in texto.splitlines()[:80]:
        m = RE_CAMPO.match(linea)
        if m and m.group(1) in OBLIGATORIOS + ("Change",):
            campos[m.group(1)] = m.group(2)
    return campos


def revisar_plan(archivo, planes, casos06, topics, grupos, comandos, rutas, errores_txt):
    donde = archivo.name
    texto = leer(archivo)
    nombre = archivo.stem
    if not RE_NOMBRE_PLAN.fullmatch(nombre):
        error(donde, "nombre inválido: debe ser ámbito-tema.md en minúsculas")
    cab = cabecera(texto)
    for campo in OBLIGATORIOS:
        if campo not in cab:
            error(donde, f"falta el campo «{campo}» en la cabecera")

    def v(campo):
        return cab.get(campo, "").strip("` ")

    ambito = nombre.split("-", 1)[0]
    if v("Plan") != nombre:
        error(donde, "el campo Plan no coincide con el nombre del archivo")
    if v("Ámbito") != ambito:
        error(donde, f"el ámbito «{v('Ámbito')}» no coincide con el prefijo «{ambito}»")
    if v("Fase") not in FASES:
        error(donde, f"Fase inválida: «{v('Fase')}»")
    if v("Modo") not in MODOS:
        error(donde, f"Modo inválido: «{v('Modo')}»")
    if v("Estado") not in ESTADOS:
        error(donde, f"Estado inválido: «{v('Estado')}»")
    if v("Modo") == "openspec" and not v("Change").startswith("openspec/changes/"):
        error(donde, "modo openspec exige Change: openspec/changes/<plan>/")
    if ambito == "front" and "DESIGN.md" not in texto:
        error(donde, "los planes front deben citar ../DESIGN.md (sistema de diseño)")

    deps = set(re.findall(r"`([a-z0-9-]+)`", cab.get("Depende de", "")))
    for d in sorted(deps):
        if d == nombre:
            error(donde, "el plan depende de sí mismo")
        elif d not in planes:
            error(donde, f"depende de «{d}», que no está en el catálogo")

    casos_txt = cab.get("Casos", "")
    if "Todos" not in casos_txt:
        for i in sorted(expandir(casos_txt) - casos06):
            error(donde, f"el caso {i} no existe en 06-migracion-y-pruebas.md")

    if nombre in planes:
        ref = planes[nombre]
        if v("Fase") != ref["fase"]:
            error(donde, f"Fase «{v('Fase')}» distinta del catálogo ({ref['fase']})")
        if ambito != ref["ambito"]:
            error(donde, f"ámbito distinto del catálogo ({ref['ambito']})")
        if v("Modo") != ref["modo"]:
            aviso(donde, f"Modo «{v('Modo')}» distinto del catálogo ({ref['modo']}); justificar en el PR")
        for d in sorted(ref["deps"] - deps):
            error(donde, f"faltan dependencias que fija el catálogo: {d}")
    else:
        error(donde, "plan no previsto en catalogo.md")

    for s in SECCIONES:
        if not re.search(rf"^#+ .*{re.escape(s)}", texto, re.M):
            error(donde, f"falta la sección «{s}»")
    pasos = re.findall(r"^\s*- \[[ xX]\]", cuerpo(texto, "Pasos"), re.M)
    if not 1 <= len(pasos) <= MAX_PASOS:
        error(donde, f"la sección Pasos tiene {len(pasos)} tareas; el rango permitido es 1 a {MAX_PASOS}")

    for i in sorted(set(RE_CASO_SUELTO.findall(texto)) - casos06):
        error(donde, f"el caso {i} no existe en 06-migracion-y-pruebas.md")
    for t in sorted(set(re.findall(r"`([a-z]+\.[a-z-]+\.v1)`", texto)) - topics):
        error(donde, f"el topic «{t}» no existe en 05-mensajeria.md")
    for c in sorted(set(re.findall(r"`((?:limpieza|stock|recepcion|notificacion)\.[a-z-]+)`", texto)) - comandos):
        error(donde, f"el comando «{c}» no existe en 05-mensajeria.md")
    for g in sorted(set(re.findall(r"`([a-z]+(?:-[a-z]+)*-v1)`", texto)) - grupos):
        error(donde, f"el grupo de consumidor «{g}» no existe en 05-mensajeria.md")
    rutas_citadas = re.findall(
        r"`(?:(?:GET|POST|PUT|DELETE|PATCH) )?(/(?:api|internal|actuator|\.well-known)/[^`\s]+)`", texto)
    for ruta in sorted(set(rutas_citadas)):
        if not any(r.startswith(normalizar(ruta)) for r in rutas):
            aviso(donde, f"la ruta `{ruta}` no aparece en los api.md")
    for cod in sorted(set(re.findall(r"`([A-Z]+(?:_[A-Z]+)+)`", texto))):
        if cod not in errores_txt:
            aviso(donde, f"`{cod}` no está en errors.md (¿código de error o estado?)")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    cat_txt = leer(PLANES / "catalogo.md")
    sec = secciones(cat_txt)
    cat = "catalogo.md"

    casos06 = casos_matriz()
    topics, grupos, comandos = mensajeria()
    rutas = rutas_api()
    errores_txt = leer(ERRORES)

    planes = leer_planes(seccion(sec, "1."), cat)
    for nombre, p in planes.items():
        if not nombre.startswith(p["ambito"] + "-"):
            error(cat, f"{nombre}: el ámbito no coincide con el prefijo")
        for d in sorted(p["deps"] - planes.keys()):
            error(cat, f"{nombre} depende de «{d}», que no está en el catálogo")
    if hay_ciclo(planes):
        error(cat, "hay un ciclo en las dependencias del catálogo")

    for nombre in sorted(set(RE_PLAN_EN_TEXTO.findall(cat_txt))):
        if "*" in nombre:
            base = nombre.split("*")[0]
            if not any(p.startswith(base) for p in planes):
                error(cat, f"«{nombre}» no coincide con ningún plan del catálogo")
        elif nombre not in planes:
            error(cat, f"«{nombre}» no es un plan del catálogo")

    sec2 = seccion(sec, "2.")
    for n in sorted(topics | grupos | comandos):
        if n not in sec2:
            error(cat, f"«{n}» de 05-mensajeria.md no tiene dueño en la sección 2")

    prefijos = leer_mapa(seccion(sec, "3."), planes, cat)
    for ruta in sorted({r for r in rutas if dueno(r, prefijos) is None}):
        error(cat, f"ruta sin plan dueño: {ruta}")
    for p in prefijos:
        if not any(r == p or r.startswith(p) for r in rutas):
            aviso(cat, f"el prefijo {p} no corresponde a ninguna ruta de los api.md")

    cubiertos = set()
    for linea in seccion(sec, "4.").splitlines():
        m = RE_FILA_DOS.match(linea)
        if not m:
            continue
        plan, col = m.groups()
        if plan not in planes:
            error(cat, f"«{plan}» no está en la sección de planes")
        ids = expandir(col)
        for i in sorted(ids - casos06):
            error(cat, f"{plan}: el caso {i} no existe en 06")
        cubiertos |= ids
    for c in sorted(casos06 - cubiertos):
        error(cat, f"el caso {c} no tiene plan dueño")

    archivos = [p for p in sorted(PLANES.glob("*.md"))
                if p.name not in ("README.md", "catalogo.md") and not p.name.startswith("_")]
    for md in sorted(PLANES.glob("*.md")):
        enlaces_rotos(md, leer(md))
    for archivo in archivos:
        revisar_plan(archivo, planes, casos06, topics, grupos, comandos, rutas, errores_txt)

    for linea in errores + avisos:
        print(linea)
    print(f"\n{len(archivos)} plan(es) revisado(s); {len(planes)} previsto(s) en el catálogo; "
          f"{len(errores)} error(es), {len(avisos)} aviso(s).")
    return 1 if errores else 0


if __name__ == "__main__":
    sys.exit(main())
