# back-arreglo-hotel-arranque — arranque de hotel-service y evidencia real del catálogo (F2)

| Campo | Valor |
|---|---|
| Plan | `back-arreglo-hotel-arranque` |
| Ámbito | `back` |
| Componente | `hotel-service` |
| Fase | `F2` |
| Modo | `directo` |
| Estado | `en curso` |
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
- [ ] 13. Ejecutar `SEC03` con un token E de la sede 1: `GET /api/habitacion/buscar/{id}` con id 201. Verificación: 404 `RESOURCE_NOT_FOUND`, sin datos de la sede 2, y `SELECT id_hotel FROM habitacion WHERE id = 201` devuelve 2. Guardar la respuesta y la consulta.
- [x] 14. Ejecutar `CRUD01` (parte de hotel) con un token A: alta y edición de sede, piso, categoría y habitación; GET posteriores y DELETE. Verificación: las filas quedan persistidas, `version` incrementa en cada edición, el DELETE deja `estado=false`, `PUT /api/hotel/actualizar/{id}` sin `If-Match` responde 428 y los payloads usan solo campos de `hotel.yaml`. Guardar respuestas y consultas.
- [x] 15. Ejecutar `OCC04` (parte de hotel): lanzar `DELETE /api/piso/eliminar/{id}` sobre el piso 11 con `If-Match` y `POST /api/habitacion/registrar` en paralelo, repitiendo hasta observar ambos órdenes. Verificación: una de las dos respuestas es 409 `ACTIVE_DEPENDENCIES`, ninguna consulta muestra una habitación activa bajo un piso inactivo y los counts coinciden. Guardar respuestas y counts.
- [ ] 16. Correr los comandos canónicos: `bash scripts/mvn.sh -f services/pom.xml verify`, `python scripts/validar-planes.py` y `python scripts/validar-contratos.py --base origin/main`. Verificación: `BUILD SUCCESS` de los nueve módulos, 0 errores del validador y «Los contratos solo se han ampliado. Correcto.». Adjuntar las salidas al PR de ejecución.
- [ ] 17. Cierre: llevar a la sección 6 las respuestas y consultas reales, escribir la bitácora de ejecución en `docs/implementacion/decisiones/`, marcar todos los pasos y cambiar `Estado` a `cerrado` en el último commit del plan. Verificación: no quedan pasos sin marcar, los cuatro casos tienen evidencia y no hay preguntas abiertas.

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

## 7. Preguntas abiertas

Ninguna. Las decisiones de nivel 2 de este arreglo quedan registradas en [`IA-back-arreglo-hotel-arranque-01.md`](../implementacion/decisiones/IA-back-arreglo-hotel-arranque-01.md): corregir V3/V4 en sitio y recrear la base de demo; modelar `Habitacion.piso` por `id_piso` conservando la FK compuesta; y completar la proyección pública sin cambiar la firma de `fn_catalogo_publico` ni el OpenAPI.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] `hotel-service` arranca sobre una `hotel_db` vacía con Flyway V1–V4 y `ddl-auto=validate`; `GET /actuator/health/readiness` responde 200.
- [ ] `LP01` responde 200 en los cuatro GET públicos con las claves y tipos de `HotelPublico`/`CategoriaPublica`, imágenes y `meta` completos.
- [ ] `SEC03` devuelve 404 `RESOURCE_NOT_FOUND` para la habitación 201 con token E de la sede 1.
- [ ] `CRUD01` deja filas persistidas, versiones incrementadas, bajas lógicas y 428 sin `If-Match`.
- [ ] `OCC04` deja una respuesta 409 `ACTIVE_DEPENDENCIES` y ninguna habitación activa bajo un piso inactivo.
- [ ] `python scripts/validar-planes.py` termina con 0 errores y `python scripts/validar-contratos.py --base origin/main` confirma que los contratos solo se han ampliado.
- [ ] Las respuestas y consultas reales están adjuntas al PR de ejecución y el hallazgo del plan reparado está en `docs/implementacion/decisiones/`.
- [ ] Sin preguntas abiertas y con `Estado: cerrado` en el último commit del plan.
