#!/usr/bin/env python3
"""Comprueba que los cambios en contratos solo son aditivos.

Existe porque la campaña de planes puede ampliar los contratos cuando la
especificación no llega a decidir un detalle. Eso está bien. Lo que no puede
hacer es cambiar lo que ya estaba, porque un cambio en un contrato consumido se
propaga en silencio y el síntoma aparece tres planes después, en otro módulo,
sin que nadie lo relacione con la decisión que lo causó.

La norma lo dice con todas las letras (§6.4): quien ejecuta un plan nunca toca
`docs/contracts/errors.md`. Este script es la excepción que hace falta para lo
demás, y lo hace con una garantía que un prompt no da: **esto es un fallo de
build, no una recomendación**.

Compara el árbol de trabajo contra una base (`origin/main` por defecto) y falla
si desaparece algo:

- una ruta, un método dentro de una ruta, o un código de respuesta
- el `operationId` de una operación existente
- un schema de `components/schemas`, o una propiedad sua
- un schema de evento entero
- un código de error declarado en `errors.md`

Añadir no falla nunca. Solo sumar.

Uso:
    python scripts/validar-contratos.py                    # contra origin/main
    python scripts/validar-contratos.py --base main
    python scripts/validar-contratos.py --base HEAD        # contra el ultimo commit
    python scripts/validar-contratos.py --solo-estructura  # sin git, solo convenciones

Solo biblioteca estandar: CI corre Python 3.12 sin instalar nada.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
OPENAPI = RAIZ / "docs" / "contracts" / "openapi"
EVENTOS = RAIZ / "docs" / "contracts" / "events"
ERRORES = RAIZ / "docs" / "contracts" / "errors.md"

METODOS = ("get", "post", "put", "delete", "patch", "head", "options", "trace")

# Los nombres reales no siguen un orden fijo: en unos el v1 va antes del
# nombre del evento (hotel.habitacion.v1.HabitacionCreada) y en otros despues
# (limpieza.solicitada.LimpiezaSolicitada.v1). Por eso la convencion no se
# codifica como un regex rigido, sino como una forma: los mismos segmentos que
# sus hermanos y el dominio en minusculas.
SUFIJO_EVENTO = ".schema.json"

# Codigo de error: identificador en mayusculas con guion bajo.
RE_CODIGO_ERROR = re.compile(r"`([A-Z][A-Z0-9_]{3,})`")


class Problema:
    def __init__(self, donde: str, msg: str, grave: bool = True):
        self.donde = donde
        self.msg = msg
        self.grave = grave

    def __str__(self) -> str:
        return f"{'ERROR ' if self.grave else 'AVISO '}{self.donde}: {self.msg}"


# --------------------------------------------------------------------------
# Lectura del contrato
# --------------------------------------------------------------------------

def _git_show(ref: str, ruta_rel: str) -> str | None:
    try:
        res = subprocess.run(["git", "show", f"{ref}:{ruta_rel}"],
                             cwd=RAIZ, capture_output=True, text=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        return None
    if res.returncode != 0:
        return None
    return res.stdout


def _sin_comentarios(linea: str) -> str:
    """Quita el comentario de final de linea, fuera de comillas."""
    dentro = None
    for i, c in enumerate(linea):
        if dentro:
            if c == dentro:
                dentro = None
        elif c in "'\"":
            dentro = c
        elif c == "#" and (i == 0 or linea[i - 1] in " \t"):
            return linea[:i]
    return linea


RE_PATH = re.compile(r"^  (/\S*):\s*(?:#.*)?$")
RE_METODO = re.compile(r"^    (" + "|".join(METODOS) + r"):\s*(?:#.*)?$", re.I)
RE_RESPONSES = re.compile(r"^      responses:\s*(?:#.*)?$")
RE_CODIGO_RESP = re.compile(r"^        '?([0-9]{3}|default)'?:\s*(?:#.*)?$")
RE_OPERATION_ID = re.compile(r"^      operationId:\s*(\S+)")
RE_SCHEMAS = re.compile(r"^  schemas:\s*(?:#.*)?$")
RE_SCHEMA = re.compile(r"^    ([A-Za-z_][A-Za-z0-9_]*):\s*(?:#.*)?$")
RE_PROPERTIES = re.compile(r"^      properties:\s*(?:#.*)?$")
RE_PROPIEDAD = re.compile(r"^        ([A-Za-z_][A-Za-z0-9_]*):\s*(?:#.*)?$")


def escanear_openapi(texto: str) -> dict:
    """Extrae rutas, metodos, codigos de respuesta, operationId y propiedades.

    No es un parser de YAML: es un lector de la forma que tienen estos ficheros,
    que es plana y regular, y que no se equivoca con los `>-`, los `[a, b]` en
    linea ni los `$ref`. Un parser general aqui daria falsos positivos, y un
    validador que falla de mas es peor que no tener validador.
    """
    rutas: dict[str, dict] = {}
    esquemas: dict[str, set] = {}

    en_paths = False
    en_schemas = False
    path_actual = None
    metodo_actual = None
    esquema_actual = None
    # (ruta, metodo) espera codigos de respuesta
    en_respuestas = False
    # esquema espera propiedades
    en_props = False
    # bloque multilinea: (>-, |, >, indentado que continua la anterior)
    multilinea_indent = None

    for cruda in texto.split("\n"):
        linea = _sin_comentarios(cruda)
        if not linea.strip():
            continue
        indent = len(linea) - len(linea.lstrip(" "))

        # Saltar el cuerpo de escalares de bloque multilinea.
        if multilinea_indent is not None:
            if indent > multilinea_indent:
                continue
            multilinea_indent = None

        if linea.rstrip().endswith((">-", "|", ">", "|-", ">-")) or re.search(r":\s*[>|][-+]?\s*$", linea):
            multilinea_indent = indent
            continue

        if indent == 0:
            en_paths = linea.startswith("paths:")
            en_schemas = False
            path_actual = metodo_actual = esquema_actual = None
            en_respuestas = en_props = False
            continue

        if RE_SCHEMAS.match(linea):
            en_paths = False
            en_schemas = True
            esquema_actual = None
            en_props = False
            continue

        if en_paths:
            m = RE_PATH.match(linea)
            if m:
                path_actual = m.group(1)
                rutas.setdefault(path_actual, {})
                metodo_actual = None
                en_respuestas = False
                continue
            if path_actual is None:
                continue
            m = RE_METODO.match(linea)
            if m:
                metodo_actual = m.group(1).lower()
                rutas[path_actual].setdefault(metodo_actual, {"responses": set(), "operationId": None})
                en_respuestas = False
                continue
            if metodo_actual is None:
                continue
            op = rutas[path_actual][metodo_actual]
            if RE_RESPONSES.match(linea):
                en_respuestas = True
                continue
            if indent == 6:
                en_respuestas = False
                m = RE_OPERATION_ID.match(linea)
                if m:
                    op["operationId"] = m.group(1)
                continue
            if indent == 8 and en_respuestas:
                m = RE_CODIGO_RESP.match(linea)
                if m:
                    op["responses"].add(m.group(1))
                continue

        if en_schemas:
            if indent == 2:
                en_schemas = False
                continue
            m = RE_SCHEMA.match(linea)
            if m and indent == 4:
                esquema_actual = m.group(1)
                esquemas.setdefault(esquema_actual, set())
                en_props = False
                continue
            if esquema_actual is None:
                continue
            if RE_PROPERTIES.match(linea):
                en_props = True
                continue
            if indent == 6:
                en_props = False
                continue
            if indent == 8 and en_props:
                m = RE_PROPIEDAD.match(linea)
                if m:
                    esquemas[esquema_actual].add(m.group(1))

    return {"paths": rutas, "schemas": esquemas}


def escanear_codigos_error(texto: str) -> set:
    return set(RE_CODIGO_ERROR.findall(texto))


def listar_eventos(directorio: Path) -> set:
    if not directorio.is_dir():
        return set()
    return {p.name for p in directorio.glob("*.schema.json")}


# --------------------------------------------------------------------------
# Comparacion
# --------------------------------------------------------------------------

def comparar_openapi(nombre: str, antes: dict, ahora: dict) -> list[Problema]:
    p: list[Problema] = []
    ref = f"docs/contracts/openapi/{nombre}"

    for ruta, ops in antes["paths"].items():
        if ruta not in ahora["paths"]:
            p.append(Problema(ref, f"se ha eliminado la ruta {ruta}"))
            continue
        for metodo, info in ops.items():
            if metodo not in ahora["paths"][ruta]:
                p.append(Problema(ref, f"se ha eliminado {metodo.upper()} {ruta}"))
                continue
            nuevo = ahora["paths"][ruta][metodo]
            perdidos = info["responses"] - nuevo["responses"]
            if perdidos:
                p.append(Problema(
                    ref,
                    f"{metodo.upper()} {ruta} ya no declara las respuestas "
                    f"{', '.join(sorted(perdidos))}"))
            if info["operationId"] and nuevo["operationId"] != info["operationId"]:
                p.append(Problema(
                    ref,
                    f"{metodo.upper()} {ruta} cambio de operationId "
                    f"({info['operationId']} -> {nuevo['operationId'] or 'ninguno'})"))

    for esquema, props in antes["schemas"].items():
        if esquema not in ahora["schemas"]:
            p.append(Problema(ref, f"se ha eliminado el schema {esquema}"))
            continue
        perdidas = props - ahora["schemas"][esquema]
        if perdidas:
            p.append(Problema(
                ref,
                f"el schema {esquema} ya no declara las propiedades "
                f"{', '.join(sorted(perdidas))}"))
    return p


def comprobar_convenciones(contratos: dict[str, dict], eventos: set,
                           nuevos_eventos: set | None = None) -> list[Problema]:
    """Sobre lo nuevo: que no se cuele nada con otra forma.

    `eventos` son todos los schemas que hay ahora (los que ya existen mas los
    nuevos). `nuevos_eventos` son solo los anadidos en este cambio; si no se
    pasa, se comprueban todos, que es lo util al pasar `--solo-estructura`.
    """
    p: list[Problema] = []

    for nombre, datos in contratos.items():
        ref = f"docs/contracts/openapi/{nombre}"
        rutas = list(datos["paths"])
        prefijos = {"/" + r.strip("/").split("/")[0] for r in rutas if r.count("/") > 1}
        for ruta in rutas:
            if ruta.count("/") <= 1:
                continue
            if "/" + ruta.strip("/").split("/")[0] not in prefijos:
                p.append(Problema(
                    ref,
                    f"la ruta {ruta} no cuelga de ningun prefijo existente",
                    grave=False))

    a_revisar = sorted(eventos if nuevos_eventos is None else nuevos_eventos)

    # Forma que siguen los hermanos: numero de segmentos separados por punto.
    hermanos = [n[:-len(SUFIJO_EVENTO)].count(".") for n in eventos
                if n.endswith(SUFIJO_EVENTO)]
    if hermanos:
        forma = max(set(hermanos), key=hermanos.count)
        for nombre in a_revisar:
            if not nombre.endswith(SUFIJO_EVENTO):
                continue
            base = nombre[:-len(SUFIJO_EVENTO)]
            if base.count(".") != forma:
                p.append(Problema(
                    "docs/contracts/events",
                    f"{nombre} tiene {base.count('.') + 1} segmentos y sus hermanos tienen "
                    f"{forma + 1}. Copia la forma de un evento del mismo dominio.",
                    grave=False))
            if not base or not base.split(".")[0].islower():
                p.append(Problema(
                    "docs/contracts/events",
                    f"{nombre} deberia empezar por el dominio en minusculas",
                    grave=False))
    return p


# --------------------------------------------------------------------------
# Programa
# --------------------------------------------------------------------------

def main() -> int:
    ap = argparse.ArgumentParser(description="Valida que los cambios en contratos son aditivos.")
    ap.add_argument("--base", default="origin/main", help="referencia contra la que comparar")
    ap.add_argument("--solo-estructura", action="store_true",
                    help="no usar git: solo comprobar convenciones de nombres")
    args = ap.parse_args()

    ahora_contratos = {a.name: escanear_openapi(a.read_text(encoding="utf-8"))
                       for a in sorted(OPENAPI.glob("*.yaml"))}
    ahora_eventos = listar_eventos(EVENTOS)
    nuevos_eventos: set = set()
    ahora_errores = escanear_codigos_error(ERRORES.read_text(encoding="utf-8")) if ERRORES.exists() else set()

    problemas: list[Problema] = []
    problemas += comprobar_convenciones(ahora_contratos, ahora_eventos, nuevos_eventos)

    if not args.solo_estructura:
        base_contratos: dict[str, dict] = {}
        base_eventos: set = set()
        base_errores: set = set()

        for archivo in sorted(OPENAPI.glob("*.yaml")):
            texto = _git_show(args.base, f"docs/contracts/openapi/{archivo.name}")
            if texto is not None:
                base_contratos[archivo.name] = escanear_openapi(texto)

        # Schemas de evento: los que existian en la base.
        salida = subprocess.run(["git", "ls-tree", "-r", "--name-only", args.base,
                                 "--", "docs/contracts/events"],
                                cwd=RAIZ, capture_output=True, text=True)
        if salida.returncode == 0:
            base_eventos = {Path(l).name for l in salida.stdout.split("\n")
                            if l.strip().endswith(SUFIJO_EVENTO)}
            nuevos_eventos = ahora_eventos - base_eventos

        texto_err = _git_show(args.base, "docs/contracts/errors.md")
        if texto_err is not None:
            base_errores = escanear_codigos_error(texto_err)

        for nombre in sorted(set(base_contratos) | set(ahora_contratos)):
            if nombre not in base_contratos:
                continue
            if nombre not in ahora_contratos:
                problemas.append(Problema(
                    f"docs/contracts/openapi/{nombre}", "se ha eliminado el fichero de contrato"))
                continue
            problemas += comparar_openapi(nombre, base_contratos[nombre], ahora_contratos[nombre])

        for nombre in sorted(base_eventos - ahora_eventos):
            problemas.append(Problema("docs/contracts/events", f"se ha eliminado el schema {nombre}"))

        for codigo in sorted(base_errores - ahora_errores):
            problemas.append(Problema(
                "docs/contracts/errors.md",
                f"ha desaparecido el codigo de error {codigo}"))

    errores = [x for x in problemas if x.grave]
    avisos = [x for x in problemas if not x.grave]

    base_texto = "convenciones" if args.solo_estructura else f"aditivos frente a {args.base}"
    print(f"Contratos: {len(ahora_contratos)} OpenAPI, {len(ahora_eventos)} schemas de evento. Regla: {base_texto}.")

    for a in avisos:
        print(f"  {a}")
    for e in errores:
        print(f"  {e}")

    if errores:
        print()
        print(f"{len(errores)} cambio(s) rompen un contrato ya publicado.")
        print("Añadir esta permitido; quitar o cambiar, no. Si de verdad hay que romperlo,")
        print("es una decision de la especificacion, no de un plan, y aqui no se resuelve.")
        return 1
    if avisos:
        print(f"{len(avisos)} aviso(s), sin errores.")
        return 0
    print("Los contratos solo se han ampliado. Correcto.")
    return 0


if __name__ == "__main__":
    sys.exit(main())