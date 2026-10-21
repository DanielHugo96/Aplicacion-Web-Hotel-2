# back-arreglo-sales-pago-202 — el pago con 202 no se recupera y queda atascado (F3)

| Campo | Valor |
|---|---|
| Plan | `back-arreglo-sales-pago-202` |
| Ámbito | `back` |
| Componente | `sales-service` |
| Fase | `F3` |
| Modo | `openspec` |
| Estado | `cerrado` |
| Depende de | `back-sales-ventas` |
| Repara | `back-sales-ventas` |
| Spec | [`04-flujos.md` §2. Venta y stock](../especificacion/04-flujos.md#2-venta-y-stock), [`sales/api.md` §REST: ventas](../especificacion/microservicios/sales/api.md#rest-ventas), [`sales/base-de-datos.md` §Tablas](../especificacion/microservicios/sales/base-de-datos.md#tablas) y [§Funciones/procedimientos](../especificacion/microservicios/sales/base-de-datos.md#funcionesprocedimientos), [`03-contrato-comun.md` §Respuestas](../especificacion/03-contrato-comun.md#respuestas) y [§Headers](../especificacion/03-contrato-comun.md#headers), [`sales.yaml`](../contracts/openapi/sales.yaml), [`errors.md` §Inventory y Sales](../contracts/errors.md#inventory-y-sales) y [`openspec/specs/sales-ventas/spec.md`](../../openspec/specs/sales-ventas/spec.md) §Operaciones observables con recuperación cada 30 s |
| Casos | `—` |
| Change | `openspec/changes/archive/2026-10-20-back-arreglo-sales-pago-202/` |

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

- [x] 1. Reproducir el fallo desde `main` con el flujo de §3: crear una venta `PENDIENTE`, detener `reception-service`, pagar y observar el `202`, reiniciar reception y sondear 30 veces cada 3 s. Verificación: la venta sigue `PENDIENTE`/`CONFIRMADA`, `paidAt` es `null` y la operación `PAGO` queda `TERMINAL`; la consulta de `preparandoVencidas` devuelve 0 filas.
- [x] 2. Añadir las pruebas de regresión que fallan en `main`: el pago con la admisión simulada como `503` devuelve `202` y deja la operación `PREPARANDO`; una operación `PAGO` en `PREPARANDO` sobre una venta `CONFIRMADA` con `next_attempt_at` vencido es seleccionada por el recuperador; una operación `VENTA` vieja sigue seleccionándose para rechazo. Verificación: `bash scripts/mvn.sh -f services/pom.xml -pl sales-service test` falla en esas pruebas antes del cambio.
- [x] 3. Corregir `SalesService.pagar`: mover `terminarOperacionSiCoincide` a cada rama de rechazo (`409`, `403`, `404`); en timeout, `503` o error de red no terminarla, dejar `PREPARANDO` y devolver `202` con la venta actual. No cambiar el `200` del pago aplicado, el `202` de una operación en curso ni los encabezados. Verificación: la prueba de `503` queda en verde; un `409` sigue devolviendo `STAY_NOT_ACTIVE` y termina la operación.
- [x] 4. Aplicar el mismo tratamiento en `SalesService.anular`: solo `409`, `403` y `404` terminan la operación; un fallo de transporte la deja `PREPARANDO` y responde `202`. Verificación: la prueba de anulación con `503` queda en verde; la anulación normal sigue devolviendo `202` y terminando en `CANCELADA` cuando llega la restitución.
- [x] 5. Separar en `SalesRepository` la consulta de operaciones `PREPARANDO`: `VENTA` conserva `v.estado_operacion='PENDIENTE'` y `o.created_at < now() - (? * interval '1 second')`; `PAGO` se selecciona con `v.estado='PENDIENTE'` y `v.estado_operacion IN ('PENDIENTE','CONFIRMADA')` y `o.next_attempt_at <= now()`; `ANULACION` con `v.estado_operacion IN ('PENDIENTE','CONFIRMADA')` y `o.next_attempt_at <= now()`. Mantener el `JOIN` por `current_operation_id` y no tocar `debitoVencido` ni `cancelacionesPendientes`. Verificación: la prueba de repositorio devuelve el `PAGO` de la venta `CONFIRMADA` y no devuelve el `PAGO` de una venta ya `PAGADO`; la `VENTA` vieja sigue en la lista de rechazo.
- [x] 6. Corregir `RecuperadorVentas`: el método que hoy se llama `rechazarPreparandoVencidas` pasa a recuperar por tipo; para `PAGO` y `ANULACION` llama a `reintentarAdmision` y nunca a `rechazarVenta`. En `reintentarAdmision`, si la venta ya está `PAGADO` (para `PAGO`) o CANCELADA (para `ANULACION`), terminar la operación y asegurar el `recepcion.operacion-finalizar` sin volver a admitir; si la anulación está en CANCELACION_PENDIENTE, no volver a admitir y dejar que `cancelacionesPendientes` republique la restitución. Verificación: el recuperador deja la venta `PAGADO` en el primer ciclo con reception arriba; una venta pagada por el cierre termina la operación sin segunda admisión ni segundo finalizar.
- [x] 7. Verificar la idempotencia: ejecutar el recuperador dos veces seguidas sobre la misma operación `PAGO` y comprobar que `pagarVenta` gana un solo CAS, que `paid_at` no cambia y que hay una sola fila de `sales.venta.v1` y una sola de `recepcion.operacion-finalizar` con `tipo=PAGO` en `outbox`. Verificación: los conteos de `outbox` y el `version` de la venta son los esperados; no aparece `stock.restituir` por el pago.
- [x] 8. Reiniciar `sales-service` desde la rama corregida y repetir la reproducción de §3 con el script o su flujo equivalente. Verificación: el `202` conserva `Location` y `Retry-After: 2`; el sondeo llega a `PAGADO` antes del sondeo 30; `paidAt` y `medioPago` quedan registrados; la operación `PAGO` queda `TERMINAL` y la venta tiene una sola operación `PAGO`.
- [x] 9. Comprobar que el pago no movió inventario ni compensó stock: `producto_sede.cantidad` de la sede 1 no cambia por el pago, `movimiento_venta` no gana una restitución por ese `idVenta` y el log no muestra `stock_restituido` ni una llamada a `inventory` por el pago. Verificación: las consultas de §6 y el log de `sales-service` no muestran compensación.
- [x] 10. Correr los comandos canónicos: `bash scripts/mvn.sh -f services/pom.xml -pl sales-service test`, `bash scripts/mvn.sh -f services/pom.xml verify`, `python scripts/validar-planes.py`, `python scripts/validar-contratos.py --base origin/main` y `npx openspec validate back-arreglo-sales-pago-202 --strict`. Verificación: pruebas y build en `BUILD SUCCESS`, 0 errores del validador, «Los contratos solo se han ampliado. Correcto.» y change válido.
- [x] 11. Guardar en §6 las salidas de los pasos 1, 2, 6, 7, 8, 9 y 10, marcar todos los pasos y cambiar `Estado` a `cerrado` en el último commit. Verificación: no quedan pasos sin marcar y la reproducción original está en verde.

## 5. Contratos afectados

- **OpenAPI:** no se modifica `docs/contracts/openapi/sales.yaml`. Se implementan `PUT /api/venta/{id}` con `200` o `202` y `Location`/`Retry-After: 2`, y `GET /api/venta/operaciones/{operationId}` con la forma ya publicada. No se añade, cambia ni elimina ninguna ruta, parámetro, schema, `operationId` ni código.
- **Eventos y comandos:** no se añaden ni cambian `sales.venta.v1` ni `recepcion.operacion-finalizar`. La recuperación de un `PAGO` emite un solo snapshot y un solo finalizar con `tipo=PAGO` y `resultado=PAGO_REGISTRADO`; no emite `stock.restituir` por un timeout de pago. No se tocan `docs/contracts/events/` ni el catálogo de mensajería.
- **Persistencia:** ninguna migración ni cambio de tabla. Se usan las columnas existentes `venta.estado`, `venta.estado_operacion`, `venta.current_operation_id`, `venta.version`, `operacion_venta.estado`, `operacion_venta.next_attempt_at` y `operacion_venta.attempts`; el índice único `uq_operacion_venta_en_curso` sigue garantizando una sola operación no terminal por venta.
- **Errores:** se usan los códigos ya publicados en [`errors.md`](../contracts/errors.md): `STAY_NOT_ACTIVE` (409), `FORBIDDEN` (403), `RESOURCE_NOT_FOUND` (404) y `DEPENDENCY_UNAVAILABLE` (503). No se añade ningún código; el `202` no es un error.
- **Seguridad:** no se cambia el validador JWT, la audiencia, el issuer ni los scopes. La identidad sigue saliendo del token validado y no se aceptan cabeceras de identidad.

## 6. Pruebas

### Reproducción antes del arreglo (paso 1)

Ejecutada el 2026-10-20 sobre la rama del arreglo con el código de `main` (`e75bcde`), con
`reception-service` detenido y el JWT de `admin@hotel.test`. El script es el de la rama del front y
no se copia a `main`:

```bash
mkdir -p apps/admin-web/tests/evidence
git show origin/front-admin-web-ventas:apps/admin-web/tests/evidence/integracion-pago-202.py \
  > apps/admin-web/tests/evidence/integracion-pago-202.py
python apps/admin-web/tests/evidence/integracion-pago-202.py
```

Salida literal (recortada en los 30 sondeos, que son idénticos):

```text
=== 202 de pago con recuperacion real (reception-service detenido) ===

[venta crear para pago 202] HTTP 201
  {"success":true,"message":"OK","data":{"idVenta":27,"idRecepcion":22,"idCliente":100,"idHotel":1,"total":8.00,"estado":"PENDIENTE","estadoOperacion":"CONFIRMADA","operationId":"92e19dc8-657d-4bbe-922d-ecfd3da99573","medioPago":null,"version":2,"fechaCreacion":"2026-10-21T00:39:37.394729Z","paidAt":null,...}}

Deteniendo reception-service para que la admisión no se confirme…
reception-service detenido; puerto 8083 cerrado.

[pago con reception caído (202 esperado)] HTTP 202 · Location=/api/venta/buscar/27 Retry-After=2
  {"success":true,"message":"OK","data":{"idVenta":27,...,"estado":"PENDIENTE","estadoOperacion":"CONFIRMADA","operationId":"a796e675-b0c3-4c9b-9884-6fcf82d35e28","medioPago":null,"version":2,...,"paidAt":null,...}}

Reiniciando reception-service…
reception-service listo otra vez.
[polling pago 202 1] HTTP 200 · estado=PENDIENTE estadoOperacion=CONFIRMADA paidAt=None
[polling pago 202 2] HTTP 200 · estado=PENDIENTE estadoOperacion=CONFIRMADA paidAt=None
...
[polling pago 202 30] HTTP 200 · estado=PENDIENTE estadoOperacion=CONFIRMADA paidAt=None
El pago no llegó a PAGADO en el tiempo de sondeo.
```

El script termina con código 1. Consultas a `sales_db` de esa venta:

```text
$ docker exec hotel-postgres-1 psql -U postgres -d sales_db -c \
    "select o.operation_id, o.id_venta, o.tipo, o.estado, o.attempts, o.next_attempt_at, v.estado, v.estado_operacion, v.version from operacion_venta o join venta v on v.id=o.id_venta where o.id_venta=27 order by o.tipo;"

             operation_id             | id_venta | tipo  |  estado  | attempts |        next_attempt_at        |  estado   | estado_operacion | version
--------------------------------------+----------+-------+----------+----------+-------------------------------+-----------+------------------+---------
 a796e675-b0c3-4c9b-9884-6fcf82d35e28 |       27 | PAGO  | TERMINAL |        0 | 2026-10-21 00:39:38.69625+00  | PENDIENTE | CONFIRMADA       |       2
 92e19dc8-657d-4bbe-922d-ecfd3da99573 |       27 | VENTA | TERMINAL |        1 | 2026-10-21 00:40:07.555636+00 | PENDIENTE | CONFIRMADA       |       2

$ docker exec hotel-postgres-1 psql -U postgres -d sales_db -c \
    "SELECT v.id, o.operation_id, o.tipo, v.estado_operacion FROM venta v JOIN operacion_venta o ON o.operation_id = v.current_operation_id WHERE v.estado_operacion = 'PENDIENTE' AND o.estado = 'PREPARANDO' AND o.created_at < now() - (120 * interval '1 second');"

 id | operation_id | tipo | estado_operacion
----+--------------+------+------------------
(0 rows)
```

La operación `PAGO` queda `TERMINAL` con `attempts=0` y la consulta de la recuperación devuelve 0
filas. La transcripción `front-admin-web-ventas-integracion.txt` de la rama `front-admin-web-ventas`
reproduce el mismo resultado. Sin tokens en la evidencia.

### Pruebas de regresión que fallan antes del cambio (paso 2)

```bash
bash scripts/mvn.sh -f services/pom.xml -pl sales-service test \
  -Dtest='SalesRepositoryRecuperacionTests,SalesServicePagoTests,RecuperadorVentasTests'
```

```text
[ERROR] Tests run: 5, Failures: 2, Errors: 0, Skipped: 0 -- in pe.hotel.sales.domain.repository.SalesRepositoryRecuperacionTests
[ERROR] Tests run: 5, Failures: 3, Errors: 0, Skipped: 0 -- in pe.hotel.sales.domain.service.RecuperadorVentasTests
[ERROR] Tests run: 4, Failures: 2, Errors: 0, Skipped: 0 -- in pe.hotel.sales.domain.service.SalesServicePagoTests
[ERROR] Tests run: 14, Failures: 7, Errors: 0, Skipped: 0

[ERROR]   SalesRepositoryRecuperacionTests.elPagoEnPreparandoSobreVentaConfirmadaSeSelecciona:44 [el PAGO en PREPARANDO con next_attempt_at vencido se recupera]
[ERROR]   SalesRepositoryRecuperacionTests.laAnulacionEnPreparandoSeSeleccionaPorNextAttemptAt:69
[ERROR]   RecuperadorVentasTests.elPagoDeUnaVentaQueYaPagoTerminaLaOperacionSinAdmitirNiCobrar:90
[ERROR]   RecuperadorVentasTests.laAnulacionDeUnaVentaYaCanceladaTerminaLaOperacionSinAdmitir:128
[ERROR]   RecuperadorVentasTests.laAnulacionEnCancelacionPendienteNoVuelveAdmitirNiRechaza:110
[ERROR]   SalesServicePagoTests.elPagoConAdmissionCaidaDevuelve202YDejaLaOperacionPreparando:77 [la operación PAGO sigue viva para que la recuperación la reintente]
[ERROR]   SalesServicePagoTests.laAnulacionConAdmissionCaidaDevuelve202YDejaLaOperacionPreparando:106 [la operación ANULACION sigue viva para que la recuperación la reintente]

[ERROR]   SalesServicePagoTests.elPagoConAdmissionCaidaDevuelve202YDejaLaOperacionPreparando:77
expected: "PREPARANDO"
 but was: "TERMINAL"
[INFO] BUILD FAILURE
```

Las siete pruebas que fallan son exactamente las del defecto: la operación `PAGO`/`ANULACION` que
`main` termina en el `catch`, la selección de la recuperación que no devuelve el `PAGO` ni la
`ANULACION` en `PREPARANDO` y los guards que `main` no tiene.

### Pruebas automatizadas después del arreglo (pasos 3, 4, 5 y 6)

```bash
bash scripts/mvn.sh -f services/pom.xml -pl sales-service test
```

```text
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in pe.hotel.sales.domain.repository.SalesRepositoryRecuperacionTests
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0 -- in pe.hotel.sales.domain.service.RecuperadorVentasTests
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in pe.hotel.sales.domain.service.SalesServicePagoTests
[INFO] Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

`RecuperadorVentasTests` fija el comportamiento del recuperador: el `PAGO` en `PREPARANDO` sobre
una venta `CONFIRMADA` se reintenta y termina `PAGADO` con un solo `recepcion.operacion-finalizar`
de tipo `PAGO`; la venta alta `VENTA` vieja se rechaza sin stock y sin admitir; la venta que ya pagó
termina la operación sin volver a admitir ni a cobrar; la anulación en CANCELACION_PENDIENTE no
vuelve a admitir ni rechaza la venta. `SalesRepositoryRecuperacionTests` fija la selección: el
`PAGO` en `PREPARANDO` de una venta `CONFIRMADA` se selecciona, el de una venta ya `PAGADO` no, y la
venta alta vieja se sigue seleccionando para rechazo. `SalesServicePagoTests` fija que un `503` en
la admisión devuelve `202` y deja la operación `PREPARANDO`, y que un `409` sigue devolviendo
`STAY_NOT_ACTIVE` y termina la operación. Salida completa en
`services/sales-service/target/surefire-reports/`.

### Verificación después del arreglo (pasos 7, 8 y 9)

Con `sales-service` reiniciado desde la rama corregida y la estadía 22 `ACTIVA`, el script crea una
venta nueva, detiene reception, paga, la reinicia y sondea:

```bash
python apps/admin-web/tests/evidence/integracion-pago-202.py
```

```text
=== 202 de pago con recuperacion real (reception-service detenido) ===

[venta crear para pago 202] HTTP 201
  {"success":true,"message":"OK","data":{"idVenta":28,"idRecepcion":22,"idCliente":100,"idHotel":1,"total":8.00,"estado":"PENDIENTE","estadoOperacion":"CONFIRMADA","operationId":"5f80a88e-e2c9-4e5e-97d7-41a20de05f97","medioPago":null,"version":2,...,"paidAt":null,...}}

Deteniendo reception-service para que la admisión no se confirme…
reception-service detenido; puerto 8083 cerrado.

[pago con reception caído (202 esperado)] HTTP 202 · Location=/api/venta/buscar/28 Retry-After=2
  {"success":true,"message":"OK","data":{"idVenta":28,...,"estado":"PENDIENTE","estadoOperacion":"CONFIRMADA","operationId":"9a24c979-fb6a-4110-a08b-1c0fb71f161b","medioPago":null,"version":2,...,"paidAt":null,...}}

Reiniciando reception-service…
reception-service listo otra vez.
[polling pago 202 1] HTTP 200 · estado=PAGADO estadoOperacion=CONFIRMADA paidAt=2026-10-21T01:02:51.237342Z

[pago 202 terminal] HTTP 200
  {"success":true,"message":"OK","data":{"idVenta":28,...,"estado":"PAGADO","estadoOperacion":"CONFIRMADA","operationId":"9a24c979-fb6a-4110-a08b-1c0fb71f161b","medioPago":"TARJETA","version":3,...,"paidAt":"2026-10-21T01:02:51.237342Z",...}}
{"idVenta": 28, "resultado": "PAGADO tras 202"}
```

El script termina con código 0 en el **sondeo 1**: el `202` conserva `Location` y `Retry-After: 2`,
la venta llega a `PAGADO` con `paidAt` y `medioPago=TARJETA`, y la versión pasa de 2 a 3. La
respuesta terminal se consulta en `GET /api/venta/buscar/{id}`.

Estado en `sales_db` (consultas de apoyo del plan, con `{idVenta}` = 28):

```text
$ docker exec hotel-postgres-1 psql -U postgres -d sales_db -c \
    "select v.id, v.estado, v.estado_operacion, v.medio_pago, v.paid_at, v.version from venta v where v.id=28;"

 id | estado | estado_operacion | medio_pago |            paid_at            | version
----+--------+------------------+------------+-------------------------------+---------
 28 | PAGADO | CONFIRMADA       | TARJETA    | 2026-10-21 01:02:51.237342+00 |       3
(1 row)

$ docker exec hotel-postgres-1 psql -U postgres -d sales_db -c \
    "select o.operation_id, o.tipo, o.estado, o.attempts, o.next_attempt_at from operacion_venta o where o.id_venta=28 order by o.tipo;"

             operation_id             | tipo  |  estado  | attempts |        next_attempt_at
--------------------------------------+-------+----------+----------+-------------------------------
 9a24c979-fb6a-4110-a08b-1c0fb71f161b | PAGO  | TERMINAL |        0 | 2026-10-21 01:02:51.237342+00
 5f80a88e-e2c9-4e5e-97d7-41a20de05f97 | VENTA | TERMINAL |        1 | 2026-10-21 01:03:03.549939+00
(2 rows)
```

Una sola operación `PAGO`, en `TERMINAL`, y con `attempts=0`: la recuperador la aplicó en el primer
ciclo, sin reintento ni posposición.

**Idempotencia (paso 7).** Con el recuperador siguiendo su ciclo de 30 s, el estado no cambia:

```text
$ date
mar., 20 de oct. de 2026 08:04:06 p. m.

$ docker exec hotel-postgres-1 psql -U postgres -d sales_db -c \
    "select v.id, v.estado, v.medio_pago, v.paid_at, v.version from venta v where v.id=28;"

 id | estado | medio_pago |            paid_at            | version
----+--------+------------+-------------------------------+---------
 28 | PAGADO | TARJETA    | 2026-10-21 01:02:51.237342+00 |       3
(1 row)

$ docker exec hotel-postgres-1 psql -U postgres -d sales_db -c \
    "select channel, type, count(*) from outbox where aggregate_id='28' group by channel, type order by channel;"

            channel            |            type             | count
-------------------------------+-----------------------------+-------
 recepcion.operacion-finalizar | RecepcionOperacionFinalizar |     2
 sales.venta.v1                | VentaSnapshot               |     2
(2 rows)

$ grep -c "recuperacion_pago_registrado idVenta=28" tests/evidence/qa-runbooks/_runtime/sales-service.log
1
```

Un solo paso del CAS de pago: `version` 3, `paid_at` sin cambios, un `VentaSnapshot` por versión de
venta (2 del alta, 3 del pago) y un `recepcion.operacion-finalizar` por operación:

```text
$ docker exec hotel-postgres-1 psql -U postgres -d sales_db -t -c \
    "select aggregate_version, payload->'payload'->>'tipo', payload->'payload'->>'resultado' from outbox where channel='recepcion.operacion-finalizar' and aggregate_id='28' order by aggregate_version;"

2 | VENTA | CONFIRMADA
3 | PAGO | PAGO_REGISTRADO
```

**Sin movimiento de inventario por el pago (paso 9).** El único débito es el del alta de la venta:

```text
$ docker exec hotel-postgres-1 psql -U postgres -d inventory_db -c \
    "select id_producto, id_hotel, cantidad, version from producto_sede where id_producto=2 and id_hotel=1;"
 id_producto | id_hotel | cantidad | version
-------------+----------+---------+---------
           2 |        1 |       5 |       6
(1 row)

$ docker exec hotel-postgres-1 psql -U postgres -d inventory_db -c \
    "select id_venta, id_hotel, estado, version from movimiento_venta where id_venta=28;"

 id_venta | id_hotel |   estado   | version
----------+----------+------------+---------
       28 |        1 | DESCONTADO |       1
(1 row)

$ docker exec hotel-postgres-1 psql -U postgres -d sales_db -c \
    "select count(*) from outbox where channel='stock.restituir' and aggregate_id='28';"
 restituciones
---------------
             0
(1 row)

$ grep -n "stock_restituido\|restituir\|descontar\|inventario" tests/evidence/qa-runbooks/_runtime/sales-service.log
(sin coincidencias)
```

Antes del pago, `producto_sede` del producto 2 en la sede 1 tenía `cantidad=6` y `version=5`; al
terminar tiene `cantidad=5` y `version=6`: exactamente la unidad del alta de la venta, nada más. El
log de `sales-service` no registra restitución ni llamada a inventory por el pago, y el outbox de
sales no tiene ningún `stock.restituir` para esa venta.

En `reception_db`, las dos operaciones de la venta quedan cerradas con su resultado:

```text
$ docker exec hotel-postgres-1 psql -U postgres -d reception_db -c \
    "select operation_id, id_venta, tipo, estado, resultado from operacion_consumo where id_venta=28;"

             operation_id             | id_venta | tipo  |  estado   | resultado
--------------------------------------+----------+-------+-----------+-----------
 5f80a88e-e2c9-4e5e-97d7-41a20de05f97 |       28 | VENTA | FINALIZADA | CONFIRMADA
 9a24c979-fb6a-4110-a08b-1c0fb71f161b |       28 | PAGO  | FINALIZADA | PAGO_REGISTRADO
(2 rows)
```

### Comandos canónicos (paso 10)

```bash
bash scripts/mvn.sh -f services/pom.xml verify
python scripts/validar-planes.py
python scripts/validar-contratos.py --base origin/main
npx openspec validate back-arreglo-sales-pago-202 --strict
```

```text
[INFO] Hotel services (agregador) ......................... SUCCESS [  0.001 s]
[INFO] identity-service ................................... SUCCESS [ 44.948 s]
[INFO] hotel-service ...................................... SUCCESS [ 21.761 s]
[INFO] reception-service .................................. SUCCESS [01:13 min]
[INFO] inventory-service .................................. SUCCESS [ 56.933 s]
[INFO] sales-service ...................................... SUCCESS [ 18.276 s]
[INFO] reporting-service .................................. SUCCESS [ 17.727 s]
[INFO] notification-service ............................... SUCCESS [ 11.819 s]
[INFO] api-gateway ........................................ SUCCESS [ 41.346 s]
[INFO] discovery-server ................................... SUCCESS [ 15.068 s]
[INFO] BUILD SUCCESS

$ python scripts/validar-planes.py
32 plan(es) revisado(s); 32 previsto(s) en el catálogo; 0 error(es), 0 aviso(s).

$ python scripts/validar-contratos.py --base origin/main
Contratos: 8 OpenAPI, 20 schemas de evento. Regla: aditivos frente a origin/main.
Los contratos solo se han ampliado. Correcto.

$ npx openspec validate back-arreglo-sales-pago-202 --strict
Change 'back-arreglo-sales-pago-202' is valid
```

El change se archiva al cerrar el plan (norma §8), como en `back-sales-ventas` y
`back-reception-cierre`. Tras el archivo, el validador de las specs ya sincronizadas sigue en verde:

```text
$ npx openspec validate --all --strict
✓ spec/sales-ventas
Totals: 10 passed, 0 failed (10 items)
```

El change queda archivado como `openspec/changes/archive/2026-10-20-back-arreglo-sales-pago-202/` y
el requisito «Operaciones observables con recuperación cada 30 s» de
`openspec/specs/sales-ventas/spec.md` queda actualizado con el delta de este plan.


## 7. Preguntas abiertas

Ninguna (resuelto por IA: la separación de la selección de operaciones `PREPARANDO` se hace con
tres consultas con su propio criterio dentro de un único punto de entrada, para no romper la API
pública del repositorio ni la evidencia del paso 2; registrado en
[`IA-back-arreglo-sales-pago-202-01`](../implementacion/decisiones/IA-back-arreglo-sales-pago-202-01.md)).
La recuperación de `PAGO` y `ANULACION` en `PREPARANDO` está fijada por
[`04-flujos.md` §2](../especificacion/04-flujos.md#2-venta-y-stock), el `202` con `Location`/`Retry-After` por
[`sales/api.md` §REST: ventas](../especificacion/microservicios/sales/api.md#rest-ventas), y `next_attempt_at` ya existe en `operacion_venta`
([`sales/base-de-datos.md` §Tablas](../especificacion/microservicios/sales/base-de-datos.md#tablas)). No se decide ningún estado, código ni valor nuevo.

## 8. Criterio de terminado

- [x] Todos los pasos de §4 marcados con su verificación.
- [x] Un `PUT /api/venta/{id}` con reception caído deja la operación `PAGO` en `PREPARANDO` y responde `202`; al volver reception, la venta llega a `PAGADO` dentro de los 30 sondeos de 3 s, con `paid_at` y `medio_pago`.
- [x] La operación `PAGO` termina `TERMINAL` una sola vez y no se emite un segundo `recepcion.operacion-finalizar` ni un `stock.restituir` por el pago.
- [x] La venta alta `PENDIENTE` con operación `VENTA` en `PREPARANDO` de más de 120 s sigue rechazándose sin stock; DEBITO_SOLICITADO y CANCELACION_PENDIENTE conservan su recuperación.
- [x] `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS`; `python scripts/validar-planes.py` con 0 errores; `python scripts/validar-contratos.py --base origin/main` sin cambios de contrato; `npx openspec validate back-arreglo-sales-pago-202 --strict` sin errores.
- [x] La reproducción original de §3 está en verde con evidencia adjunta al PR y sin preguntas abiertas.
