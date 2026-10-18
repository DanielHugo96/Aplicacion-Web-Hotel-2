#!/usr/bin/env bash
# Envoltorio del Maven Wrapper que usa el JDK que pide el proyecto.
#
# Existe por un motivo concreto: el proyecto fija Java 25 (services/pom.xml,
# <java.version>), pero el PATH de una sesión puede apuntar a otro major, y el
# Maven Wrapper aborta si no encuentra JAVA_HOME. Sin esto, `mvnw verify`
# falla por un motivo que no tiene nada que ver con el código, y lo más grave es
# que un agente lo lee como "el proyecto está mal" y baja <release> a lo que
# haya instalado. Eso destruye el stack fijado sin que nada avise.
#
# Este script localiza el JDK correcto entre los instalados, lo exporta y
# devuelve el control al wrapper. Nada de esto toca la configuración de la
# máquina: cada invocación se resuelve sola.
#
# Uso:
#   bash scripts/mvn.sh -f services/pom.xml verify
#   bash scripts/mvn.sh -f services/hotel-service/pom.xml test

set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

# El major que declara el repositorio, leído de services/pom.xml.
PEDIDO="$(sed -n 's/.*<java\.version>\s*\([0-9]*\).*/\1/p' services/pom.xml | head -1)"

if [ -z "$PEDIDO" ]; then
  echo "No se pudo leer <java.version> de services/pom.xml" >&2
  exit 1
fi

major_de() {
  # major que reporta un binario java
  local exe="$1"
  [ -x "$exe" ] || [ -f "$exe" ] || return 1
  "$exe" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1
}

# Si JAVA_HOME ya apunta al major correcto, no se toca nada.
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  if [ "$(major_de "$JAVA_HOME/bin/java")" = "$PEDIDO" ]; then
    exec ./mvnw "$@"
  fi
fi

# Buscar en las carpetas de instalación habituales de Windows, Linux y macOS.
CANDIDATOS=()
for base in \
  "/c/Program Files/Eclipse Adoptium" \
  "/c/Program Files/Java" \
  "/c/Program Files/Microsoft" \
  "/c/Program Files/Zulu" \
  "/Library/Java/JavaVirtualMachines" \
  "/usr/lib/jvm"; do
  [ -d "$base" ] || continue
  for d in "$base"/jdk-* "$base"/temurin-*; do
    [ -d "$d" ] || continue
    CANDIDATOS+=("$d")
    [ -d "$d/Contents/Home" ] && CANDIDATOS+=("$d/Contents/Home")
  done
  [ -d "$base/latest" ] && CANDIDATOS+=("$base/latest")
done

for d in "${CANDIDATOS[@]:-}"; do
  [ -x "$d/bin/java" ] || continue
  if [ "$(major_de "$d/bin/java")" = "$PEDIDO" ]; then
    export JAVA_HOME="$d"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "[mvn.sh] Usando el JDK $PEDIDO de $JAVA_HOME" >&2
    exec ./mvnw "$@"
  fi
done

echo "[mvn.sh] No hay ningún JDK $PEDIDO instalado en esta máquina." >&2
echo "[mvn.sh] El proyecto lo fija en services/pom.xml y no se puede bajar a otra versión." >&2
exit 1