# back-arreglo-sales-pago-202 — el pago con 202 no se recupera y queda atascado (F3)

| Campo | Valor |
|---|---|
| Plan | `back-arreglo-sales-pago-202` |
| Ámbito | `back` |
| Componente | `sales-service` |
| Fase | `F3` |
| Modo | `openspec` |
| Estado | `en curso` |
| Depende de | `back-sales-ventas` |
| Repara | `back-sales-ventas` |
| Spec | [`04-flujos.md` §2. Venta y stock](../especificacion/04-flujos.md#2-venta-y-stock), [`sales/api.md` §REST: ventas](../especificacion/microservicios/sales/api.md#rest-ventas), [`sales/base-de-datos.md` §Tablas](../especificacion/microservicios/sales/base-de-datos.md#tablas) y [§Funciones/procedimientos](../especificacion/microservicios/sales/base-de-datos.md#funcionesprocedimientos), [`03-contrato-comun.md` §Respuestas](../especificacion/03-contrato-comun.md#respuestas) y [§Headers](../especificacion/03-contrato-comun.md#headers), [`sales.yaml`](../contracts/openapi/sales.yaml), [`errors.md` §Inventory y Sales](../contracts/errors.md#inventory-y-sales) y [`openspec/specs/sales-ventas/spec.md`](../../openspec/specs/sales-ventas/spec.md) §Operaciones observables con recuperación cada 30 s |
| Casos | `—` |
| Change | `openspec/changes/back-arreglo-sales-pago-202/` |

## 1. Objetivo

Al cerrar el plan, un `PUT /api/venta/{id}` que responde `202` porque reception no está disponible deja la operación `PAGO` viva, y el recuperador la reintenta cada 30 s: al volver reception, la venta pasa a `PAGADO` con `paid_at` y `medio_pago` dentro de los 30 sondeos de 3 s que hace el panel, sin compensar inventario. Se comprueba repitiendo la reproducción de §3 y dejando su comando en verde.

## 2. Alcance

**Entra:** la zona del plan reparado `back-sales-ventas`, componente `sales-service`:

- `services/sales-service/src/main/java/pe/hotel/sales/domain/service/SalesService.java`: en `pagar` y `anular`, no terminar la operación ante un fallo de transporte; dejar la operación en `PREPARANDO` y devolver `202`. Solo `409`, `403` y `404` terminan la operación y devuelven su error.
- `services/sales-service/src/main/java/pe/hotel/sales/domain/repository/SalesRepository.java`: separar de `preparandoVencidas` la selección de `PAGO` y `ANULACION` en `PREPARANDO`, que se rige por `next_attempt_at`, de la de `VENTA`, que conserva `estado_operacion='PENDIENTE'` y la edad de 120 s. No cambiar `debitoVencido` ni `cancelacionesPendientes`.
- `services/sales-service/src/main/java/pe/hotel/sales/domain/service/RecuperadorVentas.java`: enrutar `PAGO` y `ANULACION` en `PREPARANDO` a `reintentarAdmision` en vez de rechazar la venta; si la venta ya está `PAGADO` o `CANCELADA`, terminar la operación y asegurar el finalizar sin nueva admisión.
- `services/sales-service/src/test/java/pe/hotel/sales/**`: pruebas de regresión del `202` con reception caído, de la selección de `PAGO`/`ANULACION` en `PREPARANDO`, del rechazo de la venta alta vieja y de la ausencia de doble pago o doble finalizar.
- `docs/plans/back-arreglo-sales-pago-202.md`, su fila en `docs/plans/catalogo.md` y los artefactos `openspec/changes/back-arreglo-sales-pago-202/`.

**Fuera de alcance:**

- `docs/contracts/**` y `docs/especificacion/**`: no se modifican. El `202` con `Location`/`Retry-After: 2`, los estados `PREPARANDO`/`ADMITIDA`/`TERMINAL` y los códigos ya están publicados.
- `reception-service`, `inventory-service`, `api-gateway`, `discovery-server` e `infra/**`: no se tocan. El arreglo no cambia la admisión de reception ni el débito de inventory.
- Migraciones Flyway, tablas, columnas, índices y seeds: no hay cambio de esquema; se usan `operacion_venta.estado`, `operacion_venta.next_attempt_at`, `operacion_venta.attempts` y `venta.current_operation_id`, que ya existen.
- El resto de `sales-service`: alta de venta, débito, compensación, cierre de consumos, carrito, catálogo privado y lectura de operaciones no cambian.
- `apps/admin-web/**` y la evidencia del plan `front-admin-web-ventas`: la transcripción y el script se citan, no se copian a `main`.

## 3. Contexto

El defecto lo introdujo `back-sales-ventas`, plan ya `cerrado`, en el PR #100 (commit `775170c`, «feat: implementa el plan back-sales-ventas (#100)»). Su zona es `services/sales-service/**`, exactamente la zona de este arreglo. Lo detectó la ejecución de `front-admin-web-ventas` al hacer la integración real por el Gateway; su paso 19 quedó sin marcar y su evidencia vive en `origin/front-admin-web-ventas`, no en `main`: `apps/admin-web/tests/evidence/integracion-pago-202.py` y `apps/admin-web/tests/evidence/front-admin-web-ventas-integracion.txt`.

La reproducción se ejecutó desde `main` (`803a9f1`) el 2026-10-20, con los siete servicios arriba, `reception-service` en `8083`, `sales-service` en `8085`, el Gateway en `8080` y el JWT de `admin@hotel.test` (ADMIN). Se usó el mismo flujo del script, con `scripts.qa.common`, porque el archivo vive en la rama del front.

**Reproducción 1 — el 202 de pago no llega nunca a `PAGADO`.**

```text
=== 202 de pago con recuperacion real (reception-service detenido) ===
[venta crear] HTTP 201
{"success":true,"message":"OK","data":{"idVenta":25,"idRecepcion":22,"idCliente":100,"idHotel":1,"total":8.00,"estado":"PENDIENTE","estadoOperacion":"CONFIRMADA","operationId":"f6ca5cb6-120e-4716-a358-c6fcbf583525","medioPago":null,"version":2,...,"paidAt":null,...}}
[detalle] version 2 estadoOperacion CONFIRMADA
Deteniendo reception-service...
reception detenido; puerto 8083 abierto: False
[pago con reception caido] HTTP 202 Location /api/venta/buscar/25 Retry-After 2
{"success":true,"message":"OK","data":{"idVenta":25,...,"estado":"PENDIENTE","estadoOperacion":"CONFIRMADA","operationId":"82620b32-bd8a-4eb6-81d3-c50b1518026a","medioPago":null,"version":2,...}}
Reiniciando reception-service...
reception listo.
[polling 1] HTTP 200 estado=PENDIENTE estadoOperacion=CONFIRMADA paidAt=None
[polling 2] HTTP 200 estado=PENDIENTE estadoOperacion=CONFIRMADA paidAt=None
...
[polling 30] HTTP 200 estado=PENDIENTE estadoOperacion=CONFIRMADA paidAt=None
El pago no llego a PAGADO en el tiempo de sondeo.
```

La transcripción original `front-admin-web-ventas-integracion.txt` muestra el mismo resultado con la venta 24 y 30 sondeos idénticos: `estado=PENDIENTE estadoOperacion=CONFIRMADA paidAt=None`, «El pago no llegó a PAGADO en el tiempo de sondeo.»

**Reproducción 2 — la operación de pago ya está `TERMINAL` y la consulta de recuperación no la ve.**

```text
$ docker exec hotel-postgres-1 psql -U postgres -d sales_db -c \
    "select o.operation_id, o.id_venta, o.tipo, o.estado, o.attempts, o.next_attempt_at, v.estado, v.estado_operacion, v.version from operacion_venta o join venta v on v.id=o.id_venta where o.id_venta=25;"

             operation_id             | id_venta | tipo  |  estado  | attempts |        next_attempt_at        |  estado   | estado_operacion | version
--------------------------------------+----------+-------+----------+----------+-------------------------------+-----------+------------------+---------
 f6ca5cb6-120e-4716-a358-c6fcbf583525 |       25 | VENTA | TERMINAL |        1 | 2026-10-20 23:17:50.968191+00 | PENDIENTE | CONFIRMADA       |       2
 82620b32-bd8a-4eb6-81d3-c50b1518026a |       25 | PAGO  | TERMINAL |        0 | 2026-10-20 23:17:22.721873+00 | PENDIENTE | CONFIRMADA       |       2

$ docker exec hotel-postgres-1 psql -U postgres -d sales_db -c \
    "SELECT v.id, o.operation_id, o.tipo, v.estado_operacion FROM venta v JOIN operacion_venta o ON o.operation_id = v.current_operation_id WHERE v.estado_operacion = 'PENDIENTE' AND o.estado = 'PREPARANDO' AND o.created_at < now() - (120 * interval '1 second');"

 id | operation_id | tipo | estado_operacion
----+--------------+------+------------------
(0 rows)
```

**Causa 1 — el `catch` de `pagar` termina la operación antes de responder `202`.** En `SalesService.pagar` (líneas 402-416) cualquier `FeignException` ejecuta `repo.terminarOperacionSiCoincide(operationId, idVenta)` y después, si no es `409`/`403`/`404`, devuelve `202`. La operación queda `TERMINAL` (`attempts=0`), así que ningún recuperador puede reintentarla. El comentario «Operación durable en recuperación» describe una intención que el código no cumple. `SalesService.anular` tiene el mismo `catch` (líneas 509-523) y el mismo defecto para `ANULACION`.

**Causa 2 — `preparandoVencidas` solo mira ventas `PENDIENTE`.** En `SalesRepository.preparandoVencidas` (líneas 568-583) el `WHERE` exige `v.estado_operacion = 'PENDIENTE'` y `o.created_at < now() - 120 s`. Un `PAGO` se crea sobre una venta `CONFIRMADA` o `PENDIENTE`; en el caso reproducido la venta ya está `CONFIRMADA`, así que la consulta nunca la devuelve. Aun corrigiendo la causa 1, la edad de 120 s sobre `created_at` retrasa la primera recuperación a entre 120 y 150 s (el job corre cada 30 s), mientras el sondeo del panel termina a los ~90 s de los 30 intentos de 3 s: la operación creada a las `23:17:22.721873+00` no cumple los 120 s hasta las `23:19:22`, y el sondeo de 90 s termina antes. Para `PAGO` y `ANULACION` la recuperación debe regirse por `next_attempt_at`, que ya existe y arranca en `now()`.

**Qué fija la spec.** [`04-flujos.md` §2](../especificacion/04-flujos.md#2-venta-y-stock) dice: «La recuperación revisa también operacion_venta, no solo Venta.estadoOperacion. Una operación PAGO en PREPARANDO/ADMITIDA reintenta la misma admisión y pago local; si ya pagó, solo asegura el outbox de finalizar. No compensa inventario por un timeout de pago. ANULACION reanuda su CAS/restitución; rechazo de admisión termina la operación sin tocar dinero/stock.» El mismo apartado fija que la venta alta `PENDIENTE` con operación `PREPARANDO` de más de 120 s se rechaza, y [`sales/api.md` §REST: ventas](../especificacion/microservicios/sales/api.md#rest-ventas) fija el `202` con `Location` y `Retry-After: 2`. No hay regla nueva que decidir: el arreglo alinea el código con lo ya publicado y con el change archivado `back-sales-ventas`.

## 4. Pasos

- [ ] 1. Reproducir el fallo desde `main` con el flujo de §3: crear una venta `PENDIENTE`, detener `reception-service`, pagar y observar el `202`, reiniciar reception y sondear 30 veces cada 3 s. Verificación: la venta sigue `PENDIENTE`/`CONFIRMADA`, `paidAt` es `null` y la operación `PAGO` queda `TERMINAL`; la consulta de `preparandoVencidas` devuelve 0 filas.
- [ ] 2. Añadir las pruebas de regresión que fallan en `main`: el pago con la admisión simulada como `503` devuelve `202` y deja la operación `PREPARANDO`; una operación `PAGO` en `PREPARANDO` sobre una venta `CONFIRMADA` con `next_attempt_at` vencido es seleccionada por el recuperador; una operación `VENTA` vieja sigue seleccionándose para rechazo. Verificación: `bash scripts/mvn.sh -f services/pom.xml -pl sales-service test` falla en esas pruebas antes del cambio.
- [ ] 3. Corregir `SalesService.pagar`: mover `terminarOperacionSiCoincide` a cada rama de rechazo (`409`, `403`, `404`); en timeout, `503` o error de red no terminarla, dejar `PREPARANDO` y devolver `202` con la venta actual. No cambiar el `200` del pago aplicado, el `202` de una operación en curso ni los encabezados. Verificación: la prueba de `503` queda en verde; un `409` sigue devolviendo `STAY_NOT_ACTIVE` y termina la operación.
- [ ] 4. Aplicar el mismo tratamiento en `SalesService.anular`: solo `409`, `403` y `404` terminan la operación; un fallo de transporte la deja `PREPARANDO` y responde `202`. Verificación: la prueba de anulación con `503` queda en verde; la anulación normal sigue devolviendo `202` y terminando en `CANCELADA` cuando llega la restitución.
- [ ] 5. Separar en `SalesRepository` la consulta de operaciones `PREPARANDO`: `VENTA` conserva `v.estado_operacion='PENDIENTE'` y `o.created_at < now() - (? * interval '1 second')`; `PAGO` se selecciona con `v.estado='PENDIENTE'` y `v.estado_operacion IN ('PENDIENTE','CONFIRMADA')` y `o.next_attempt_at <= now()`; `ANULACION` con `v.estado_operacion IN ('PENDIENTE','CONFIRMADA')` y `o.next_attempt_at <= now()`. Mantener el `JOIN` por `current_operation_id` y no tocar `debitoVencido` ni `cancelacionesPendientes`. Verificación: la prueba de repositorio devuelve el `PAGO` de la venta `CONFIRMADA` y no devuelve el `PAGO` de una venta ya `PAGADO`; la `VENTA` vieja sigue en la lista de rechazo.
- [ ] 6. Corregir `RecuperadorVentas`: el método que hoy se llama `rechazarPreparandoVencidas` pasa a recuperar por tipo; para `PAGO` y `ANULACION` llama a `reintentarAdmision` y nunca a `rechazarVenta`. En `reintentarAdmision`, si la venta ya está `PAGADO` (para `PAGO`) o CANCELADA (para `ANULACION`), terminar la operación y asegurar el `recepcion.operacion-finalizar` sin volver a admitir; si la anulación está en CANCELACION_PENDIENTE, no volver a admitir y dejar que `cancelacionesPendientes` republique la restitución. Verificación: el recuperador deja la venta `PAGADO` en el primer ciclo con reception arriba; una venta pagada por el cierre termina la operación sin segunda admisión ni segundo finalizar.
- [ ] 7. Verificar la idempotencia: ejecutar el recuperador dos veces seguidas sobre la misma operación `PAGO` y comprobar que `pagarVenta` gana un solo CAS, que `paid_at` no cambia y que hay una sola fila de `sales.venta.v1` y una sola de `recepcion.operacion-finalizar` con `tipo=PAGO` en `outbox`. Verificación: los conteos de `outbox` y el `version` de la venta son los esperados; no aparece `stock.restituir` por el pago.
- [ ] 8. Reiniciar `sales-service` desde la rama corregida y repetir la reproducción de §3 con el script o su flujo equivalente. Verificación: el `202` conserva `Location` y `Retry-After: 2`; el sondeo llega a `PAGADO` antes del sondeo 30; `paidAt` y `medioPago` quedan registrados; la operación `PAGO` queda `TERMINAL` y la venta tiene una sola operación `PAGO`.
- [ ] 9. Comprobar que el pago no movió inventario ni compensó stock: `producto_sede.cantidad` de la sede 1 no cambia por el pago, `movimiento_venta` no gana una restitución por ese `idVenta` y el log no muestra `stock_restituido` ni una llamada a `inventory` por el pago. Verificación: las consultas de §6 y el log de `sales-service` no muestran compensación.
- [ ] 10. Correr los comandos canónicos: `bash scripts/mvn.sh -f services/pom.xml -pl sales-service test`, `bash scripts/mvn.sh -f services/pom.xml verify`, `python scripts/validar-planes.py`, `python scripts/validar-contratos.py --base origin/main` y `npx openspec validate back-arreglo-sales-pago-202 --strict`. Verificación: pruebas y build en `BUILD SUCCESS`, 0 errores del validador, «Los contratos solo se han ampliado. Correcto.» y change válido.
- [ ] 11. Guardar en §6 las salidas de los pasos 1, 2, 6, 7, 8, 9 y 10, marcar todos los pasos y cambiar `Estado` a `cerrado` en el último commit. Verificación: no quedan pasos sin marcar y la reproducción original está en verde.

## 5. Contratos afectados

- **OpenAPI:** no se modifica `docs/contracts/openapi/sales.yaml`. Se implementan `PUT /api/venta/{id}` con `200` o `202` y `Location`/`Retry-After: 2`, y `GET /api/venta/operaciones/{operationId}` con la forma ya publicada. No se añade, cambia ni elimina ninguna ruta, parámetro, schema, `operationId` ni código.
- **Eventos y comandos:** no se añaden ni cambian `sales.venta.v1` ni `recepcion.operacion-finalizar`. La recuperación de un `PAGO` emite un solo snapshot y un solo finalizar con `tipo=PAGO` y `resultado=PAGO_REGISTRADO`; no emite `stock.restituir` por un timeout de pago. No se tocan `docs/contracts/events/` ni el catálogo de mensajería.
- **Persistencia:** ninguna migración ni cambio de tabla. Se usan las columnas existentes `venta.estado`, `venta.estado_operacion`, `venta.current_operation_id`, `venta.version`, `operacion_venta.estado`, `operacion_venta.next_attempt_at` y `operacion_venta.attempts`; el índice único `uq_operacion_venta_en_curso` sigue garantizando una sola operación no terminal por venta.
- **Errores:** se usan los códigos ya publicados en [`errors.md`](../contracts/errors.md): `STAY_NOT_ACTIVE` (409), `FORBIDDEN` (403), `RESOURCE_NOT_FOUND` (404) y `DEPENDENCY_UNAVAILABLE` (503). No se añade ningún código; el `202` no es un error.
- **Seguridad:** no se cambia el validador JWT, la audiencia, el issuer ni los scopes. La identidad sigue saliendo del token validado y no se aceptan cabeceras de identidad.

## 6. Pruebas

### Reproducción antes del arreglo

Ejecutada el 2026-10-20 sobre `main` (`803a9f1`), con `reception-service` detenido y el JWT de `admin@hotel.test`. Salida literal de §3: `PUT /api/venta/{id}` → `HTTP 202`, `Location: /api/venta/buscar/25`, `Retry-After: 2`; los 30 sondeos devuelven `estado=PENDIENTE`, `estadoOperacion=CONFIRMADA`, `paidAt=None`; la operación `PAGO` queda `TERMINAL` con `attempts=0`; la consulta de `preparandoVencidas` devuelve 0 filas. La transcripción `front-admin-web-ventas-integracion.txt` de la rama `front-admin-web-ventas` reproduce el mismo resultado con la venta 24. Evidencia: esta salida y la transcripción citada; sin tokens.

### Verificación después del arreglo

El comando original vive en la rama del front. Para ejecutarlo sobre la rama del arreglo, sin copiarlo a `main`:

```bash
mkdir -p apps/admin-web/tests/evidence
git show origin/front-admin-web-ventas:apps/admin-web/tests/evidence/integracion-pago-202.py \
  > apps/admin-web/tests/evidence/integracion-pago-202.py
python apps/admin-web/tests/evidence/integracion-pago-202.py
rm -f apps/admin-web/tests/evidence/integracion-pago-202.py
```

Con `sales-service` reiniciado desde el código corregido y la estadía 22 `ACTIVA`, el script crea una venta nueva, detiene reception, paga, la reinicia y sondea. Resultado esperado: `HTTP 202` con `Location` y `Retry-After: 2`; algún sondeo devuelve `estado=PAGADO` con `paidAt` no nulo y el script termina con `{"idVenta": <n>, "resultado": "PAGADO tras 202"}` y código 0, antes del sondeo 30. Evidencia: la salida completa en el PR de ejecución y en §6 de este plan.

Consultas de apoyo:

```bash
docker exec hotel-postgres-1 psql -U postgres -d sales_db -c \
  "select v.id, v.estado, v.estado_operacion, v.medio_pago, v.paid_at, o.tipo, o.estado, o.attempts from venta v join operacion_venta o on o.id_venta=v.id where v.id=<idVenta> order by o.tipo;"
docker exec hotel-postgres-1 psql -U postgres -d sales_db -c \
  "select channel, aggregate_id, published_at from outbox where aggregate_id in (select id::text from venta where id=<idVenta>) order by channel;"
```

Resultado esperado: una fila `PAGADO`/`CONFIRMADA` con `medio_pago` y `paid_at`; una sola operación `PAGO` en `TERMINAL`; una sola fila de `recepcion.operacion-finalizar` con `tipo=PAGO`; ninguna fila de `stock.restituir` por el pago. En `inventory_db`, `producto_sede.cantidad` de la sede 1 no cambia por el pago.

### Pruebas automatizadas

```bash
bash scripts/mvn.sh -f services/pom.xml -pl sales-service test
```

Resultado esperado: las pruebas nuevas de `SalesService`, `SalesRepository` y `RecuperadorVentas` en verde, además de las existentes. Evidencia: `services/sales-service/target/surefire-reports/` y la salida del comando adjunta al PR.

### Comandos canónicos

```bash
bash scripts/mvn.sh -f services/pom.xml verify
python scripts/validar-planes.py
python scripts/validar-contratos.py --base origin/main
npx openspec validate back-arreglo-sales-pago-202 --strict
```

Resultado esperado: `BUILD SUCCESS` de los nueve módulos; 0 errores del validador de planes; «Los contratos solo se han ampliado. Correcto.»; `Change 'back-arreglo-sales-pago-202' is valid`.

## 7. Preguntas abiertas

Ninguna. La recuperación de `PAGO` y `ANULACION` en `PREPARANDO` está fijada por [`04-flujos.md` §2](../especificacion/04-flujos.md#2-venta-y-stock), el `202` con `Location`/`Retry-After` por [`sales/api.md` §REST: ventas](../especificacion/microservicios/sales/api.md#rest-ventas), y `next_attempt_at` ya existe en `operacion_venta` ([`sales/base-de-datos.md` §Tablas](../especificacion/microservicios/sales/base-de-datos.md#tablas)). No se decide ningún estado, código ni valor nuevo.

## 8. Criterio de terminado

- [ ] Todos los pasos de §4 marcados con su verificación.
- [ ] Un `PUT /api/venta/{id}` con reception caído deja la operación `PAGO` en `PREPARANDO` y responde `202`; al volver reception, la venta llega a `PAGADO` dentro de los 30 sondeos de 3 s, con `paid_at` y `medio_pago`.
- [ ] La operación `PAGO` termina `TERMINAL` una sola vez y no se emite un segundo `recepcion.operacion-finalizar` ni un `stock.restituir` por el pago.
- [ ] La venta alta `PENDIENTE` con operación `VENTA` en `PREPARANDO` de más de 120 s sigue rechazándose sin stock; DEBITO_SOLICITADO y CANCELACION_PENDIENTE conservan su recuperación.
- [ ] `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS`; `python scripts/validar-planes.py` con 0 errores; `python scripts/validar-contratos.py --base origin/main` sin cambios de contrato; `npx openspec validate back-arreglo-sales-pago-202 --strict` sin errores.
- [ ] La reproducción original de §3 está en verde con evidencia adjunta al PR y sin preguntas abiertas.
