# IA-front-public-web-landing-01 — puerto de desarrollo y enlace al panel

- **Plan:** `front-public-web-landing`
- **Paso:** 1 de la sección 4. Pasos (esqueleto y base del Gateway) y paso 5 (navegación de la landing)
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-20

## Qué se encontró

Los documentos discrepan sobre los puertos de desarrollo de las dos apps:

- El `README.md` raíz, sección «Estructura», dice `public-web` (puerto 4200) y `admin-web`
  (puerto 4300).
- `docs/DESIGN.md`, encabezado, dice `public-web` (dev en `http://localhost:4300`) y `admin-web`
  (dev en `http://localhost:4200`).

El plan no fija puertos: solo exige que la base del Gateway sea `http://localhost:8080` y que el
enlace al panel sea configurable por entorno sin embeber tokens. Los CORS del Gateway
(`docs/contracts/README.md` §1.1) permiten tanto `http://localhost:4200` como
`http://localhost:4300`.

## Qué se decidió

1. Mantener el puerto por defecto del CLI de Angular para `public-web` (`4200`), sin tocar
   `angular.json`. La app se sirve en `http://localhost:4200` en desarrollo.
2. Usar `http://localhost:4300` como valor por defecto del enlace «Acceso al panel», tomado del
   `README.md` raíz, y dejarlo configurable en tiempo de ejecución con
   `globalThis.__PUBLIC_WEB_CONFIG__.panelUrl`.

## Por qué

Se aplica la jerarquía de [`../README.md`](../README.md) §1 (norma > plan > catálogo > registro).
El `README.md` raíz describe la estructura real del monorepo y coincide con los valores por
defecto del CLI y con el CORS del Gateway; `DESIGN.md` es una guía visual y su encabezado no es la
fuente de los puertos. La opción elegida es la menos invasiva: no cambia `angular.json`, no toca
`apps/admin-web` y no altera ningún contrato.

## Alternativas descartadas

- **Seguir los puertos de `DESIGN.md`** (`public-web` en 4300 y `admin-web` en 4200): obligaría a
  cambiar la configuración de ambas apps y contradiría el `README.md` raíz sin que el plan lo pida.
- **Fijar el panel a `localhost:4200`**: chocaría con el puerto por defecto de `public-web` y
  dejaría el enlace apuntando a la propia landing.
- **Hardcodear el panel en el template**: impediría cambiarlo por entorno, que es un requisito
  explícito del plan.

## A quién afecta

Solo a `apps/public-web` (configuración en tiempo de ejecución y enlace de navegación). No toca
contratos, endpoints, eventos ni servicios. El valor se puede sobrescribir sin recompilar.
