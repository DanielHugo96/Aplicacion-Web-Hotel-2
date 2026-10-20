# back-arreglo-hotel-arranque — arranque de hotel-service y evidencia real del catálogo (F2)

| Campo | Valor |
|---|---|
| Plan | `back-arreglo-hotel-arranque` |
| Ámbito | `back` |
| Componente | `hotel-service` |
| Fase | `F2` |
| Modo | `directo` |
| Estado | `cerrado` |
| Depende de | `back-hotel-catalogo` |
| Repara | `back-hotel-catalogo` |
| Spec | [`hotel/api.md` §Públicas nuevas: solo lectura](../especificacion/microservicios/hotel/api.md#públicas-nuevas-solo-lectura), [§Sedes, pisos y categorías](../especificacion/microservicios/hotel/api.md#sedes-pisos-y-categorías), [§Habitaciones y físico](../especificacion/microservicios/hotel/api.md#habitaciones-y-físico) y [§DTO](../especificacion/microservicios/hotel/api.md#dto); [`hotel/base-de-datos.md` §Tablas](../especificacion/microservicios/hotel/base-de-datos.md#tablas), [§DDL de referencia](../especificacion/microservicios/hotel/base-de-datos.md#ddl-de-referencia), [§Procedimientos](../especificacion/microservicios/hotel/base-de-datos.md#procedimientos) y [§Seeds deterministas](../especificacion/microservicios/hotel/base-de-datos.md#seeds-deterministas); [`03-contrato-comun.md` §Respuestas](../especificacion/03-contrato-comun.md#respuestas) y [§Headers](../especificacion/03-contrato-comun.md#headers); [`06-migracion-y-pruebas.md` §5](../especificacion/06-migracion-y-pruebas.md#5-matriz-de-aceptación) |
| Casos | `LP01`, `SEC03`, `CRUD01`, `OCC04` |
| Change | `—` |

## 1. Objetivo

`hotel-service` arranca desde `main` sobre una `hotel_db` vacía: Flyway aplica V1–V4, Hibernate construye el metamodelo con `ddl-auto=validate` y el catálogo público responde conforme a `HotelPublico` y `CategoriaPublica`. Se comprueba arrancando el servicio y volviendo a ejecutar `LP01`, `SEC03`, `CRUD01` y `OCC04` con respuestas HTTP y consultas SQL reales.

## 2. Alcance

**Entra:**

- `services/hotel-service/src/main/resources/db/migration/V3__hotel_tablas.sql`: corregir la función `fn_catalogo_publico` para que use la columna `publicada` que declara la tabla.
- `services/hotel-service/src/main/resources/db/migration/V4__seeds_hotel.sql`: fijar los ids deterministas de [§Seeds deterministas](../especificacion/microservicios/hotel/base-de-datos.md#seeds-deterministas), corregir la FK de `imagen_habitacion` y dejar las secuencias por encima del máximo.
- `services/hotel-service/src/main/java/pe/hotel/hotel/domain/model/Habitacion.java`: corregir el mapeo de la asociación `piso` contra `piso(id)`.
- `services/hotel-service/src/main/java/pe/hotel/hotel/domain/repository/HabitacionRepository.java`: corregir las tres consultas que navegan por `idCategoria`.
- `services/hotel-service/src/main/java/pe/hotel/hotel/domain/service/CatalogoPublicoService.java`: completar la proyección pública con los campos de sede y el detalle de categoría que la función no devuelve, y leer `imagenes` como texto JSON.
- `services/hotel-service/src/main/java/pe/hotel/hotel/web/HotelPublicoController.java`: devolver `correlationId` y la paginación `page`/`size`/`totalElements` que exige el contrato de los listados públicos.
- `docs/plans/back-arreglo-hotel-arranque.md`, la fila de este plan en `docs/plans/catalogo.md` y las bitácoras de `docs/implementacion/decisiones/` que documentan el hallazgo y las decisiones de nivel 2.

**Fuera de alcance:**

- `docs/contracts/**`, `docs/especificacion/**`, `docs/plans/README.md`, `docs/plans/_plantilla.md` y `docs/guia-git.md`: no se modifican. El arreglo se limita a hacer que el código cumpla los contratos ya publicados.
- Las versiones fijadas (`services/pom.xml`, `apps/*/package.json`, `README.md` raíz) y cualquier otro servicio o app.
- `infra/**`: no se cambian Compose, roles ni volúmenes. La verificación puede recrear `hotel_db`, que es una base de demo.
- Los flujos de mantenimiento, baja con `blockId`, limpieza, reactivación y el consumidor de `limpieza.solicitada` (`back-hotel-operaciones`), así como la lectura de la proyección de ocupación y los demás planes dependientes.
- Cualquier corrección de `back-inventory-stock` o de otros servicios: su desbloqueo se hace después de cerrar este plan, en su propia rama.

## 3. Contexto

El defecto lo introdujo `back-hotel-catalogo`, un plan ya `cerrado` y mergeado en `main` con el PR #66 (merge `a2a1f3c`). Su zona es `services/hotel-service/**`, que es exactamente la zona de este arreglo. La ejecutora de `back-inventory-stock` lo detectó al no poder validar una sede por Feign y lo registró en [`IA-back-inventory-stock-02.md`](../implementacion/decisiones/IA-back-inventory-stock-02.md); su PR #72 quedó cerrado sin mergear.

**Reproducción 1 — Flyway no aplica V3 sobre una base vacía.**

```text
$ SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/hotel_db_arreglo \
  SPRING_DATASOURCE_USERNAME=hotel_migrator SPRING_DATASOURCE_PASSWORD=... \
  SPRING_FLYWAY_USER=hotel_migrator SPRING_FLYWAY_PASSWORD=... \
  bash scripts/mvn.sh -f services/pom.xml -pl hotel-service spring-boot:run

Migration of schema "public" to version "3 - hotel tablas" failed! Changes successfully rolled back.
SQL State  : 42703
Error Code : 0
Message    : ERROR: column hab.publica does not exist
  Hint: Perhaps you meant to reference the column "hab.publicada".
Location   : db/migration/V3__hotel_tablas.sql (.../target/classes/db/migration/V3__hotel_tablas.sql)
Line       : 86
```

La columna `habitacion.publicada` está declarada en la línea 58; la función la referencia como `hab.publica` y `h2.publica` en las líneas 110 y 116. PostgreSQL valida el cuerpo de la función al crearla (`check_function_bodies` está en `on`), así que la migración falla y el servicio no llega a arrancar.

**Reproducción 2 — Hibernate falla aunque se corrija la función.**

Con la función corregida en una base de diagnóstico y `spring.jpa.hibernate.ddl-auto=none`, el arranque sigue fallando:

```text
Failed to initialize JPA EntityManagerFactory: No column with logical name 'id_hotel' in table 'piso'
Caused by: org.hibernate.MappingException: No column with logical name 'id_hotel' in table 'piso'
    at org.hibernate.boot.model.internal.AnnotatedJoinColumns.column(AnnotatedJoinColumns.java:402)
    at org.hibernate.boot.model.internal.AnnotatedJoinColumns.getReferencedColumnsType(AnnotatedJoinColumns.java:363)
```

No es un síntoma del primer defecto: el DDL sí declara `UNIQUE (id, id_hotel)` en `piso`, pero `Piso` expone `id_hotel` solo como `@JoinColumn` de su asociación `hotel`, no como columna básica. `Habitacion.piso` referencia `piso.id_hotel` como columna lógica y Hibernate no la encuentra en el metamodelo.

**Reproducción 3 — tercer bloqueo de arranque.**

Al corregir el mapeo anterior, el contexto sigue sin arrancar por una consulta derivada:

```text
QueryCreationException: Cannot create query for method [HabitacionRepository.countByIdCategoriaEstadoTrue(java.lang.Integer)]; Query validation failed for 'SELECT COUNT(h) > 0 FROM Habitacion h WHERE h.idCategoria = :idCategoria AND h.estado = true'
Caused by: org.hibernate.query.sqm.UnknownPathException: Could not resolve attribute 'idCategoria' of 'pe.hotel.hotel.domain.model.Habitacion'
```

`Habitacion` tiene la asociación `categoria`, no un atributo persistente `idCategoria`; las tres consultas que usan `h.idCategoria` son inválidas. Sin corregirlas el servicio tampoco arranca.

**Reproducción 4 — el catálogo público no cumple el contrato aunque arranque.**

Con los bloqueos de arranque corregidos solo en el entorno de diagnóstico, los cuatro GET públicos devuelven 200, pero el cuerpo no es conforme:

```text
GET /api/public/hoteles
{"success":true,"message":"OK","data":[{"idHotel":1,"slug":"lima-centro","nombre":"Hotel Demo Lima Centro","descripcion":"Individual","direccion":null,"ciudad":null,"telefono":null,"correoContacto":null}],"meta":{}}

GET /api/public/hoteles/lima-centro/categorias/1
{"success":true,"message":"OK","data":{"idCategoria":1,"descripcion":"Individual","detalle":"","capacidad":1,"precioDesde":70.0,"moneda":"PEN","imagenes":[]},"meta":{}}
```

`HotelPublico` exige `descripcion`, `direccion`, `ciudad`, `telefono` y `correoContacto`; el servicio los lee de una función que no los devuelve (y usa la descripción de la categoría como descripción de la sede). `CategoriaPublica` exige `detalle`, que queda vacío porque la función tampoco lo devuelve. La imagen sembrada se pierde porque `leerImagenes` recibe un `PGobject` de `jsonb`, no un `String`. `meta` queda `{}` porque el controlador usa `Meta.of(null)` en vez del `correlationId` de la petición. Los listados tampoco devuelven `page`, `size` ni `totalElements`, que el contrato exige.

**Reproducción 5 — los seeds no son los deterministas de la spec.**

```text
SELECT id, id_hotel, numero FROM habitacion ORDER BY id;
1 | 1 | 101
2 | 1 | 102
3 | 2 | 101
4 | 2 | 102
5 | 3 | 101
6 | 3 | 102

SELECT id, id_hotel FROM piso ORDER BY id;
1 | 1
2 | 2
3 | 3
```

La spec fija `piso` 11, 21 y 31 y `habitacion` 101, 102, 201, 202, 301 y 302. Con los ids automáticos actuales, `SEC03` no puede pedir la habitación 201 y `OCC04` no puede usar el piso 11. Además, `imagen_habitacion` selecciona `h.id` (la sede) en vez de `hab.id` (la habitación): la primera imagen se guarda por coincidencia y la segunda choca con `(id_habitacion, orden)` o queda asociada a la habitación equivocada.

**Evidencia declarada por el plan reparado.** El §6 de `back-hotel-catalogo` declara `LP01`, `SEC03`, `CRUD01` y `OCC04` con «evidencia: respuestas», pero su sección «Evidencia obtenida» solo muestra el build, los validadores y tres clases de pruebas unitarias. Como el servicio no arranca desde `main`, esas respuestas no pudieron obtenerse. El hallazgo queda registrado en [`IA-back-hotel-catalogo-01.md`](../implementacion/decisiones/IA-back-hotel-catalogo-01.md); este arreglo vuelve a ejecutar los casos y adjunta las respuestas reales.

## 4. Pasos

- [x] 1. Reproducir el fallo sobre una base desechable (`hotel_db_arreglo`) con el comando de la Reproducción 1. Verificación: aparece literalmente `ERROR: column hab.publica does not exist` y `Migration of schema "public" to version "3 - hotel tablas" failed`.
- [x] 2. Corregir `V3__hotel_tablas.sql`: cambiar `hab.publica` por `hab.publicada` y `h2.publica` por `h2.publicada` dentro de `fn_catalogo_publico`; no cambiar la firma de la función ni el resto del DDL. Verificación: `grep -n "publica" V3__hotel_tablas.sql` no encuentra referencias a la columna inexistente y la migración aplica sobre una base vacía.
- [x] 3. Verificar la migración completa V1–V4 sobre una base vacía. Verificación: `flyway_schema_history` tiene las versiones 1–4 con `success = true` y `SELECT count(*) FROM fn_catalogo_publico(NULL, NULL)` devuelve las seis filas de sede/categoría publicadas.
- [x] 4. Corregir el mapeo de `Habitacion.piso`: sustituir el `@JoinColumns` de dos columnas por un único `@JoinColumn(name = "id_piso", referencedColumnName = "id", insertable = false, updatable = false)`. Mantener `idHotel` e `idPiso` como columnas básicas y la FK compuesta `(id_piso, id_hotel)` en la migración. Verificación: con `ddl-auto=validate` el `EntityManagerFactory` se inicializa y desaparece `No column with logical name 'id_hotel' in table 'piso'`.
- [x] 5. Corregir `HabitacionRepository`: reemplazar `h.idCategoria` por `h.categoria.id` en `findByIdHotelAndIdCategoriaAndEstadoTrueOrderByIdAsc`, `countByIdCategoriaEstadoTrue` y `findByIdCategoriaAndEstadoTrueOrderByIdAsc`. Verificación: el repositorio se crea sin `UnknownPathException` y las tres consultas devuelven las habitaciones sembradas.
- [x] 6. Corregir `V4__seeds_hotel.sql`: asignar ids fijos 11, 21 y 31 a los pisos y 101, 102, 201, 202, 301 y 302 a las habitaciones; en `imagen_habitacion` seleccionar `hab.id`, no `h.id`; cerrar con `setval` sobre las secuencias de `piso` y `habitacion` al máximo insertado. Verificación: las tablas coinciden con [§Seeds deterministas](../especificacion/microservicios/hotel/base-de-datos.md#seeds-deterministas), las dos imágenes quedan en 101 y 102, y `nextval` devuelve 32 y 303.
- [x] 7. Verificar la idempotencia de los seeds: ejecutar V4 dos veces sobre la misma base. Verificación: no se duplican filas, no se reponen datos y los ids siguen siendo los de la spec.
- [x] 8. Corregir `CatalogoPublicoService.listarHoteles`: unir el resultado de `fn_catalogo_publico` con `hotel` para obtener `descripcion`, `direccion`, `ciudad`, `telefono` y `correo_contacto` de la sede, y seleccionar `imagenes::text` para que `leerImagenes` reciba un `String`. No cambiar la firma de la función. Verificación: `GET /api/public/hoteles` y `GET /api/public/hoteles/{slug}` devuelven los cinco campos no nulos y `descripcion` es la de la sede, no la de la categoría.
- [x] 9. Corregir `CatalogoPublicoService.listarCategorias`: unir con `categoria` para obtener `detalle` y seleccionar `imagenes::text`. Verificación: `GET /api/public/hoteles/{slug}/categorias` devuelve `detalle` con el texto de la categoría y `imagenes` con `/assets/demo/hab-101-1.jpg` para Individual.
- [x] 10. Corregir `HotelPublicoController`: usar `Meta.of(CorrelationId.current())` en los dos GET de detalle y `Meta.of(CorrelationId.current(), page, size, totalElements)` en los dos listados; aceptar `page` (0-based, por defecto 0) y `size` (1..100, por defecto 20); conservar `Cache-Control: public,max-age=60`. Verificación: `meta.correlationId` es un UUID, los listados devuelven `page`, `size` y `totalElements`, y `page`/`size` fuera de rango responden 400.
- [x] 11. Arrancar `hotel-service` con la configuración por defecto y una `hotel_db` recreada, con las credenciales del migrador en las variables de Flyway y `ddl-auto=validate`. Verificación: el log muestra `Started HotelServiceApplication`, `flyway_schema_history` tiene V1–V4 en `success` y `GET /actuator/health/readiness` responde 200 sin `MappingException`, `UnknownPathException` ni `column ... does not exist`.
- [x] 12. Ejecutar `LP01` con los cuatro GET públicos sin token. Verificación: los cuatro responden 200; `data` solo contiene las claves de `HotelPublico`/`CategoriaPublica`; no hay campos requeridos nulos; `imagenes` incluye la imagen sembrada; `meta.correlationId` está presente y los listados llevan `page`/`size`/`totalElements`; `Cache-Control` es `public,max-age=60`. Guardar las cuatro respuestas.
- [x] 13. Ejecutar `SEC03` con un token E de la sede 1: `GET /api/habitacion/buscar/{id}` con id 201. Verificación: 404 `RESOURCE_NOT_FOUND`, sin datos de la sede 2, y `SELECT id_hotel FROM habitacion WHERE id = 201` devuelve 2. Guardar la respuesta y la consulta.
- [x] 14. Ejecutar `CRUD01` (parte de hotel) con un token A: alta y edición de sede, piso, categoría y habitación; GET posteriores y DELETE. Verificación: las filas quedan persistidas, `version` incrementa en cada edición, el DELETE deja `estado=false`, `PUT /api/hotel/actualizar/{id}` sin `If-Match` responde 428 y los payloads usan solo campos de `hotel.yaml`. Guardar respuestas y consultas.
- [x] 15. Ejecutar `OCC04` (parte de hotel): lanzar `DELETE /api/piso/eliminar/{id}` sobre el piso 11 con `If-Match` y `POST /api/habitacion/registrar` en paralelo, repitiendo hasta observar ambos órdenes. Verificación: una de las dos respuestas es 409 `ACTIVE_DEPENDENCIES`, ninguna consulta muestra una habitación activa bajo un piso inactivo y los counts coinciden. Guardar respuestas y counts.
- [x] 16. Correr los comandos canónicos: `bash scripts/mvn.sh -f services/pom.xml verify`, `python scripts/validar-planes.py` y `python scripts/validar-contratos.py --base origin/main`. Verificación: `BUILD SUCCESS` de los nueve módulos, 0 errores del validador y «Los contratos solo se han ampliado. Correcto.». Adjuntar las salidas al PR de ejecución.
- [x] 17. Cierre: llevar a la sección 6 las respuestas y consultas reales, escribir la bitácora de ejecución en `docs/implementacion/decisiones/`, marcar todos los pasos y cambiar `Estado` a `cerrado` en el último commit del plan. Verificación: no quedan pasos sin marcar, los cuatro casos tienen evidencia y no hay preguntas abiertas.

## 5. Contratos afectados

- **OpenAPI:** no se modifica `docs/contracts/openapi/hotel.yaml`. El arreglo implementa lo que ya está publicado para `HotelPublico`, `CategoriaPublica`, `ApiMetaSingle` y `ApiMetaPage`. No se añaden rutas, parámetros ni códigos.
- **Eventos y comandos:** ninguno. No se tocan `docs/contracts/events/` ni los schemas de `hotel.sede.v1` o `hotel.habitacion.v1`.
- **Persistencia:** se corrigen `V3__hotel_tablas.sql` y `V4__seeds_hotel.sql`; no se añaden tablas, columnas ni índices. La FK compuesta `habitacion(id_piso, id_hotel) → piso(id, id_hotel)` se mantiene. Las bases de demo existentes se recrean; no se cambia `validate-on-migrate` ni se desactiva Flyway.
- **Errores:** no se añaden códigos. Se usan los ya existentes en [`errors.md`](../contracts/errors.md): `RESOURCE_NOT_FOUND`, `VALIDATION_ERROR`, `PRECONDITION_REQUIRED` y `ACTIVE_DEPENDENCIES`.
- **Configuración y versiones:** sin cambios. No se tocan `services/pom.xml`, `apps/*/package.json`, `infra/**` ni el `README.md` raíz.

## 6. Pruebas

- **Arranque:** `bash scripts/mvn.sh -f services/pom.xml -pl hotel-service spring-boot:run` contra una `hotel_db` recreada, con el usuario y la contraseña del migrador en las variables de Flyway y `ddl-auto=validate`; resultado esperado: `Started HotelServiceApplication`, V1–V4 en `success` y `GET /actuator/health/readiness` 200. Evidencia: log de arranque y consulta a `flyway_schema_history`, adjuntos al PR de ejecución.
- **Caso `LP01`:** sin token, ejecutar:

  ```bash
  curl -sS -D - http://localhost:8082/api/public/hoteles
  curl -sS -D - http://localhost:8082/api/public/hoteles/lima-centro
  curl -sS -D - http://localhost:8082/api/public/hoteles/lima-centro/categorias
  curl -sS -D - http://localhost:8082/api/public/hoteles/lima-centro/categorias/1
  ```

  Resultado esperado: 200 en los cuatro, solo claves de `HotelPublico`/`CategoriaPublica`, campos requeridos no nulos, `imagenes` con la imagen sembrada, `meta.correlationId` UUID y `meta.page`/`size`/`totalElements` en los listados. Evidencia: los cuatro cuerpos y el assert de claves/tipos contra `hotel.yaml`, adjuntos al PR.
- **Caso `SEC03`:** con token E de la sede 1, `GET /api/habitacion/buscar/{id}` con id 201; resultado esperado: 404 `RESOURCE_NOT_FOUND`, sin datos de la sede 2, y `SELECT id_hotel FROM habitacion WHERE id = 201` → 2. Evidencia: respuesta y consulta, adjuntas al PR.
- **Caso `CRUD01` (parte de hotel):** alta y edición de sede, piso, categoría y habitación con token A; GET posteriores y DELETE; `PUT /api/hotel/actualizar/{id}` sin `If-Match` → 428. Resultado esperado: filas persistidas, `version` incrementada en cada edición y `estado=false` tras el DELETE. Evidencia: respuestas y consultas a `hotel_db`, adjuntas al PR.
- **Caso `OCC04` (parte de hotel):** `DELETE /api/piso/eliminar/{id}` sobre el piso 11 con `If-Match` y `POST /api/habitacion/registrar` concurrentes; resultado esperado: una respuesta 409 `ACTIVE_DEPENDENCIES` y ninguna habitación activa bajo un piso inactivo. Evidencia: respuestas y counts, adjuntos al PR.
- **Comandos:** los de la sección «Verificación» del `README.md` raíz (`bash scripts/mvn.sh -f services/pom.xml verify`, Compose y `python scripts/validar-planes.py`), más `python scripts/validar-contratos.py --base origin/main`. Evidencia: salidas adjuntas al PR de ejecución.
- **Evidencia del plan reparado:** el hallazgo de que `back-hotel-catalogo` declaró `LP01`, `SEC03`, `CRUD01` y `OCC04` sin respuestas reales queda en [`IA-back-hotel-catalogo-01.md`](../implementacion/decisiones/IA-back-hotel-catalogo-01.md). Las respuestas reales de este arreglo se adjuntan al PR de ejecución y se referencian aquí.

**Evidencia obtenida (2026-10-19).** Salidas literales de la ejecución sobre `hotel_db` recreada.

- **Paso 1 — reproducción del fallo.**

  ```text
  Migration of schema "public" to version "3 - hotel tablas" failed! Changes successfully rolled back.
  SQL State  : 42703
  Message    : ERROR: column hab.publica does not exist
    Hint: Perhaps you meant to reference the column "hab.publicada".
  Line       : 86
  ```

- **Paso 2 — `grep` de la columna.** No queda ninguna referencia a `publica` como columna:

  ```text
  $ grep -nE "(hab|h2)\.publica\b" V3__hotel_tablas.sql
  (ninguna)
  ```

- **Paso 3 — migración V1–V4 y función.**

  ```text
   version |   description   | success
  ---------+-----------------+---------
         1 | api idempotency | t
         2 | outbox inbox    | t
         3 | hotel tablas    | t
         4 | seeds hotel     | t
  (4 rows)

   count
  -------
       6
  (1 row)
  ```

- **Paso 4 — el mapeo de `Habitacion.piso`.** Desaparece el `MappingException`; el siguiente
  bloqueo es el de la consulta derivada:

  ```text
  $ grep -c "No column with logical name 'id_hotel' in table 'piso'" paso4-run.log
  0
  Could not resolve attribute 'idCategoria'
  Cannot create query for method [HabitacionRepository.findByIdHotelAndIdCategoriaAndEstadoTrueOrderByIdAsc(java.lang.Integer,java.lang.Integer)]
  ```

- **Paso 5 — repositorio de habitaciones.** La consulta con filtro de categoría devuelve las
  habitaciones sembradas y `countByIdCategoriaEstadoTrue` cuenta las activas:

  ```bash
  curl -sS "http://localhost:8082/api/habitacion/listar?idHotel=1&idCategoria=1" \
    -H "Authorization: Bearer <token A>"
  ```

  ```json
  {"success":true,"message":"OK","data":[{"idHabitacion":101,"idHotel":1,"numero":"101","idPiso":11,"pisoNombre":"Primer piso","idCategoria":1,"categoriaNombre":"Individual","detalle":"Habitacion individual de demostracion","precio":70.00,"publicada":true,"estado":true,"idEstadoHabitacion":1,"estadoFisico":{"idEstadoHabitacion":1,"codigo":"LISTA","descripcion":"Lista","estado":true,"version":1},"ocupacion":{"estadoEstadia":"DESCONOCIDA","idRecepcion":null,"updatedAt":null},"blockId":null,"endpoint":null,"version":1},{"idHabitacion":303,"idHotel":1,"numero":"901","idPiso":11,"pisoNombre":"Primer piso","idCategoria":1,"categoriaNombre":"Individual","detalle":"Habitacion final editada","precio":80.00,"publicada":true,"estado":true,"idEstadoHabitacion":1,"estadoFisico":{"idEstadoHabitacion":1,"codigo":"LISTA","descripcion":"Lista","estado":true,"version":1},"ocupacion":{"estadoEstadia":"DESCONOCIDA","idRecepcion":null,"updatedAt":null},"blockId":null,"endpoint":null,"version":2},{"idHabitacion":304,"idHotel":1,"numero":"902","idPiso":11,"pisoNombre":"Primer piso","idCategoria":1,"categoriaNombre":"Individual","detalle":"Habitacion OCC04 final 2","precio":70.00,"publicada":true,"estado":true,"idEstadoHabitacion":1,"estadoFisico":{"idEstadoHabitacion":1,"codigo":"LISTA","descripcion":"Lista","estado":true,"version":1},"ocupacion":{"estadoEstadia":"DESCONOCIDA","idRecepcion":null,"updatedAt":null},"blockId":null,"endpoint":null,"version":1},{"idHabitacion":305,"idHotel":1,"numero":"903","idPiso":11,"pisoNombre":"Primer piso","idCategoria":1,"categoriaNombre":"Individual","detalle":"Habitacion OCC04 final 3","precio":70.00,"publicada":true,"estado":true,"idEstadoHabitacion":1,"estadoFisico":{"idEstadoHabitacion":1,"codigo":"LISTA","descripcion":"Lista","estado":true,"version":1},"ocupacion":{"estadoEstadia":"DESCONOCIDA","idRecepcion":null,"updatedAt":null},"blockId":null,"endpoint":null,"version":1}],"meta":{"correlationId":"163890ee-09a6-4346-9f42-7a93b19096e1"}}
  ```

  ```bash
  curl -sS -X DELETE "http://localhost:8082/api/categoria/eliminar/1" \
    -H "Authorization: Bearer <token A>" -H "If-Match: 1" -H "Idempotency-Key: <uuid>"
  ```

  ```json
  {"success":false,"message":"No se puede dar de baja mientras tenga elementos activos.","error":{"code":"ACTIVE_DEPENDENCIES","fields":{}},"meta":{"correlationId":"276c4e49-258f-4012-9d83-aed217437a0c"}}
  HTTP 409
  ```

  `findByIdCategoriaAndEstadoTrueOrderByIdAsc` no tiene llamador en el servicio; su JPQL se
  comprueba al crear el repositorio y su equivalente SQL devuelve las filas sembradas:

  ```sql
  SELECT id, numero FROM habitacion WHERE id_categoria = 1 AND estado = true ORDER BY id ASC;
   id  | numero
  -----+--------
   101 | 101
   201 | 101
   301 | 101
   303 | 901
   304 | 902
   305 | 903
  (6 rows)
  ```

- **Paso 6 — seeds deterministas.**

  ```text
   id | id_hotel | descripcion
  ----+----------+-------------
   11 |        1 | Primer piso
   21 |        2 | Primer piso
   31 |        3 | Primer piso
  (3 rows)

   id  | id_hotel | id_piso | id_categoria | numero | precio
  -----+----------+---------+--------------+--------+--------
   101 |        1 |      11 |            1 | 101    |  70.00
   102 |        1 |      11 |            2 | 102    | 100.00
   201 |        2 |      21 |            1 | 101    |  80.00
   202 |        2 |      21 |            2 | 102    | 120.00
   301 |        3 |      31 |            1 | 101    |  60.00
   302 |        3 |      31 |            2 | 102    |  90.00
  (6 rows)

   id_habitacion |            url             |            alt             | orden
  ---------------+----------------------------+----------------------------+-------
             101 | /assets/demo/hab-101-1.jpg | Habitacion de demostracion |     1
             102 | /assets/demo/hab-102-1.jpg | Habitacion de demostracion |     1
  (2 rows)

   next_piso
  -----------
          32
  (1 row)

   next_habitacion
  -----------------
               303
  (1 row)
  ```

- **Paso 7 — idempotencia de V4.** Antes y después de ejecutar V4 dos veces: `3|6|2` y
  `pisos=3, habitaciones=6, imagenes=2`; los ids siguen siendo 11/21/31 y 101…302, y `nextval`
  sigue devolviendo 32 y 303.

- **Pasos 8–10 y 12 — `LP01`.** Los cuatro GET responden 200 con `Cache-Control:
  public,max-age=60`:

  ```text
  GET /api/public/hoteles -> HTTP 200
  GET /api/public/hoteles/lima-centro -> HTTP 200
  GET /api/public/hoteles/lima-centro/categorias -> HTTP 200
  GET /api/public/hoteles/lima-centro/categorias/1 -> HTTP 200
  LP01 PASS final: 4 GET 200; claves exactas; requeridos no nulos; imagenes y meta completos
  page=-1 -> 400
  size=0 -> 400
  size=101 -> 400
  ```

  ```json
  {"success":true,"message":"OK","data":[{"idHotel":1,"slug":"lima-centro","nombre":"Hotel Demo Lima Centro","descripcion":"Sede de demostracion","direccion":"Direccion de demostracion","ciudad":"Lima","telefono":"+51 900 000 001","correoContacto":"contacto@hotel.test"},{"idHotel":2,"slug":"miraflores","nombre":"Hotel Demo Miraflores","descripcion":"Sede de demostracion","direccion":"Direccion de demostracion","ciudad":"Lima","telefono":"+51 900 000 001","correoContacto":"contacto@hotel.test"},{"idHotel":3,"slug":"arequipa","nombre":"Hotel Demo Arequipa","descripcion":"Sede de demostracion","direccion":"Direccion de demostracion","ciudad":"Arequipa","telefono":"+51 900 000 001","correoContacto":"contacto@hotel.test"}],"meta":{"correlationId":"df616261-d43e-4214-9842-67f6296ad43c","page":0,"size":20,"totalElements":3}}
  ```

  ```json
  {"success":true,"message":"OK","data":{"idHotel":1,"slug":"lima-centro","nombre":"Hotel Demo Lima Centro","descripcion":"Sede de demostracion","direccion":"Direccion de demostracion","ciudad":"Lima","telefono":"+51 900 000 001","correoContacto":"contacto@hotel.test"},"meta":{"correlationId":"aef5325c-9289-42c1-b5ec-6804642556e9"}}
  ```

  ```json
  {"success":true,"message":"OK","data":[{"idCategoria":1,"descripcion":"Individual","detalle":"Habitacion individual de demostracion","capacidad":1,"precioDesde":70.0,"moneda":"PEN","imagenes":[{"url":"/assets/demo/hab-101-1.jpg","alt":"Habitacion de demostracion"}]},{"idCategoria":2,"descripcion":"Doble","detalle":"Habitacion doble de demostracion","capacidad":2,"precioDesde":100.0,"moneda":"PEN","imagenes":[{"url":"/assets/demo/hab-102-1.jpg","alt":"Habitacion de demostracion"}]}],"meta":{"correlationId":"87d06a9e-75f5-433c-a8ae-6a227d79a5b1","page":0,"size":20,"totalElements":2}}
  ```

  ```json
  {"success":true,"message":"OK","data":{"idCategoria":1,"descripcion":"Individual","detalle":"Habitacion individual de demostracion","capacidad":1,"precioDesde":70.0,"moneda":"PEN","imagenes":[{"url":"/assets/demo/hab-101-1.jpg","alt":"Habitacion de demostracion"}]},"meta":{"correlationId":"5e28f251-1c5a-4f97-9484-474b23cde5db"}}
  ```

- **Paso 11 — arranque final sobre `hotel_db` recreada.**

  ```text
  Successfully applied 4 migrations to schema "public", now at version v4 (execution time 00:00.059s)
  Started HotelServiceApplication in 5.588 seconds (process running for 5.897)
  {"status":"UP"}
  HTTP 200
  ```

  ```text
   version |   description   | success
  ---------+-----------------+---------
         1 | api idempotency | t
         2 | outbox inbox    | t
         3 | hotel tablas    | t
         4 | seeds hotel     | t
  (4 rows)
  ```

- **Paso 13 — `SEC03`.** Token E de la sede 1, habitación 201:

  ```bash
  curl -sS "http://localhost:8082/api/habitacion/buscar/201" \
    -H "Authorization: Bearer <token E sede 1>" \
    -H "X-Correlation-Id: 33333333-3333-4333-8333-333333333333"
  ```

  ```json
  {"success":false,"message":"No encontramos lo que buscas.","error":{"code":"RESOURCE_NOT_FOUND","fields":{}},"meta":{"correlationId":"33333333-3333-4333-8333-333333333333"}}
  HTTP 404
  ```

  ```text
   id  | id_hotel
  -----+----------
   201 |        2
  (1 row)
  ```

- **Paso 14 — `CRUD01`.**

  ```json
  POST /api/hotel/registrar -> HTTP 201
  {"success":true,"message":"OK","data":{"idHotel":4,"slug":"final-crud-sede-1792454398","nombre":"Sede Final CRUD","descripcion":"Sede final de prueba","direccion":"Calle Final 1","ciudad":"Lima","telefono":"+51 900 000 888","correoContacto":"final@hotel.test","estado":true,"publicado":false,"version":1},"meta":{"correlationId":"e479bd85-1032-4711-8428-378575b8a383"}}

  PUT sin If-Match -> HTTP 428
  {"success":false,"message":"Falta la versión del registro.","error":{"code":"PRECONDITION_REQUIRED","fields":{}},"meta":{"correlationId":"a0805d4e-aaff-415e-aaad-0ff71b37c253"}}

  PUT con If-Match 1 -> HTTP 200
  {"success":true,"message":"OK","data":{"idHotel":4,"slug":"final-crud-sede-1792454398","nombre":"Sede Final CRUD editada","descripcion":"Sede final de prueba","direccion":"Calle Final 1","ciudad":"Lima","telefono":"+51 900 000 888","correoContacto":"final@hotel.test","estado":true,"publicado":false,"version":2},"meta":{"correlationId":"85eb5ca9-05d5-4f44-b4da-566a85b8b77e"}}

  DELETE con If-Match 2 -> HTTP 200
  {"success":true,"message":"OK","data":{"idHotel":4,"slug":"final-crud-sede-1792454398","nombre":"Sede Final CRUD editada","descripcion":"Sede final de prueba","direccion":"Calle Final 1","ciudad":"Lima","telefono":"+51 900 000 888","correoContacto":"final@hotel.test","estado":false,"publicado":false,"version":3},"meta":{"correlationId":"462b5bc6-8113-4f40-92d3-b3ac1d7e081a"}}
  ```

  ```json
  POST /api/piso/registrar -> HTTP 201
  {"success":true,"message":"OK","data":{"idPiso":32,"idHotel":1,"descripcion":"Piso Final 1792454399","estado":true,"version":1},"meta":{"correlationId":"33ce3428-481d-4f8d-8c82-7d54facfe092"}}
  PUT /api/piso/actualizar/32 -> HTTP 200
  {"success":true,"message":"OK","data":{"idPiso":32,"idHotel":1,"descripcion":"Piso Final 1792454399 editado","estado":true,"version":2},"meta":{"correlationId":"dea69552-c4fb-435c-b12b-2de7b7f8c28b"}}
  DELETE /api/piso/eliminar/32 -> HTTP 200
  {"success":true,"message":"OK","data":{"idPiso":32,"idHotel":1,"descripcion":"Piso Final 1792454399 editado","estado":false,"version":3},"meta":{"correlationId":"cadb023f-9d0c-4761-8f29-de6bd45500d4"}}
  ```

  ```json
  POST /api/categoria/registrar -> HTTP 201
  {"success":true,"message":"OK","data":{"idCategoria":3,"descripcion":"Cat Final 1792454400","detalle":"Detalle final","capacidad":2,"estado":true,"version":1},"meta":{"correlationId":"84b2454a-00af-4ce3-a85d-66ea57e080ae"}}
  PUT /api/categoria/actualizar/3 -> HTTP 200
  {"success":true,"message":"OK","data":{"idCategoria":3,"descripcion":"Cat Final 1792454400 editada","detalle":"Detalle final editado","capacidad":2,"estado":true,"version":2},"meta":{"correlationId":"f55706d3-d0f1-4c72-9257-4eb5468a812a"}}
  DELETE /api/categoria/eliminar/3 -> HTTP 200
  {"success":true,"message":"OK","data":{"idCategoria":3,"descripcion":"Cat Final 1792454400 editada","detalle":"Detalle final editado","capacidad":2,"estado":false,"version":3},"meta":{"correlationId":"b75874a0-7813-4a12-9e0a-44861553907e"}}
  ```

  ```json
  POST /api/habitacion/registrar -> HTTP 201
  {"success":true,"message":"OK","data":{"idHabitacion":303,"idHotel":1,"numero":"901","idPiso":11,"pisoNombre":"Primer piso","idCategoria":1,"categoriaNombre":"Individual","detalle":"Habitacion final","precio":75.50,"publicada":true,"estado":true,"idEstadoHabitacion":1,"estadoFisico":{"idEstadoHabitacion":1,"codigo":"LISTA","descripcion":"Lista","estado":true,"version":1},"ocupacion":{"estadoEstadia":"DESCONOCIDA","idRecepcion":null,"updatedAt":null},"blockId":null,"endpoint":null,"version":1},"meta":{"correlationId":"147bcae9-84a8-4b0d-a669-829b27a25e2d"}}
  PUT /api/habitacion/actualizar/303 -> HTTP 200
  {"success":true,"message":"OK","data":{"idHabitacion":303,"idHotel":1,"numero":"901","idPiso":11,"pisoNombre":"Primer piso","idCategoria":1,"categoriaNombre":"Individual","detalle":"Habitacion final editada","precio":80.00,"publicada":true,"estado":true,"idEstadoHabitacion":1,"estadoFisico":{"idEstadoHabitacion":1,"codigo":"LISTA","descripcion":"Lista","estado":true,"version":1},"ocupacion":{"estadoEstadia":"DESCONOCIDA","idRecepcion":null,"updatedAt":null},"blockId":null,"endpoint":null,"version":2},"meta":{"correlationId":"e70511fc-b3af-42eb-b40b-c8a4c1d71f7c"}}
  ```

  ```text
   id | estado | version        (hotel)
  ----+--------+---------
    4 | f      |       3
   id | estado | version        (piso)
  ----+--------+---------
   32 | f      |       3
   id | estado | version        (categoria)
  ----+--------+---------
    3 | f      |       3
   id  |         detalle          | precio | version   (habitacion)
  -----+--------------------------+--------+---------
   303 | Habitacion final editada |  80.00 |       2
   id_habitacion |            url             | orden
  ---------------+----------------------------+-------
             303 | /assets/demo/hab-901-2.jpg |     1
  ```

- **Paso 15 — `OCC04`.** Dos iteraciones con `DELETE` del piso 11 y `POST` de habitación
  concurrentes:

  ```text
  DELETE -> HTTP 409 {"success":false,"message":"No se puede dar de baja mientras tenga elementos activos.","error":{"code":"ACTIVE_DEPENDENCIES","fields":{}},"meta":{"correlationId":"83734f8c-3b88-441c-8099-54811be0fe23"}}
  POST   -> HTTP 201 {"success":true,"message":"OK","data":{"idHabitacion":304,"idHotel":1,"numero":"902","idPiso":11,"pisoNombre":"Primer piso","idCategoria":1,"categoriaNombre":"Individual","detalle":"Habitacion OCC04 final 2","precio":70.00,"publicada":true,"estado":true,"idEstadoHabitacion":1,"estadoFisico":{"idEstadoHabitacion":1,"codigo":"LISTA","descripcion":"Lista","estado":true,"version":1},"ocupacion":{"estadoEstadia":"DESCONOCIDA","idRecepcion":null,"updatedAt":null},"blockId":null,"endpoint":null,"version":1},"meta":{"correlationId":"44dd618b-0eaf-4bbd-8e8b-7370df9511a6"}}

  DELETE -> HTTP 409 {"success":false,"message":"No se puede dar de baja mientras tenga elementos activos.","error":{"code":"ACTIVE_DEPENDENCIES","fields":{}},"meta":{"correlationId":"a1b88d93-266c-4c89-b245-8ef43bc9974d"}}
  POST   -> HTTP 201 {"success":true,"message":"OK","data":{"idHabitacion":305,"idHotel":1,"numero":"903","idPiso":11,"pisoNombre":"Primer piso","idCategoria":1,"categoriaNombre":"Individual","detalle":"Habitacion OCC04 final 3","precio":70.00,"publicada":true,"estado":true,"idEstadoHabitacion":1,"estadoFisico":{"idEstadoHabitacion":1,"codigo":"LISTA","descripcion":"Lista","estado":true,"version":1},"ocupacion":{"estadoEstadia":"DESCONOCIDA","idRecepcion":null,"updatedAt":null},"blockId":null,"endpoint":null,"version":1},"meta":{"correlationId":"52f3747e-b37b-4fc3-be1f-a151d1c3f91b"}}
  ```

  ```text
   id | estado | version
  ----+--------+---------
   11 | t      |       1

   activas_piso_11
  -----------------
                 5

   activas_bajo_piso_inactivo
  ----------------------------
                            0
  ```

- **Paso 16 — comandos canónicos.**

  ```text
  [INFO] BUILD SUCCESS
  [INFO] Hotel services (agregador) ......................... SUCCESS
  [INFO] identity-service ................................... SUCCESS
  [INFO] hotel-service ...................................... SUCCESS
  [INFO] reception-service .................................. SUCCESS
  [INFO] inventory-service .................................. SUCCESS
  [INFO] sales-service ...................................... SUCCESS
  [INFO] reporting-service .................................. SUCCESS
  [INFO] notification-service ............................... SUCCESS
  [INFO] api-gateway ........................................ SUCCESS
  [INFO] discovery-server ................................... SUCCESS
  ```

  ```text
  26 plan(es) revisado(s); 26 previsto(s) en el catálogo; 0 error(es), 0 aviso(s).
  Contratos: 8 OpenAPI, 20 schemas de evento. Regla: aditivos frente a origin/main.
  Los contratos solo se han ampliado. Correcto.
  ```

## 7. Preguntas abiertas

Ninguna. Las decisiones de nivel 2 de este arreglo quedan registradas en [`IA-back-arreglo-hotel-arranque-01.md`](../implementacion/decisiones/IA-back-arreglo-hotel-arranque-01.md) y [`IA-back-arreglo-hotel-arranque-02.md`](../implementacion/decisiones/IA-back-arreglo-hotel-arranque-02.md): corregir V3/V4 en sitio y recrear la base de demo; modelar `Habitacion.piso` por `id_piso` conservando la FK compuesta; completar la proyección pública sin cambiar la firma de `fn_catalogo_publico` ni el OpenAPI; y devolver los 409 con los códigos que ya publica `errors.md`.

## 8. Criterio de terminado

- [x] Todos los pasos marcados con su verificación.
- [x] `hotel-service` arranca sobre una `hotel_db` vacía con Flyway V1–V4 y `ddl-auto=validate`; `GET /actuator/health/readiness` responde 200.
- [x] `LP01` responde 200 en los cuatro GET públicos con las claves y tipos de `HotelPublico`/`CategoriaPublica`, imágenes y `meta` completos.
- [x] `SEC03` devuelve 404 `RESOURCE_NOT_FOUND` para la habitación 201 con token E de la sede 1.
- [x] `CRUD01` deja filas persistidas, versiones incrementadas, bajas lógicas y 428 sin `If-Match`.
- [x] `OCC04` deja una respuesta 409 `ACTIVE_DEPENDENCIES` y ninguna habitación activa bajo un piso inactivo.
- [x] `python scripts/validar-planes.py` termina con 0 errores y `python scripts/validar-contratos.py --base origin/main` confirma que los contratos solo se han ampliado.
- [x] Las respuestas y consultas reales están adjuntas al PR de ejecución y el hallazgo del plan reparado está en `docs/implementacion/decisiones/`.
- [x] Sin preguntas abiertas y con `Estado: cerrado` en el último commit del plan.
