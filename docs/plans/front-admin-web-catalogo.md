# Plan `front-admin-web-catalogo`

| Campo | Valor |
|---|---|
| Plan | `front-admin-web-catalogo` |
| Ámbito | `front` |
| Componente | `admin-web` |
| Fase | `F2` |
| Modo | `directo` |
| Estado | `en curso` |
| Depende de | `front-admin-web-base`, `back-identity-personas`, `back-hotel-catalogo` |
| Spec | [08-admin-web.md §1 Reglas transversales](../especificacion/08-admin-web.md) · [08-admin-web.md §2 Rutas Angular y guards](../especificacion/08-admin-web.md) · [08-admin-web.md §3 Formularios](../especificacion/08-admin-web.md) · [08-admin-web.md §4 Estados por pantalla](../especificacion/08-admin-web.md) · [08-admin-web.md §5 Menú por rol](../especificacion/08-admin-web.md) · [02-landing-y-panel.md § Panel: matriz de acceso](../especificacion/02-landing-y-panel.md) · [02-landing-y-panel.md § Formatos de UI](../especificacion/02-landing-y-panel.md) · [03-contrato-comun.md § Headers](../especificacion/03-contrato-comun.md) · [03-contrato-comun.md § Respuestas](../especificacion/03-contrato-comun.md) · [03-contrato-comun.md § Identidad y sedes](../especificacion/03-contrato-comun.md) · [errors.md § Genéricos](../contracts/errors.md) · [errors.md § Identity](../contracts/errors.md) · [errors.md § Hotel](../contracts/errors.md) · [errors.md § Cómo lo usa el frontend](../contracts/errors.md) · [../DESIGN.md §10 Tokens](../DESIGN.md) · [../DESIGN.md §11 Checklist](../DESIGN.md) · [../DESIGN.md §4 Componentes](../DESIGN.md) · [../DESIGN.md §6 Formatos](../DESIGN.md) · [../DESIGN.md §7 Estados y errores](../DESIGN.md) · [contracts/README.md §1 Reglas transversales](../contracts/README.md) · [contracts/README.md §3.1 Auth y personas](../contracts/README.md) · [contracts/README.md §3.2 Sedes, pisos, categorías, habitaciones](../contracts/README.md) · [admin-web.yaml — rutas de personas y catálogo](../contracts/openapi/admin-web.yaml) · [identity/api.md § REST](../especificacion/microservicios/identity/api.md) · [identity/api.md § DTO de entrada](../especificacion/microservicios/identity/api.md) · [hotel/api.md § Sedes, pisos y categorías](../especificacion/microservicios/hotel/api.md) · [hotel/api.md § Habitaciones y físico](../especificacion/microservicios/hotel/api.md) · [hotel/api.md § DTO](../especificacion/microservicios/hotel/api.md) · [06-migracion-y-pruebas.md §4 Cambios frontend](../especificacion/06-migracion-y-pruebas.md) |
| Casos | `—` |
| Change | `—` |
| Contenido | Personas y roles; sedes, pisos, categorías y habitaciones con sus formularios, validaciones y estados de [08](../especificacion/08-admin-web.md). |

> Nota: este plan sigue la norma vigente de `docs/plans/README.md` (§§4–5, §7–§8), la estructura de `docs/plans/_plantilla.md` y su fila de `docs/plans/catalogo.md` (§§1–4). No toca `docs/especificacion/**`, `docs/contracts/errors.md`, `docs/plans/README.md` ni `docs/plans/_prompts.md`.

## 1. Objetivo

Dejar operativo en `apps/admin-web` el módulo de catálogo del panel: personas y roles, sedes, pisos, categorías y habitaciones (alta y edición con imágenes) con sus formularios, validaciones y estados, todo contra el Gateway (`http://localhost:8080`).

Es verificable cuando: cada pantalla lista, crea, edita y da de baja según rol con sus mensajes exactos; cada pantalla usa los tokens de [../DESIGN.md §10](../DESIGN.md) y pasa el checklist de [../DESIGN.md §11](../DESIGN.md); y `python scripts/validar-planes.py` da 0 errores.

## 2. Alcance

**Entra:**

- Rutas Angular sobre el shell y los guards de `front-admin-web-base`: `/app/personas`, `/app/personas/nuevo`, `/app/personas/:id/editar`, `/app/tipos-persona`, `/app/sedes`, `/app/sedes/nuevo`, `/app/sedes/:id/editar`, `/app/pisos`, `/app/pisos/nuevo`, `/app/pisos/:id/editar`, `/app/categorias`, `/app/categorias/nuevo`, `/app/categorias/:id/editar`, `/app/habitaciones`, `/app/habitaciones/nuevo`, `/app/habitaciones/:id/editar`; lectura para ADMIN y EMPLEADO de su alcance, mutación solo ADMIN; el guard oculta y el backend decide.
- Personas vía Gateway: `GET /api/persona/listar` (paginado con `idTipoPersona` y `documento`; EMPLEADO solo CLIENTE con `documento` exacto), `GET /api/persona/buscar/{id}`, `POST /api/persona/registrar`, `PUT /api/persona/actualizar/{id}` con `If-Match`, `DELETE /api/persona/eliminar/{id}` con `If-Match`; formulario con las validaciones y mensajes por campo, documento no editable tras crear, sedes según rol, clave obligatoria para personal nuevo y protección del último ADMIN.
- Roles vía Gateway: `GET /api/tipopersona/listar`, `GET /api/tipopersona/buscar/{id}`, `PUT /api/tipopersona/actualizar/{id}` con `If-Match` solo para la etiqueta; sin crear ni eliminar.
- Sedes, pisos y categorías vía Gateway con CRUD completo (listar paginado, buscar, registrar, actualizar con `If-Match`, eliminar con `If-Match`); baja con explicación ante dependencias activas; piso siempre atado a su sede sin cambio de sede por PUT.
- Habitaciones vía Gateway: `GET /api/habitacion/listar` por sede con filtro opcional de categoría, `GET /api/habitacion/buscar/{id}`, `POST /api/habitacion/registrar` (nace LISTA), `PUT /api/habitacion/actualizar/{id}` con `If-Match` solo de catálogo (detalle, precio, publicada, imágenes; sin físico, número ni sede); formulario con imágenes (límite, URL permitida, texto alternativo y orden) y sus mensajes por campo.
- Estados físicos vía Gateway: `GET /api/estadohabitacion/listar` y `GET /api/estadohabitacion/buscar/{id}` para etiquetas y píldoras, `PUT /api/estadohabitacion/actualizar/{id}` con `If-Match` solo para la etiqueta; sin crear ni eliminar.
- Formularios, validaciones y mensajes exactos por campo; estados de vacío, carga con skeleton y error con mensaje del catálogo, campos resaltados, acción esperada y `correlationId` copiable; interceptor, `Idempotency-Key` una por intención, `If-Match` desde `ETag` y manejo 401 aportados por `front-admin-web-base`.

**Fuera de alcance:**

- Operaciones de habitación (iniciar y completar mantenimiento, dar de baja, reactivar, polling de operaciones) y tablero de limpieza: lo monta `front-admin-web-recepcion` sobre estas pantallas.
- Estadías y cierre, productos y stock, ventas y carrito, reportes y área de cliente: planes propios.
- Mensajería: este plan no produce ni consume mensajería; el catálogo no le asigna ningún topic, comando ni grupo.
- Persistencia: sin tablas ni migraciones Flyway; sin cambios de schemas.
- Identidad por cabeceras falsificables: nunca `X-User-Id`, `X-Role` ni `X-Hotel-Id`; solo JWT validado.
- Puertos de servicios, Eureka, brokers o base de datos directos; solo Gateway `http://localhost:8080`, con avance previo contra mocks del OpenAPI.
- Versiones distintas de la fijada para este front: solo Angular 22.2.2; sin `latest`.
- Alcance cerrado: sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server.
- Ficheros fuera de su ámbito: solo toca `apps/admin-web/**` y su propio `docs/plans/front-admin-web-catalogo.md`; nunca `docs/especificacion/**`, `docs/contracts/errors.md`, `docs/plans/README.md` ni `docs/plans/_prompts.md`.

## 3. Contexto

La spec ya fija el qué; este plan solo ordena el cómo:

- Reglas transversales del panel (solo Gateway, JWT en memoria con logout que borra token, 401 hacia relogin, rol y sede solo como UX con revalidación en backend, clave de idempotencia una por intención, `If-Match` desde `ETag`, formatos fijos, errores del catálogo), según [08-admin-web.md §1](../especificacion/08-admin-web.md).
- Rutas Angular del módulo, roles por pantalla y límites del área de cliente, según [08-admin-web.md §2](../especificacion/08-admin-web.md) y [02-landing-y-panel.md § Panel: matriz de acceso](../especificacion/02-landing-y-panel.md).
- Campos, validaciones y mensajes de persona, sede, piso, categoría y habitación con imágenes, según [08-admin-web.md §3](../especificacion/08-admin-web.md).
- Estados de vacío, carga y error por pantalla, según [08-admin-web.md §4](../especificacion/08-admin-web.md).
- Menú visible por rol (personas y roles, catálogo físico con lectura asignada para EMPLEADO) y regla de que el backend es la autoridad, según [08-admin-web.md §5](../especificacion/08-admin-web.md).
- Formatos obligatorios de moneda, fechas y etiquetas con locale es-PE, según [02-landing-y-panel.md § Formatos de UI](../especificacion/02-landing-y-panel.md) y [../DESIGN.md §6](../DESIGN.md).
- Headers, idempotencia, envelope con `success`, `message`, `data`, `meta` y `error`, paginación, filtro por sede e identidad por JWT, según [03-contrato-comun.md § Headers](../especificacion/03-contrato-comun.md), [§ Respuestas](../especificacion/03-contrato-comun.md) y [§ Identidad y sedes](../especificacion/03-contrato-comun.md).
- Base, headers, envelope, convenciones de paginación y versión, y superficie de personas y de sedes, pisos, categorías y habitaciones, según [contracts/README.md §1](../contracts/README.md), [§3.1](../contracts/README.md) y [§3.2](../contracts/README.md), [admin-web.yaml](../contracts/openapi/admin-web.yaml), [identity/api.md § REST](../especificacion/microservicios/identity/api.md) con sus DTO de [§ DTO de entrada](../especificacion/microservicios/identity/api.md), y [hotel/api.md § Sedes, pisos y categorías](../especificacion/microservicios/hotel/api.md), [§ Habitaciones y físico](../especificacion/microservicios/hotel/api.md) y [§ DTO](../especificacion/microservicios/hotel/api.md).
- Mensajes para UI y acciones esperadas como fuente única, sin redactar propios, según [errors.md § Genéricos](../contracts/errors.md), [§ Identity](../contracts/errors.md), [§ Hotel](../contracts/errors.md) y [§ Cómo lo usa el frontend](../contracts/errors.md).
- Visual con tokens, componentes, estados y accesibilidad, según [../DESIGN.md §10](../DESIGN.md), [§11](../DESIGN.md), [§4](../DESIGN.md) y [§7](../DESIGN.md); ante conflicto, manda la spec.
- Cambios frontend aplicables (base Gateway, selector de sede como filtro de trabajo sin valor de autorización, sin campos calculados, clave una por intención con nueva clave tras corrección, `ETag` con recarga ante 412, quitar CRUD de códigos fijos, baja lógica con texto preciso), según [06-migracion-y-pruebas.md §4](../especificacion/06-migracion-y-pruebas.md).
- Dependencias: `front-admin-web-base` deja shell, guards, interceptor, sesión, mensajes y pipes; `back-identity-personas` deja personas, tipos y auditoría de lecturas; `back-hotel-catalogo` deja sedes, pisos, categorías, habitaciones, estados físicos y seeds; este plan no implementa backend y no avanza sin esos contratos estables o sus mocks del OpenAPI.

Decisiones de este plan (no son reglas nuevas): avanzar contra los mocks del OpenAPI y cerrar contra Gateway real; la edición de etiqueta de estado físico (solo ADMIN) vive como diálogo dentro de `/app/habitaciones` porque la spec no le define ruta propia, sin crear ni eliminar; el selector de sede es filtro de trabajo en los listados por sede; vocabulario con minúscula de oración ("Dar de baja", nunca "Eliminar"; nunca "disponible" ni "reservar"). Este plan no decide reglas que la spec no fija.

## 4. Pasos

- [x] 1. Montar las rutas del módulo sobre el shell y los guards de `front-admin-web-base` (personas, tipos-persona, sedes, pisos, categorías, habitaciones con sus altas y ediciones) con visibilidad según la matriz por rol. Verificación: el enrutador expone esas rutas, el menú visible coincide con la matriz y el build termina con código 0.
- [x] 2. Aplicar los tokens de [../DESIGN.md §10](../DESIGN.md) en estas pantallas (tablas, formularios, píldoras de estado, diálogos y avisos), sin inventar colores, tipografías, componentes ni movimientos. Verificación: inspección muestra solo tokens del sistema y el build termina con código 0.
- [x] 3. Crear modelos y cliente HTTP tipados con los schemas de `admin-web.yaml` (Persona, TipoPersona, Sede, Piso, Categoria, Habitacion, EstadoFisico) contra el Gateway, con paginación `page` y `size`, filtro `estado` en maestros e `idHotel` en listados por sede. Verificación: compilación con código 0 y el código solo referencia la base del Gateway.
- [x] 4. Construir `/app/personas` con listado y búsqueda paginados, filtros por tipo y documento exacto, y estados de vacío, skeleton y error. Verificación: contra mocks un 200 pinta filas y píldoras, el vacío muestra su frase exacta y un 403 muestra su mensaje sin datos.
- [x] 5. Construir el alta y la edición de persona con sus validaciones y mensajes por campo, documento bloqueado en edición, sedes según rol, clave obligatoria para personal nuevo, `Idempotency-Key` y `If-Match` desde `ETag`. Verificación: 201 en alta válida y 200 en edición válida; 400 resalta campos; versión vieja devuelve `PRECONDITION_FAILED` con recarga.
- [x] 6. Construir la baja de persona (solo ADMIN) con diálogo de "Dar de baja" y protección del último ADMIN. Verificación: 200 deja `estado` en falso y el último ADMIN devuelve `LAST_ADMIN_PROTECTED` con su mensaje.
- [ ] 7. Construir `/app/tipos-persona` con lectura y edición de etiqueta (solo ADMIN), sin botones ni llamadas de crear o eliminar. Verificación: PUT válido devuelve 200 con versión incrementada y el código muestra que registrar y eliminar no se invocan.
- [x] 8. Construir sedes con CRUD completo y baja explicada ante habitaciones activas. Verificación: 201 en alta, 200 en edición y baja; dependencias activas devuelven `ACTIVE_DEPENDENCIES` con su mensaje y su acción.
- [x] 9. Construir pisos por sede con selector de sede requerido, sin cambio de sede por PUT. Verificación: el alta exige sede, la edición solo cambia descripción y estado, y la baja devuelve 200 o `ACTIVE_DEPENDENCIES`.
- [x] 10. Construir categorías globales con CRUD completo y unicidad de descripción. Verificación: duplicada devuelve `DUPLICATE_RESOURCE` con su mensaje y la baja con dependencias devuelve `ACTIVE_DEPENDENCIES`.
- [x] 11. Construir `/app/habitaciones` con listado por sede, filtro por categoría, detalle por búsqueda y píldoras de físico y ocupación ("Libre", "Ocupada", "Sin datos"; nunca "Disponible"). Verificación: contra mocks el listado filtra por sede y categoría y cada fila muestra sus dos píldoras.
- [x] 12. Construir el alta y la edición de habitación (piso de la sede, número único entre activas, detalle, precio mayor que 0, publicada e imágenes con URL permitida, texto alternativo y orden único); el alta nace LISTA y la edición no toca físico, número ni sede. Verificación: 201 y 200 válidos; número duplicado devuelve `DUPLICATE_RESOURCE`; 400 resalta el campo de imagen inválida.
- [x] 13. Construir el diálogo de etiqueta de estado físico (solo ADMIN) con `If-Match`, sin crear ni eliminar. Verificación: PUT válido devuelve 200, sin `If-Match` devuelve `PRECONDITION_REQUIRED` y con versión vieja `PRECONDITION_FAILED` con recarga.
- [x] 14. Aplicar el manejo de errores del catálogo en estas pantallas (`VALIDATION_ERROR`, `UNAUTHENTICATED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `RATE_LIMITED`, `IDEMPOTENCY_CONFLICT`, `DUPLICATE_RESOURCE`, `LAST_ADMIN_PROTECTED`, `FIXED_ROLE_CATALOG`, `FIXED_STATE_CATALOG`, `ACTIVE_DEPENDENCIES`, `STAFF_CREDENTIAL_REQUIRED`, `PRECONDITION_FAILED`, `PRECONDITION_REQUIRED`, `DEPENDENCY_UNAVAILABLE`, `INTERNAL_ERROR`), con mensaje para UI, campos resaltados, acción esperada y `correlationId` copiable, y código desconocido con mensaje y correlación sin traducir. Verificación: simulando cada código la UI muestra el mensaje del catálogo con su acción y el `correlationId` visible.
- [x] 15. Cerrar cada pantalla con sus estados de vacío, carga con skeleton y error, accesibilidad con teclado, foco visible, etiquetas y contraste, y anchos de 390px y 1024px con movimiento reducido respetado, pasando el checklist de [../DESIGN.md §11](../DESIGN.md). Verificación: recorrido por teclado completo, checklist firmado por pantalla y vocabulario controlado sin "Eliminar", "disponible" ni "reservar".
- [x] 16. Integrar contra el Gateway real con las dependencias cerradas y correr el validador. Verificación: respuestas reales guardadas por pantalla y `python scripts/validar-planes.py` desde la raíz con 0 errores; cada aviso resuelto o justificado en el PR.

## 5. Contratos afectados

- OpenAPI: `GET /api/persona/listar`, `GET /api/persona/buscar/{id}`, `POST /api/persona/registrar`, `PUT /api/persona/actualizar/{id}`, `DELETE /api/persona/eliminar/{id}`, `GET /api/tipopersona/listar`, `GET /api/tipopersona/buscar/{id}`, `PUT /api/tipopersona/actualizar/{id}`, `GET /api/hotel/listar`, `GET /api/hotel/buscar/{id}`, `POST /api/hotel/registrar`, `PUT /api/hotel/actualizar/{id}`, `DELETE /api/hotel/eliminar/{id}`, `GET /api/piso/listar`, `GET /api/piso/buscar/{id}`, `POST /api/piso/registrar`, `PUT /api/piso/actualizar/{id}`, `DELETE /api/piso/eliminar/{id}`, `GET /api/categoria/listar`, `GET /api/categoria/buscar/{id}`, `POST /api/categoria/registrar`, `PUT /api/categoria/actualizar/{id}`, `DELETE /api/categoria/eliminar/{id}`, `GET /api/habitacion/listar`, `GET /api/habitacion/buscar/{id}`, `POST /api/habitacion/registrar`, `PUT /api/habitacion/actualizar/{id}`, `GET /api/estadohabitacion/listar`, `GET /api/estadohabitacion/buscar/{id}` y `PUT /api/estadohabitacion/actualizar/{id}` de `contracts/openapi/admin-web.yaml`; solo consumo vía Gateway, sin altas ni cambios de schemas.
- Eventos y comandos: ninguno; este plan no produce ni consume mensajería.
- Persistencia: ninguna; sin tablas ni migraciones Flyway.
- Errores: `VALIDATION_ERROR`, `UNAUTHENTICATED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `RATE_LIMITED`, `IDEMPOTENCY_CONFLICT`, `DUPLICATE_RESOURCE`, `LAST_ADMIN_PROTECTED`, `FIXED_ROLE_CATALOG`, `FIXED_STATE_CATALOG`, `ACTIVE_DEPENDENCIES`, `STAFF_CREDENTIAL_REQUIRED`, `PRECONDITION_FAILED`, `PRECONDITION_REQUIRED`, `DEPENDENCY_UNAVAILABLE` e `INTERNAL_ERROR` de `errors.md`; la UI usa el mensaje para UI del catálogo y su acción esperada.

## 6. Pruebas

- Pantallas de personas: invocar listar con y sin `documento`, alta, edición con y sin `If-Match` y baja vía Gateway; resultado exigido: 200 en válidas, 400 con campos resaltados, 428 sin versión, 412 con versión vieja y 409 ante el último ADMIN; evidencia: respuestas HTTP guardadas y capturas de formularios con sus mensajes.
- Pantalla de roles: invocar listar y PUT de etiqueta vía Gateway; resultado exigido: 200 con versión incrementada y sin acciones de crear ni eliminar en la UI; evidencia: respuestas y captura de la pantalla.
- Pantallas de sedes, pisos y categorías: invocar el CRUD completo vía Gateway; resultado exigido: 201 en altas, 200 en ediciones y bajas, y 409 explicado ante dependencias activas; evidencia: respuestas y `ETag` guardados.
- Pantallas de habitaciones: invocar listar por sede con y sin categoría, alta y edición vía Gateway; resultado exigido: nace LISTA, la edición no cambia físico, número ni sede, y el duplicado devuelve su código con su mensaje; evidencia: respuestas y capturas de píldoras y galería.
- Etiqueta de estado físico: invocar listar y PUT vía Gateway; resultado exigido: 200 válido, 428 sin versión y 412 con versión vieja; evidencia: respuestas guardadas.
- Errores y cierre visual: simular cada código de la sección 5 y recorrer cada pantalla con teclado en 390px y 1024px; resultado exigido: mensaje del catálogo con acción y `correlationId`, y checklist firmado por pantalla; evidencia: capturas y checklist guardados.
- Comandos: los de la sección «Verificación» del `README.md` raíz (creada en `infra-monorepo`) para build y pruebas de `apps/admin-web`, más `python scripts/validar-planes.py` desde la raíz con 0 errores; cada aviso resuelto o justificado en el PR.

### 6.1 Evidencia real de ejecución (2026-10-20)

Todo se ejecutó contra el Gateway real (`http://localhost:8080`) con `identity-service`,
`hotel-service`, `reception-service`, `discovery-server` y `api-gateway` en marcha. La salida HTTP
completa está en [`apps/admin-web/evidencias/front-admin-web-catalogo/http-evidence.md`](../../apps/admin-web/evidencias/front-admin-web-catalogo/http-evidence.md)
y las capturas en [`apps/admin-web/evidencias/front-admin-web-catalogo/`](../../apps/admin-web/evidencias/front-admin-web-catalogo/).

- **Comandos canónicos** (en `apps/admin-web`): `npm ci` → `added 281 packages`; `npm run build` →
  `Application bundle generation complete`, 0 errores; `npm test -- --watch=false` → **8 archivos,
  22 pruebas, 0 fallos**.
- **Validador de planes** (raíz): `python scripts/validar-planes.py` →
  `28 plan(es) revisado(s); 28 previsto(s) en el catálogo; 0 error(es), 0 aviso(s)`.
- **Validador de contratos** (raíz): `python scripts/validar-contratos.py --base origin/main` →
  `Los contratos solo se han ampliado. Correcto.`
- **Personas**: `GET /api/persona/listar?page=0&size=20` → 200; filtro por documento exacto en UI
  deja una fila; vacío con `Sin filtros` y documento inexistente muestra “No hay personas que
  coincidan”; `POST /api/persona/registrar` → 201; `PUT /api/persona/actualizar/{id}` con ETag
  vigente → 200; con ETag viejo → 412 `PRECONDITION_FAILED`; `DELETE /api/persona/eliminar/{id}` →
  200 con `estado: false`; último ADMIN → 409 `LAST_ADMIN_PROTECTED`; ADMIN sin clave → 409
  `STAFF_CREDENTIAL_REQUIRED`. La validación por campo se comprobó en la UI (documento, nombre,
  apellido, correo y clave muestran su mensaje exacto) y el componente de errores está cubierto por
  prueba unitaria. Capturas: `01-personas-listado.png` y `13-persona-form-errores.png`.
- **Roles**: la pantalla lista los tres códigos y no tiene botones de crear ni eliminar; el código
  no expone métodos de alta/baja. El `PUT /api/tipopersona/actualizar/{id}` real responde **500**
  `INTERNAL_ERROR` (ver §9): el paso 7 queda sin marcar.
- **Sedes, pisos y categorías**: 201 en altas, 200 en ediciones y bajas; `DELETE
  /api/hotel/eliminar/{id}` → 409 `ACTIVE_DEPENDENCIES`; capturas `02-sedes-listado.png`,
  `03-pisos-listado.png`, `04-categorias-listado.png`.
- **Habitaciones**: `GET /api/habitacion/listar?...&idHotel=1&idCategoria=1` → 200 y la UI filtra
  por sede y categoría; alta → 201 con `estadoFisico.codigo = LISTA` y píldora “Lista”; edición →
  200 sin cambiar número ni sede; número duplicado → 409 `DUPLICATE_RESOURCE` con el mensaje de
  campo “Ya existe una habitación activa con ese número”; URL de imagen inválida se resalta en el
  formulario. Capturas `05-habitaciones-listado.png`, `07-habitacion-form.png`,
  `09-habitacion-editada.png`, `10-habitacion-duplicada.png`.
- **Estado físico**: `GET /api/estadohabitacion/listar` → 200; `PUT
  /api/estadohabitacion/actualizar/{id}` → 200; sin `If-Match` → 428 `PRECONDITION_REQUIRED`; con
  versión distinta → 412 `PRECONDITION_FAILED`; el diálogo de etiquetas solo aparece para ADMIN y
  no ofrece crear ni eliminar. Captura `06-estados-dialogo.png`.
- **Errores**: `401` sin token, `403` con CLIENTE, `404` de recurso inexistente, `400`
  `VALIDATION_ERROR`, `409` `DUPLICATE_RESOURCE`, `409` `ACTIVE_DEPENDENCIES`, `409`
  `LAST_ADMIN_PROTECTED`, `500` y `PRECONDITION_REQUIRED`/`PRECONDITION_FAILED` se observaron en
  vivo. La prueba `error-banner.component.spec.ts` recorre los 16 códigos del catálogo y comprueba
  que la franja muestra mensaje, acción, código y `correlationId`; `api-error.spec.ts` comprueba el
  mapeo y el código desconocido sin traducir.
- **Responsive y accesibilidad**: capturas `11-habitaciones-390.png` (viewport 390×844, sin
  overflow horizontal: `document.scrollWidth = 390`) y `12-habitaciones-1024.png` (1024×768, sin
  overflow). El sidebar queda fuera de pantalla en 390/1024 con botón “Menú”; foco visible, `label`
  por campo, `aria-describedby` en el documento bloqueado, `role="alert"` en errores, diálogos con
  `Esc` y foco inicial. `prefers-reduced-motion` desactiva skeletons y spinners.

### 6.2 Checklist de [../DESIGN.md §11](../DESIGN.md) por pantalla

| Pantalla | Rol | Formatos | Vacío/carga/error | 202 | Destructivo | Vocabulario | Teclado/foco | Números/importes | Copy | 390/1024 |
|---|---|---|---|---|---|---|---|---|---|---|
| Personas | A/E lectura, A muta | — | Sí | n/a | “Dar de baja” con diálogo | Sí | Sí | n/a | Sí | Sí |
| Persona alta/edición | A/E (E solo Cliente) | — | Sí | n/a | — | Sí | Sí | n/a | Sí | Sí |
| Roles | A/E lectura, A edita | — | Sí | n/a | — | Sí | Sí | n/a | Sí | Sí |
| Sedes | A/E lectura, A muta | — | Sí | n/a | “Dar de baja” | Sí | Sí | n/a | Sí | Sí |
| Pisos | A/E lectura, A muta | — | Sí | n/a | “Dar de baja” | Sí | Sí | n/a | Sí | Sí |
| Categorías | A/E lectura, A muta | — | Sí | n/a | “Dar de baja” | Sí | Sí | capacidad tabular | Sí | Sí |
| Habitaciones | A/E lectura, A muta | `S/ 70.00` | Sí | n/a | — | “Libre/Ocupada/Sin datos”, nunca “Disponible” | Sí | precio a la derecha | Sí | Sí |
| Estado físico (diálogo) | A | — | Sí | n/a | — | “Editar etiqueta”, sin crear/eliminar | Sí | n/a | Sí | Sí |


## 7. Preguntas abiertas

Ninguna pregunta de especificación. El plan queda **en curso y bloqueado** por defectos de las
dependencias de backend, reproducidos contra el Gateway real y detallados en §9.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación observable.
- [ ] Pruebas de la sección 6 con evidencia de respuestas, capturas y checklist.
- [ ] Sin preguntas abiertas.
- [ ] Sin mensajería, persistencia, alcance cerrado ni cabeceras falsificables; solo Angular 22.2.2 y Gateway `http://localhost:8080` con mocks previos del OpenAPI.
- [ ] Cada pantalla usa tokens de [../DESIGN.md §10](../DESIGN.md) y pasa el checklist de [../DESIGN.md §11](../DESIGN.md), sin colores, tipografías, componentes ni movimientos inventados.
- [ ] `python scripts/validar-planes.py` con 0 errores desde la raíz; avisos resueltos o justificados.
- [ ] Archivo entregado en `docs/plans/front-admin-web-catalogo.md`; rama `front-admin-web-catalogo` desde `main` actualizado, commit `docs: agrega el plan front-admin-web-catalogo`, push y PR a `main` sin auto-merge (revisa una persona).

## 9. Bloqueo de dependencias (no es una pregunta de especificación)

La implementación de `apps/admin-web` está completa y compila, pero el cierre del plan exige
respuestas reales por pantalla que hoy no puede dar el backend de `main`. Los defectos se
reprodujeron contra el Gateway y quedan fuera de la zona de este plan (`apps/admin-web/**`), así
que no se corrigen aquí.

1. **`PUT /api/tipopersona/actualizar/{id}` → 500.** Con `If-Match: "1"` y `{"descripcion":"Cliente"}`
   responde `500 INTERNAL_ERROR`. El log de `identity-service` muestra
   `ERROR: column "updated_at" of relation "tipo_persona" does not exist` en
   `PersonaRepository.actualizarTipoPersona` (la migración `V4__identity_personas.sql` no crea esa
   columna). Impide marcar el paso 7 (exige 200 con versión incrementada).
2. **Personas con rol EMPLEADO → 500.** `GET /api/persona/listar?page=0&size=20` y
   `PUT /api/persona/actualizar/{id}` con el token de `recepcion.lima@hotel.test` responden
   `500 INTERNAL_ERROR` con `ClassCastException: Long cannot be cast to Integer` en
   `PersonaController`. El listado de clientes por documento y la lectura asignada al EMPLEADO no
   son usables contra el servicio real, aunque la UI está implementada.
3. **Sedes del EMPLEADO → lista vacía.** `GET /api/hotel/listar?page=0&size=100` con el token de
   `recepcion.lima@hotel.test` (JWT con `sedes: [1]`) responde 200 con `data: []`. El topbar y los
   listados por sede muestran “Sin sede asignada”; el backend no devuelve el alcance que el JWT
   declara.
4. **`hotel-service` no se registra en Eureka.** El módulo no declara
   `spring-cloud-starter-netflix-eureka-client` ni bloque `eureka` en `application.yml`. Para poder
   probar el Gateway se registró su instancia manualmente contra Eureka
   (`POST /eureka/apps/HOTEL-SERVICE` y heartbeat del entorno); en un arranque limpio
   `GET /api/hotel/listar` por el Gateway responde 503 `DEPENDENCY_UNAVAILABLE`.
5. **`PUT /api/estadohabitacion/actualizar/{id}` ignora el body.** Sin query param responde 500
   “Required request parameter 'descripcion' is not present”; con `?descripcion=...` y body
   responde 200. El frontend envía ambos para cumplir el contrato y hablar con el servicio real
   (ver `IA-front-admin-web-catalogo-01.md`).
6. **Falta `If-Match` en persona → 500 en vez de 428.** `PUT /api/persona/actualizar/{id}` sin
   `If-Match` responde 500 `INTERNAL_ERROR`; el contrato y el catálogo exigen 428
   `PRECONDITION_REQUIRED`. La UI siempre obtiene el ETag con `buscar/{id}` antes de editar, por lo
   que no lo provoca en el flujo normal, pero la respuesta genérica no es la del contrato.
7. **Metadatos de paginación ausentes en hotel.** Los listados de hotel/piso/categoría/habitación
   devuelven `meta` solo con `correlationId`; el frontend usa una página con el total recibido
   (ver `IA-front-admin-web-catalogo-01.md`).

Evidencia reproducible en
[`apps/admin-web/evidencias/front-admin-web-catalogo/http-evidence.md`](../../apps/admin-web/evidencias/front-admin-web-catalogo/http-evidence.md).
El bloqueo real es el punto 1: sin un `PUT` de rol que responda 200 no se puede cerrar el paso 7 ni
el criterio de terminado. Los puntos 2–7 deberían resolverse en un plan de arreglo de backend antes
de dar por cerrada la lectura de EMPLEADO y la integración limpia por Eureka.
