# IA-front-public-web-presentacion-real-02 — origen desde el que se recorre la landing

- **Plan:** `front-public-web-presentacion-real`
- **Paso:** 16 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-22

## Qué se encontró

El paso 16 pide recorrer las cuatro pantallas contra el Gateway y dejar el `imagenUrl` visible en el
DOM, pero no dice desde qué origen se sirve el build de producción. El `allowedOrigins` que publica
`infra-gateway-eureka` en `services/api-gateway/src/main/resources/application.yml` solo admite
`http://localhost:4200` y `http://localhost:4300`; cualquier otro puerto hace que el navegador
bloquee el `GET` público por CORS y la pantalla muestre el error de red de la app, que no es un
defecto de la landing.

## Qué se decidió

Servir el build de producción de `public-web` en `http://localhost:4200` durante el recorrido de
evidencia, sin tocar el Gateway, y dejar escrito el motivo en el paso 16 del plan.

## Por qué

`apps/public-web/**` es la única zona que este plan puede escribir. Cambiar la lista de orígenes del
Gateway es una decisión de `infra` que pertenece a `infra-gateway-eureka` y que, además, no hace
falta: el origen que el contrato ya permite es suficiente para demostrar que la landing pinta los
datos reales. Es la opción menos invasiva que no rompe ningún plan cerrado.

## Alternativas descartadas

- **Añadir `http://localhost:4400` al `allowedOrigins` del Gateway.** Toca la zona de `infra` y
  reabre un plan cerrado por una necesidad de evidencia, no por un requisito de producto.
- **Servir la landing en el puerto 4300.** Es el origen reservado al panel (`PANEL_URL`), así que
  confundiría la evidencia del enlace «Acceso al panel» con la propia landing.
- **Probar con `ng serve`.** Es el mismo origen 4200 y añade recarga en caliente; el build de
  producción demuestra mejor lo que se publica.

## A quién afecta

- Solo a la evidencia de este plan: el código de la app no cambia.
- `infra-gateway-eureka`: ninguna modificación. Su lista de orígenes se respeta tal cual.
