# IA-front-public-web-presentacion-real-01 — ubicación de las imágenes y archivo de créditos

- **Plan:** `front-public-web-presentacion-real`
- **Paso:** 4 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-21

## Qué se encontró

[`02` §Imágenes](../../especificacion/02-landing-y-panel.md#imágenes) exige imágenes propias o licenciadas, guardadas en el repositorio, con ficha de autor, origen, licencia y fecha, y sin hotlinks. No fija la carpeta ni el nombre del archivo de créditos, y tampoco lo hace `DESIGN-public-web.md`. La landing ya usa `/assets/demo/` para placeholders, ruta que la v1.3 retira.

## Qué se decidió

Servir los assets desde `apps/public-web/public/assets/` —la única carpeta que Angular publica tal cual (`angular.json` copia `public`)— con las subcarpetas `inicio/`, `sedes/` y `habitaciones/`; el fallback neutro en `/assets/fallback.svg`; la ficha en `apps/public-web/public/assets/CREDITOS.md`; y las rutas de datos que fija `back-hotel-presentacion-real` §5. `front-admin-web-imagenes-panel` replica las carpetas y la ficha en su propia app porque cada build sirve su `public/`.

## Por qué

Cada app Angular publica su propio `public/`; no hay carpeta compartida servida por ambos builds. Mantener los mismos nombres en las dos evita que un seed apunte a una ruta que una app no tiene, y el archivo de créditos vive junto a los archivos que describe, que es lo más fácil de mantener y de revisar.

## Alternativas descartadas

- **Una carpeta compartida en la raíz del repositorio.** Ninguno de los dos builds la publica; habría que añadirla a `angular.json` en las dos apps, más invasivo y sin ventaja.
- **Enlazar desde un banco de imágenes o un CDN.** Prohibido por [`02` §Imágenes](../../especificacion/02-landing-y-panel.md#imágenes).
- **Dejar los archivos sin ficha.** La spec no admite una imagen sin créditos.

## A quién afecta

- `front-public-web-presentacion-real` y `front-admin-web-imagenes-panel`: comparten rutas y formato de ficha.
- `back-hotel-presentacion-real` y `back-inventory-imagenes-producto`: sus seeds escriben exactamente esas rutas.
- Contratos: ninguno.
