# IA-qa-postman-coleccion-unica-01 — una colección con carpetas en lugar de seis colecciones

- **Plan:** `qa-postman-circuit-breaker`
- **Paso:** 20 (verificación de cierre) y criterio «seis colecciones + entorno»
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-23

## Qué se encontró

El plan pedía «una colección por flujo» (`identidad`, `catalogo-hotelero`, `recepcion`, `ventas`,
`reportes` y `circuit-breaker`) y su criterio de cierre lo verificaba contando seis archivos en
`tests/postman/`. Al usarlas en Postman, importar seis JSON obliga a repetir la importación en cada
máquina, deja seis entradas en la barra lateral para el mismo proyecto y multiplica las variables:
dos colecciones escriben `{{idRecepcion}}` y `{{idHabitacion}}` con valores distintos, así que el
orden de ejecución entre colecciones deja de estar garantizado por Postman.

Además, el entorno versionado tenía valores de una demo anterior que ya no existen en los seeds
(`idHabitacionB`…`idHabitacionI` apuntaban a `312`, `313`, `305`…`310`; los seeds de
`V4__seeds_hotel.sql` solo traen `101`, `102`, `201`, `202`, `301` y `302`), `baseUrl` usaba
`localhost` y las peticiones escribían en `pm.environment`, que falla si no hay entorno activo.

## Qué se decidió

Sustituir las seis colecciones por una sola (`Hotel.postman_collection.json`) con carpetas `00` a
`09`, conservar los `pm.test` de cada petición y mover todas las variables a la colección
(`pm.collectionVariables`). El entorno queda reducido a los valores de conexión y credenciales.

## Por qué

- **Un único archivo se importa una vez** y el orden lo garantiza el Collection Runner, no la
  memoria de quien ejecuta.
- **Las variables no colisionan**: al vivir en la colección, `{{idRecepcion}}` es una sola.
- **Funciona sin entorno activo**: los `pm.environment` fallaban en cuanto no había entorno
  seleccionado, que es exactamente lo que pasaba al importar solo una colección.
- La especificación ([`06` §7](../../especificacion/06-migracion-y-pruebas.md#7-entregables-de-implementación))
  pide «colecciones Postman» y una colección del ciclo del breaker, no seis archivos.

## Cómo queda el ciclo del breaker

Postman no puede detener servicios, así que el ciclo se parte en tres carpetas consecutivas
(`06`, `07`, `08`) con la parada manual en medio. Cada tramo hace su login, resuelve sus ids y
libera el ciclo si hace falta, de modo que también se puede ejecutar suelto. El guion manual y los
comandos literales de `stop`/`start` siguen escritos en la descripción de cada tramo, como exigía
el paso 8 del plan.

## A quién afecta

- `qa-postman-circuit-breaker`: sus pasos 2 a 20 nombran archivos por flujo. La sustitución no
  cambia ninguna ruta, código ni aserción: se conservan los `pm.test` y se añaden los pasos de
  preparación.
- `docs/especificacion/06-migracion-y-pruebas.md` §7: sigue cumpliéndose (colecciones Postman y
  colección del ciclo del breaker con evidencia).
- Nadie más: las colecciones son entregable de QA, no las consume código de producción.
