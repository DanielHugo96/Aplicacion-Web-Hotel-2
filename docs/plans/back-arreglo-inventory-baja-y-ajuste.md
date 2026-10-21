# back-arreglo-inventory-baja-y-ajuste — el filtro de baja devuelve vacío y el ajuste exige una query que el contrato no publica (F3)

| Campo | Valor |
|---|---|
| Plan | `back-arreglo-inventory-baja-y-ajuste` |
| Ámbito | `back` |
| Componente | `inventory-service` |
| Fase | `F3` |
| Modo | `directo` |
| Estado | `en curso` |
| Depende de | `back-inventory-stock` |
| Repara | `back-inventory-stock` |
| Spec | [`inventory/api.md` §REST](../especificacion/microservicios/inventory/api.md#rest), [`inventory/base-de-datos.md` §Tablas](../especificacion/microservicios/inventory/base-de-datos.md#tablas) y [§Funciones/procedimientos](../especificacion/microservicios/inventory/base-de-datos.md#funcionesprocedimientos), [`03-contrato-comun.md` §Identidad y sedes](../especificacion/03-contrato-comun.md#identidad-y-sedes) y [§Respuestas](../especificacion/03-contrato-comun.md#respuestas), [`inventory.yaml`](../contracts/openapi/inventory.yaml), [`admin-web.yaml`](../contracts/openapi/admin-web.yaml) y [`errors.md` §Inventory y Sales](../contracts/errors.md#inventory-y-sales) |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

Al cerrar el plan, `GET /api/producto/listar?idHotel=1&estado=false` devuelve los productos dados de baja en esa sede, `estado=true` devuelve solo los activos y la ausencia de `estado` sigue devolviendo ambos con su estado efectivo; y `POST /api/producto/{id}/ajustes` funciona con el cuerpo cerrado `{idHotel,delta,motivo}` del contrato, sin exigir `idHotel` como query. Se comprueba repitiendo las reproducciones de §3 y dejando sus comandos en verde.

## 2. Alcance

**Entra:** la zona del plan reparado `back-inventory-stock`, componente `inventory-service`:

- `services/inventory-service/src/main/java/pe/hotel/inventory/domain/service/ProductoService.java`: en `listar`, filtrar por la baja de la sede (`producto_sede.estado`) y no por la conjunción `producto.estado = ? AND producto_sede.estado = ?`; conservar el `estado` devuelto como el efectivo (`producto.estado AND producto_sede.estado`) y el orden por id ascendente. En `ajustar`, tomar la sede del cuerpo `AjusteRequest.idHotel` en vez de un parámetro de query.
- `services/inventory-service/src/main/java/pe/hotel/inventory/web/ProductoController.java`: en `ajustar`, retirar `@RequestParam Integer idHotel`, validar la sede con `verificarSede(request.idHotel())` y pasar ese valor al servicio; conservar `If-Match`, `Idempotency-Key` y las respuestas `201`/`400`/`403`/`404`/`412`/`428`.
- `services/inventory-service/src/test/java/pe/hotel/inventory/**`: pruebas de regresión del filtro `estado=false`/`true`/omitido y del ajuste solo con el cuerpo del contrato.
- `docs/plans/back-arreglo-inventory-baja-y-ajuste.md` y su fila en `docs/plans/catalogo.md`.

**Fuera de alcance:**

- `docs/contracts/**` y `docs/especificacion/**`: no se modifican. `inventory.yaml` ya declara `AjusteRequest` con `idHotel` obligatorio en el cuerpo y sin query; el `estado` devuelto como efectivo ya está publicado.
- `apps/admin-web/**` y el plan `front-admin-web-ventas`: la pantalla que envía `idHotel` en el cuerpo y en la query se queda como está; el arreglo es compatible con las dos formas. La decisión `IA-front-admin-web-ventas-01.md` vive en la rama del front y se cita, no se modifica.
- Migraciones Flyway, tablas, columnas, índices, seeds y la función `fn_stock_ajustar`: no cambian. El ajuste sigue con lock y saldo no negativo.
- La paginación de `GET /api/producto/listar` y el `default: true` del parámetro `estado`: no forman parte de este fallo y no se cambian. La ausencia de `estado` sigue significando «sin filtro», que es lo que consume «Todos» en la pantalla de ventas.
- Las demás rutas de productos, las rutas internas, la mensajería (`inventory.stock.v1`, `inventory.movimiento.v1`, `stock.restituir`), el débito y la restitución no cambian.
- `services/pom.xml`, `apps/*/package.json` y el `README.md` raíz: las versiones fijadas no se tocan.

## 3. Contexto

Los defectos los introdujo `back-inventory-stock`, plan ya `cerrado`, en el PR #81 (commit `f74dccb`, «feat: implementa el plan back-inventory-stock (#81)»). Su zona es `services/inventory-service/**`, exactamente la zona de este arreglo. Los detectó la ejecución de `front-admin-web-ventas` en su integración real por el Gateway; su paso 19 quedó sin marcar y la transcripción vive en `origin/front-admin-web-ventas`: `apps/admin-web/tests/evidence/front-admin-web-ventas-integracion.txt`. La decisión `docs/implementacion/decisiones/IA-front-admin-web-ventas-01.md` de esa rama registró el `500` del ajuste.

Las reproducciones se ejecutaron desde `main` (`803a9f1`) el 2026-10-20, con los siete servicios arriba, `inventory-service` en `8084`, el Gateway en `8080` y el JWT de `admin@hotel.test` (ADMIN).

**Reproducción 1 — el filtro de dados de baja devuelve vacío.**

```text
[registrar] HTTP 201
{"success":true,"message":"Producto registrado","data":{"idProducto":9,"sku":"REP-5801DDE5",...,"idHotel":1,"precio":4.50,"cantidad":3,"umbralBajo":1,"estado":true,...,"version":1,"productoVersion":1},...}
[eliminar] HTTP 200 -> estado False version 2
[listar] idHotel=1&estado=false&page=0&size=20 HTTP 200 filas 0 ids []
    {"success":true,"message":"OK","data":[],"meta":{"correlationId":"6166ff3d-3c5a-4fbe-ba77-d7732736780e"}}
[listar] idHotel=1&estado=true&page=0&size=20 HTTP 200 filas 8 ids [1, 2, 3, 4, 5, 6, 7, 8]
[listar] idHotel=1&page=0&size=20 HTTP 200 filas 9 ids [1, 2, 3, 4, 5, 6, 7, 8, 9]
```

El producto 9 está dado de baja en la sede 1 (`estado=false`, `version=2`) pero no aparece con `estado=false`; solo aparece al omitir el filtro. La fila de la transcripción del front es la misma: `producto/listar todos tras la baja` muestra el producto 8 con `"estado": false`, y el filtro de inactivos no lo devuelve.

`ProductoService.listar` (líneas 48-67) aplica `estado` a las dos columnas:

```sql
if (estado != null) {
  sql += " AND p.estado = ? AND ps.estado = ?";
  params.add(estado);
  params.add(estado);
}
```

En el MVP `producto.estado` global es siempre `true` (`inventory/api.md` §REST: «Producto.estado global queda activo en MVP»), así que `p.estado = false AND ps.estado = false` nunca se cumple. El contrato de [`inventory.yaml`](../contracts/openapi/inventory.yaml) describe `estado` como «Filtro de baja lógica por sede» y aclara que «el estado devuelto es el efectivo (producto AND producto_sede)». El filtro debe mirar la baja de la sede; el `estado` devuelto sigue siendo el efectivo que ya calcula `mapProductoView` (`ps_estado && estado`).

**Reproducción 2 — el ajuste exige `idHotel` como query y sin él responde 500.**

```text
[ajuste sin query] HTTP 500
{"success":false,"message":"Ocurrió un error inesperado.","error":{"code":"INTERNAL_ERROR","fields":{}},"meta":{"correlationId":"70ae4a8d-4357-481e-be17-32a4fe1abbc9"}}
[ajuste con query] HTTP 201
{"success":true,"message":"Ajuste registrado","data":{"ajusteId":"3a36e75d-5ee4-4127-ae95-d9ef8dbacbb6","cantidad":5,"version":3},"meta":{"correlationId":"f12f4b47-67ff-4d03-a770-99885564813b"}}
```

Se envió el cuerpo cerrado del contrato `{"idHotel":1,"delta":2,"motivo":"Reproduccion defecto ajuste"}` con `If-Match: "2"` e `Idempotency-Key`. El log de `inventory-service` registra `unhandled error: Required request parameter 'idHotel' for method parameter type Integer is not present`.

`ProductoController.ajustar` declara `@RequestParam Integer idHotel` además de `@Valid @RequestBody AjusteRequest request`. El contrato [`inventory.yaml`](../contracts/openapi/inventory.yaml), ruta `POST /api/producto/{id}/ajustes`, declara solo `IdPath`, `IdempotencyKey`, `IfMatch` y `CorrelationId`; no declara `idHotel` en la query. Su schema `AjusteRequest` lo exige en el cuerpo (`required: [idHotel, delta, motivo]`), y [`inventory/api.md` §REST](../especificacion/microservicios/inventory/api.md#rest) fija la entrada como `{idHotel,delta,motivo}`. El contrato es el que manda: no hay defecto de contrato, sino del controlador, que lee la sede de un sitio que el contrato no publica. La decisión del front (`IA-front-admin-web-ventas-01.md`) ya había enviado `idHotel` en ambos sitios como solución temporal; el arreglo deja el contrato como la única fuente y no rompe esa llamada.

**Delimitación.** Los dos fallos son de `inventory-service`, del mismo plan `cerrado` y de la misma zona (`services/inventory-service/**`), así que van en un solo plan de arreglo. No cruzan a `sales-service` ni a `admin-web`; el front solo consume las respuestas. El modo es `directo` porque no se decide ninguna regla de negocio ni de stock: se corrige un predicado y un binding contra un contrato ya publicado, sin tocar la función de ajuste, el esquema ni los eventos.

## 4. Pasos

- [ ] 1. Reproducir los dos fallos desde `main` con el JWT de ADMIN y el Gateway: crear un producto, darlo de baja con `DELETE /api/producto/eliminar/{id}?idHotel=1`, listar con `estado=false`, `estado=true` y sin `estado`; y enviar `POST /api/producto/{id}/ajustes` solo con el cuerpo del contrato. Verificación: `estado=false` devuelve 0 filas, el producto solo aparece sin filtro, y el ajuste sin query responde `500 INTERNAL_ERROR` mientras con `?idHotel=1` responde `201`.
- [ ] 2. Añadir pruebas de regresión que fallan en `main`: `ProductoService.listar` con `estado=false` devuelve la fila de `producto_sede.estado=false`; con `estado=true` no la devuelve; sin `estado` devuelve las dos con su estado efectivo; y `ProductoController.ajustar` responde `201` con el cuerpo del contrato y sin query. Verificación: `bash scripts/mvn.sh -f services/pom.xml -pl inventory-service test` falla en esas pruebas antes del cambio.
- [ ] 3. Corregir `ProductoService.listar`: cuando `estado` no es nulo, filtrar solo por `producto_sede.estado` (`AND ps.estado = ?`), manteniendo el `JOIN`, el orden por id ascendente y el `estado` devuelto como `ps_estado && estado`. No cambiar el comportamiento sin `estado` (sin filtro) ni añadir paginación. Verificación: la prueba de `estado=false` devuelve el producto 9 con `estado=false`; `estado=true` devuelve solo los activos; sin `estado` devuelve ambos.
- [ ] 4. Corregir `ProductoController.ajustar`: retirar `@RequestParam Integer idHotel`; usar `request.idHotel()` para `verificarSede(...)` y para el servicio. Mantener `@Valid`, `If-Match` obligatorio, `Idempotency-Key`, y las respuestas `201`/`400`/`403`/`404`/`412`/`428`. Verificación: una petición solo con `{"idHotel":1,"delta":2,"motivo":"..."}` y `If-Match` responde `201`; sin `If-Match` sigue en `428`; con versión vieja, `412`; con `idHotel` ausente del cuerpo, `400 VALIDATION_ERROR` y no `500`.
- [ ] 5. Ajustar la firma de `ProductoService.ajustar` para recibir la sede del cuerpo (o conservar el parámetro y pasarle `request.idHotel()` desde el controlador), sin cambiar la llamada a `fn_stock_ajustar`, el lock, el cálculo del saldo ni el evento `inventory.stock.v1`. Verificación: el ajuste sigue incrementando `producto_sede.version`, devuelve `{ajusteId,cantidad,version}` y deja una fila en `ajuste_stock` con el actor auditado.
- [ ] 6. Verificar la compatibilidad y los límites: `POST /api/producto/{id}/ajustes?idHotel=1` con el mismo cuerpo sigue respondiendo `201` (el query extra se ignora); un EMPLEADO fuera de su sede sigue en `403`; un producto de otra sede sigue en `404`; un `delta=0` o un saldo negativo siguen en `400`. Verificación: las cuatro respuestas se observan en la corrida real.
- [ ] 7. Reiniciar `inventory-service` desde la rama corregida y repetir la reproducción de §3: el producto dado de baja aparece con `estado=false`, no con `estado=true`, y sigue apareciendo sin filtro; el ajuste solo con el cuerpo responde `201` y el stock sube por el `delta`. Verificación: las salidas literales de §6 quedan adjuntas al PR.
- [ ] 8. Comprobar que el contrato no cambió: `git diff --exit-code origin/main -- docs/contracts` no produce salida, y `python scripts/validar-contratos.py --base origin/main` termina en «Los contratos solo se han ampliado. Correcto.» Verificación: 0 cambios en contratos y validador en verde.
- [ ] 9. Correr los comandos canónicos: `bash scripts/mvn.sh -f services/pom.xml -pl inventory-service test`, `bash scripts/mvn.sh -f services/pom.xml verify` y `python scripts/validar-planes.py`. Verificación: pruebas y build en `BUILD SUCCESS` y 0 errores del validador.
- [ ] 10. Guardar en §6 las salidas de los pasos 1, 2, 6, 7 y 9, marcar todos los pasos y cambiar `Estado` a `cerrado` en el último commit. Verificación: no quedan pasos sin marcar y las dos reproducciones originales están en verde.

## 5. Contratos afectados

- **OpenAPI:** no se modifica `docs/contracts/openapi/inventory.yaml` ni `admin-web.yaml`. Se implementan `GET /api/producto/listar` con el filtro `estado` sobre la baja de la sede y el `estado` devuelto efectivo, y `POST /api/producto/{id}/ajustes` con `idHotel` leído de `AjusteRequest` y sin query. No se añade, cambia ni elimina ninguna ruta, parámetro, schema, `operationId` ni código.
- **Eventos y comandos:** ninguno. El ajuste sigue emitiendo el snapshot de `inventory.stock.v1` con la versión de `producto_sede`; no se tocan `inventory.movimiento.v1` ni `stock.restituir`, ni `docs/contracts/events/`.
- **Persistencia:** ninguna migración ni cambio de tabla. `producto`, `producto_sede` y `ajuste_stock` ya existen y `fn_stock_ajustar` no cambia. El filtro usa `producto_sede.estado`; el `estado` devuelto sigue siendo `producto.estado AND producto_sede.estado`.
- **Errores:** se usan los códigos ya publicados en [`errors.md`](../contracts/errors.md): `VALIDATION_ERROR` (400), `FORBIDDEN` (403), `RESOURCE_NOT_FOUND` (404), `PRECONDITION_REQUIRED` (428) y `PRECONDITION_FAILED` (412). El `500 INTERNAL_ERROR` del ajuste sin query desaparece. No se añade ningún código.
- **Semántica que no se cambia:** la ausencia de `estado` sigue devolviendo todas las filas de la sede (lo que la pantalla usa para «Todos»), y el `default: true` del OpenAPI no se implementa en este arreglo porque cambiaría esa pantalla. El `estado` devuelto sigue siendo el efectivo, no el de la sede sola.
- **Seguridad:** no se cambia el validador JWT, la audiencia, el issuer ni los scopes. La sede del ajuste se sigue validando contra el JWT (`verificarSede`) y no se aceptan cabeceras de identidad.

## 6. Pruebas

### Reproducción antes del arreglo

Ejecutada el 2026-10-20 sobre `main` (`803a9f1`), con `inventory-service` en `8084` y el JWT de `admin@hotel.test`. Salida literal de §3: el producto 9 se crea (`201`), se da de baja (`200`, `estado=false`, `version=2`), `GET /api/producto/listar?idHotel=1&estado=false&page=0&size=20` devuelve `{"data":[],...}` con 0 filas, `estado=true` devuelve 8 filas sin el producto 9, y sin `estado` devuelve 9 filas con el producto 9. `POST /api/producto/{id}/ajustes` solo con el cuerpo responde `500 INTERNAL_ERROR`; con `?idHotel=1` responde `201` con `{ajusteId,cantidad:5,version:3}`. Evidencia: esta salida y la transcripción del front; sin tokens.

### Verificación después del arreglo

```bash
# Crear un producto, darlo de baja y listar por estado
# (idProducto y version se toman de las respuestas)
curl -s -X POST http://localhost:8080/api/producto/registrar \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(python -c 'import uuid;print(uuid.uuid4())')" \
  -d '{"sku":"REP-<sufijo>","nombre":"Producto reproduce","detalle":"Reproduccion","imagenUrl":"/assets/demo/producto-agua.png","idHotel":1,"precio":4.50,"cantidadInicial":3,"umbralBajo":1}'

curl -s -X DELETE "http://localhost:8080/api/producto/<id>?idHotel=1" \
  -H "Authorization: Bearer $ADMIN" \
  -H "Idempotency-Key: $(python -c 'import uuid;print(uuid.uuid4())')" -H 'If-Match: "1"'

curl -s "http://localhost:8080/api/producto/listar?idHotel=1&estado=false&page=0&size=20" \
  -H "Authorization: Bearer $ADMIN"
curl -s "http://localhost:8080/api/producto/listar?idHotel=1&estado=true&page=0&size=20" \
  -H "Authorization: Bearer $ADMIN"
curl -s "http://localhost:8080/api/producto/listar?idHotel=1&page=0&size=20" \
  -H "Authorization: Bearer $ADMIN"

# Ajuste solo con el cuerpo del contrato, sin query idHotel
curl -s -i -X POST "http://localhost:8080/api/producto/<id>/ajustes" \
  -H "Authorization: Bearer $ADMIN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(python -c 'import uuid;print(uuid.uuid4())')" -H 'If-Match: "2"' \
  -d '{"idHotel":1,"delta":2,"motivo":"Verificacion del arreglo"}'
```

Resultado esperado: `estado=false` devuelve el producto dado de baja con `"estado":false` y `"version":2`; `estado=true` no lo devuelve; sin `estado` aparece con su estado efectivo; el ajuste sin query responde `201` con `{ajusteId,cantidad:5,version:3}` y el stock de la sede sube de 3 a 5. El ajuste con `?idHotel=1` sigue respondiendo `201`. Evidencia: las respuestas completas en el PR de ejecución y en §6 de este plan.

### Pruebas automatizadas

```bash
bash scripts/mvn.sh -f services/pom.xml -pl inventory-service test
```

Resultado esperado: las pruebas nuevas de `ProductoService.listar`, `ProductoController.ajustar` y `ProductoService.ajustar` en verde, además de las existentes. Evidencia: `services/inventory-service/target/surefire-reports/` y la salida del comando adjunta al PR.

### Comandos canónicos

```bash
bash scripts/mvn.sh -f services/pom.xml verify
python scripts/validar-planes.py
python scripts/validar-contratos.py --base origin/main
```

Resultado esperado: `BUILD SUCCESS` de los nueve módulos; 0 errores del validador de planes; «Los contratos solo se han ampliado. Correcto.» (sin cambios de contrato).

## 7. Preguntas abiertas

Ninguna. El contrato fija `AjusteRequest` con `idHotel` en el cuerpo y sin query, y el filtro `estado` como baja lógica por sede con `estado` devuelto efectivo. No hay una regla de negocio, un estado, un código ni un valor nuevo que decidir; el arreglo solo alinea el código con lo publicado.

## 8. Criterio de terminado

- [ ] Todos los pasos de §4 marcados con su verificación.
- [ ] `GET /api/producto/listar?idHotel=1&estado=false` devuelve los productos dados de baja en la sede; `estado=true` devuelve solo los activos; sin `estado` devuelve ambos con su estado efectivo.
- [ ] `POST /api/producto/{id}/ajustes` funciona solo con el cuerpo `{idHotel,delta,motivo}` y sin query, y conserva `201`, `400`, `403`, `404`, `412` y `428`; el `500 INTERNAL_ERROR` desaparece.
- [ ] `POST /api/producto/{id}/ajustes?idHotel=1` con el mismo cuerpo sigue respondiendo `201`; el stock y la versión de la sede se actualizan igual.
- [ ] `docs/contracts/**` no cambia y `python scripts/validar-contratos.py --base origin/main` termina sin cambios de contrato.
- [ ] `bash scripts/mvn.sh -f services/pom.xml verify` termina en `BUILD SUCCESS` y `python scripts/validar-planes.py` con 0 errores.
- [ ] Las dos reproducciones originales de §3 están en verde con evidencia adjunta al PR y sin preguntas abiertas.
