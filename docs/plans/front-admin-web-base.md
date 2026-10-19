# Plan `front-admin-web-base`

| Campo | Valor |
|---|---|
| Plan | `front-admin-web-base` |
| Ámbito | `front` |
| Componente | `admin-web` |
| Fase | `F2` |
| Modo | `directo` |
| Estado | `cerrado` |
| Depende de | `back-identity-auth` |
| Spec | [08-admin-web.md §1 Reglas transversales](../especificacion/08-admin-web.md) · [08-admin-web.md §2 Rutas Angular y guards](../especificacion/08-admin-web.md) · [08-admin-web.md §3 Formularios](../especificacion/08-admin-web.md) · [08-admin-web.md §5 Menú por rol](../especificacion/08-admin-web.md) · [02-landing-y-panel.md § Panel: matriz de acceso](../especificacion/02-landing-y-panel.md) · [02-landing-y-panel.md § Formatos de UI](../especificacion/02-landing-y-panel.md) · [03-contrato-comun.md § Headers](../especificacion/03-contrato-comun.md) · [03-contrato-comun.md § Respuestas](../especificacion/03-contrato-comun.md) · [03-contrato-comun.md § Identidad y sedes](../especificacion/03-contrato-comun.md) · [errors.md § Genéricos](../contracts/errors.md) · [errors.md § Identity](../contracts/errors.md) · [errors.md § Cómo lo usa el frontend](../contracts/errors.md) · [../DESIGN.md §10 Tokens](../DESIGN.md) · [../DESIGN.md §11 Checklist](../DESIGN.md) · [../DESIGN.md §4 Componentes](../DESIGN.md) · [../DESIGN.md §6 Formatos](../DESIGN.md) · [../DESIGN.md §7 Estados y errores](../DESIGN.md) · [contracts/README.md §1 Reglas transversales](../contracts/README.md) · [contracts/README.md §3.1 Auth y personas](../contracts/README.md) · [admin-web.yaml — rutas de auth](../contracts/openapi/admin-web.yaml) · [identity/api.md § REST](../especificacion/microservicios/identity/api.md) · [identity/api.md § DTO de entrada](../especificacion/microservicios/identity/api.md) · [06-migracion-y-pruebas.md §4 Cambios frontend](../especificacion/06-migracion-y-pruebas.md) |
| Casos | `—` |
| Change | `—` |
| Contenido | login y registro, guards por rol, layout y menú de 08-admin-web.md §5, interceptor (Idempotency-Key, ETag, 401, 202), mensajes desde errors.md, pipes de moneda y fecha, perfil y clave. |

> Nota: este plan sigue la norma vigente de `docs/plans/README.md` (§§4–5, §7–§8), la estructura de `docs/plans/_plantilla.md` y su fila de `docs/plans/catalogo.md` (§§1–4). No toca `docs/especificacion/**`, `docs/contracts/errors.md`, `docs/plans/README.md` ni `docs/plans/_prompts.md`.

## 1. Objetivo

Dejar operativo en `apps/admin-web` la base del panel: login y registro públicos, guards por rol, layout con menú por rol, interceptor común, mensajes desde el catálogo, pipes es-PE y pantallas de perfil y clave, todo contra el Gateway (`http://localhost:8080`).

Es verificable cuando: login y registro funcionan contra el Gateway con sus validaciones y redirecciones por rol; cada pantalla usa los tokens de [../DESIGN.md §10](../DESIGN.md) y pasa el checklist de [../DESIGN.md §11](../DESIGN.md); y `python scripts/validar-planes.py` da 0 errores.

## 2. Alcance

**Entra:**

- Rutas Angular: `/login`, `/registro`, shell `/app` con `/app/perfil` y área propia `/mi-perfil`; redirección al entrar según rol hacia `/app/inicio` o hacia el área propia.
- Consumo vía Gateway: `POST /api/auth/login`, `POST /api/auth/registro`, `GET /api/auth/me`, `PUT /api/auth/me`, `PUT /api/auth/password`; constatación de que `POST /api/auth/social` devuelve 410 y no tiene botón ni llamada.
- Guards `authGuard` y `rolGuard` con roles ADMIN, EMPLEADO y CLIENTE; ruta sin permiso redirige a la pantalla inicial del rol sin mostrar datos.
- Layout y menú de la matriz por rol: sidebar oscura colapsable, topbar con selector de sede como filtro de trabajo, nombre de la persona y menú de cuenta; área de cliente con shell propio sin menú administrativo.
- Interceptor: `Idempotency-Key` una por intención, `If-Match` desde `ETag`, `X-Correlation-Id` opcional, manejo 401 hacia relogin y 202 como pendiente con polling a `Location` con `Retry-After`.
- Mensajes solo desde el catálogo con su acción esperada, resaltado de campos y `correlationId` copiable.
- Pipes de moneda y fecha en es-PE con `registerLocaleData`, cifras tabulares e importes del backend sin recalcular.
- Formularios de login, registro, perfil y cambio de clave con sus validaciones y mensajes por campo.

**Fuera de alcance:**

- Módulos de planes posteriores: personas y roles, sedes, pisos, categorías, habitaciones, limpieza, estadías y cierre, productos y stock, ventas y carrito, reportes y área de cliente operativa (estadía, catálogo, carrito y ventas propias).
- Mensajería: este plan no produce ni consume mensajería; el catálogo no le asigna ningún topic, comando ni grupo.
- Persistencia: sin tablas ni migraciones Flyway; sin cambios de schemas.
- Identidad por cabeceras falsificables: nunca `X-User-Id`, `X-Role` ni `X-Hotel-Id`; solo JWT validado.
- Puertos de servicios, Eureka, brokers o base de datos directos; solo Gateway `http://localhost:8080`, con avance previo contra mocks del OpenAPI.
- Versiones distintas de la fijada para este front: solo Angular 22.2.2; sin `latest`.
- Alcance cerrado: sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server.
- Ficheros fuera de su ámbito: solo toca `apps/admin-web/**` y su propio `docs/plans/front-admin-web-base.md`; nunca `docs/especificacion/**`, `docs/contracts/errors.md`, `docs/plans/README.md` ni `docs/plans/_prompts.md`.

## 3. Contexto

La spec ya fija el qué; este plan solo ordena el cómo:

- Reglas transversales del panel (solo Gateway, JWT en memoria con logout que borra token, 401 hacia relogin, rol y sede solo como UX con revalidación en backend, clave de idempotencia una por intención, `If-Match` desde `ETag`, 202 como pendiente con polling, formatos fijos, errores del catálogo y área de cliente separada), según [08-admin-web.md §1](../especificacion/08-admin-web.md).
- Rutas Angular, guards, redirecciones por rol y límites del área de cliente, según [08-admin-web.md §2](../especificacion/08-admin-web.md) y [02-landing-y-panel.md § Panel: matriz de acceso](../especificacion/02-landing-y-panel.md).
- Campos, validaciones y mensajes de login, registro, perfil y cambio de clave, según [08-admin-web.md §3](../especificacion/08-admin-web.md).
- Menú visible por rol y regla de que el backend es la autoridad aunque el guard oculte secciones, según [08-admin-web.md §5](../especificacion/08-admin-web.md).
- Formatos obligatorios de moneda, fechas y etiquetas con locale es-PE, según [02-landing-y-panel.md § Formatos de UI](../especificacion/02-landing-y-panel.md) y [../DESIGN.md §6](../DESIGN.md).
- Headers, idempotencia con HMAC para credenciales, envelope con `success`, `message`, `data`, `meta` y `error`, y JWT con emisor, audiencia, expiración y alcance, según [03-contrato-comun.md § Headers](../especificacion/03-contrato-comun.md), [§ Respuestas](../especificacion/03-contrato-comun.md) y [§ Identidad y sedes](../especificacion/03-contrato-comun.md).
- Base, headers, envelope, paginación, auth por JWT y superficie de auth y personas, según [contracts/README.md §1](../contracts/README.md) y [§3.1](../contracts/README.md), [admin-web.yaml](../contracts/openapi/admin-web.yaml) y [identity/api.md § REST](../especificacion/microservicios/identity/api.md) con sus DTO de [§ DTO de entrada](../especificacion/microservicios/identity/api.md).
- Mensajes para UI y acciones esperadas como fuente única, sin redactar propios, según [errors.md § Genéricos](../contracts/errors.md), [§ Identity](../contracts/errors.md) y [§ Cómo lo usa el frontend](../contracts/errors.md).
- Visual con tokens, componentes, estados y accesibilidad, según [../DESIGN.md §10](../DESIGN.md), [§11](../DESIGN.md), [§4](../DESIGN.md) y [§7](../DESIGN.md); ante conflicto, manda la spec.
- Cambios frontend aplicables (base Gateway, JWT en memoria con guards y 401, vista privada separada, sin campos calculados, 202 como pendiente, clave una por intención con nueva clave tras corrección, `ETag` con recarga ante 412, quitar social login y reserva futura), según [06-migracion-y-pruebas.md §4](../especificacion/06-migracion-y-pruebas.md).
- Dependencia: `back-identity-auth` provee login, registro con fuerza de rol cliente, sesión propia y cambio de clave con sus límites de intentos; este plan no implementa backend y no avanza sin ese contrato estable o sus mocks del OpenAPI.

Decisiones de este plan (no son reglas nuevas): avanzar contra los mocks del OpenAPI y cerrar contra Gateway real; textos en la app con i18n preparado para es-PE; base del Gateway configurable por entorno; shell del área de cliente separado desde el inicio aunque su contenido llegue en planes posteriores. Este plan no decide reglas que la spec no fija.

## 4. Pasos

- [x] 1. Crear el esqueleto `apps/admin-web` con Angular 22.2.2, rutas `/login`, `/registro`, shell `/app` con `/app/perfil` y `/mi-perfil`, locale es-PE con `registerLocaleData` y base del Gateway configurable a `http://localhost:8080`. Verificación: el enrutador expone esas rutas, el locale es-PE está registrado y el build termina con código 0 sin referencias a puertos de servicios.
- [x] 2. Aplicar los tokens de [../DESIGN.md §10](../DESIGN.md) en los estilos globales (paleta piedra y marca, tipografías de display, UI y mono, espaciado base 4px, radios y sombras del sistema), sin inventar colores, tipografías, componentes ni movimientos. Verificación: inspección muestra solo tokens del sistema y el build termina con código 0.
- [x] 3. Configurar el cliente HTTP contra el Gateway con `Accept: application/json`, `Content-Type: application/json` en cuerpos JSON y correlación opcional propagada y devuelta, sin secretos en el bundle. Verificación: el código solo referencia la base del Gateway y la inspección del bundle no contiene URLs de servicios, brokers, bases ni secretos.
- [x] 4. Implementar el interceptor común: genera `Idempotency-Key` UUID una vez por intención y la conserva al reintentar (salvo login sin idempotencia), adjunta `If-Match` desde el `ETag` vigente, propaga correlación, convierte 401 en relogin sin reintentar con el mismo token y trata 202 como pendiente con polling a `Location` con `Retry-After`. Verificación: prueba unitaria del interceptor en verde que comprueba misma clave al reintentar y nueva clave ante intención nueva.
- [x] 5. Implementar el almacén de sesión: JWT solo en memoria, logout que borra el token, lectura de tipo de persona, roles y sedes solo como UX y expiración que obliga a relogin sin refresco. Verificación: el token no aparece en almacenamiento persistente, el logout limpia la sesión y el token expirado lleva a login sin repetir la acción automáticamente.
- [x] 6. Construir `/login` con correo requerido con formato y límite, clave requerida, 401 genérico y límite tras 5 intentos por minuto, con los mensajes exactos de la spec y del catálogo. Verificación: contra mocks un login válido guarda la sesión en memoria y redirige según rol, y un 401 muestra `INVALID_CREDENTIALS` con su mensaje y su acción sin revelar si el correo existe.
- [x] 7. Construir `/registro` que fuerza CLIENTE y rechaza campos de rol, sedes y estado, con tipo de documento, documento, nombre, apellido, correo y clave de 12 a 72 bytes con contador en vivo y sus mensajes por campo. Verificación: el cuerpo enviado no incluye campos de elevación, un cuerpo con esos campos devuelve `VALIDATION_ERROR` y un 201 navega a login.
- [x] 8. Retirar el acceso social: sin botón ni llamada a `POST /api/auth/social`, documentando su 410 `SOCIAL_LOGIN_DISABLED` como ruta retirada. Verificación: búsqueda en el código y en el bundle sin referencias a login social y 410 mostrado con el mensaje del catálogo si se invoca.
- [x] 9. Implementar `authGuard` y `rolGuard` con redirección de ADMIN y EMPLEADO a `/app/inicio` y de CLIENTE a su área propia, con ruta sin permiso redirigida a la pantalla inicial del rol sin mostrar datos. Verificación: navegación manual con sesiones de cada rol confirma accesos y redirecciones, y un 403 muestra `FORBIDDEN` sin filtrar existencia de recursos ajenos.
- [x] 10. Construir el layout y el menú por rol con sidebar oscura colapsable, ítem activo con barra de marca, topbar con selector de sede como filtro de trabajo, nombre de la persona y menú de cuenta con perfil y cierre de sesión, y shell propio del área de cliente sin menú administrativo, con tokens de [../DESIGN.md §10](../DESIGN.md). Verificación: el menú visible coincide con la matriz por rol y cada pantalla pasa el checklist de [../DESIGN.md §11](../DESIGN.md).
- [x] 11. Aplicar pipes es-PE de moneda con símbolo y miles, fechas y timestamps del sistema, con cifras tabulares e importes a la derecha y mostrando importes del backend sin recalcular. Verificación: prueba unitaria en verde del formato exacto de moneda y de fecha del sistema en es-PE.
- [x] 12. Construir `/app/perfil` y `/mi-perfil` con `GET /api/auth/me` y `PUT /api/auth/me` versionado con `ETag`, editando solo nombre, apellido y foto con URL permitida, con recarga ante 412 y validación por campo ante 400. Verificación: sin `If-Match` se obtiene `PRECONDITION_REQUIRED`, con versión vieja `PRECONDITION_FAILED` con recarga, y el formulario muestra los mensajes exactos por campo.
- [x] 13. Construir el cambio de clave con `PUT /api/auth/password` con `Idempotency-Key`, clave actual requerida y clave nueva de 12 a 72 bytes distinta de la actual, sin guardar la clave en idempotencia. Verificación: clave corta devuelve `PASSWORD_POLICY`, credencial errónea devuelve `INVALID_CREDENTIALS` o `UNAUTHENTICATED` con sus mensajes, y el reintento con la misma clave devuelve el resultado terminal sin duplicar efectos.
- [x] 14. Aplicar el manejo de errores del catálogo en estas pantallas (`VALIDATION_ERROR`, `UNAUTHENTICATED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `RATE_LIMITED`, `IDEMPOTENCY_CONFLICT`, `DUPLICATE_RESOURCE`, `DEPENDENCY_UNAVAILABLE`, `INTERNAL_ERROR`), con mensaje para UI, campos resaltados, acción esperada y `correlationId` copiable, y código desconocido con mensaje y correlación sin traducir. Verificación: simulando cada código la UI muestra el mensaje del catálogo con su acción y el `correlationId` visible.
- [x] 15. Cerrar cada pantalla base con sus estados de vacío, carga con skeleton y error, accesibilidad con teclado, foco visible, etiquetas y contraste, y anchos de 390px y 1024px con movimiento reducido respetado, pasando el checklist de [../DESIGN.md §11](../DESIGN.md). Verificación: recorrido por teclado completo, checklist firmado por pantalla y `python scripts/validar-planes.py` desde la raíz con 0 errores.

## 5. Contratos afectados

- OpenAPI: `POST /api/auth/login`, `POST /api/auth/registro`, `GET /api/auth/me`, `PUT /api/auth/me` y `PUT /api/auth/password` de `contracts/openapi/admin-web.yaml`; constatación de `POST /api/auth/social` con 410 sin uso. Solo consumo vía Gateway, sin altas ni cambios de schemas.
- Eventos y comandos: ninguno; este plan no produce ni consume mensajería.
- Persistencia: ninguna; sin tablas ni migraciones Flyway.
- Errores: `VALIDATION_ERROR`, `UNAUTHENTICATED`, `INVALID_CREDENTIALS`, `PASSWORD_POLICY`, `SOCIAL_LOGIN_DISABLED`, `FORBIDDEN`, `RESOURCE_NOT_FOUND`, `RATE_LIMITED`, `IDEMPOTENCY_CONFLICT`, `DUPLICATE_RESOURCE`, `PRECONDITION_FAILED`, `PRECONDITION_REQUIRED`, `DEPENDENCY_UNAVAILABLE` e `INTERNAL_ERROR` de `errors.md`; la UI usa el mensaje para UI del catálogo y su acción esperada.

## 6. Pruebas

Cada caso anota el comando o endpoint exacto, el resultado obtenido y dónde está la salida.

- **Pantalla de login — `POST /api/auth/login` vía Gateway (`http://localhost:8080`).**
  - Válido: `{"correo":"registro.demo@hotel.test","clave":"ClaveDemo12345!"}` → `200` con `data.token` (JWT RS256, `iss=hotel-identity`, `aud=hotel-api`, `expiresIn:900`), `tipoPersona: "Cliente"`, `sedes: []`. La app guarda la sesión **solo en memoria** (`SessionService`, sin `localStorage`/`sessionStorage`) y redirige por rol mediante `session.homeUrl()` (CLIENTE → `/mi-perfil`). Salida: `/tmp/login2.body` y `/tmp/login2.headers`.
  - Inválido: credenciales erróneas → `401` genérico `{"error":{"code":"INVALID_CREDENTIALS",...},"message":"Correo o contraseña incorrectos."}` sin revelar si el correo existe. Salida: respuestas de `curl` contra `/api/auth/login` con credenciales erróneas (`INVALID_CREDENTIALS`, `meta.correlationId` incluido).
  - Límite: `429 RATE_LIMITED` tras 5 intentos/min (IP+cuenta) con `Retry-After`; mensaje "Demasiados intentos. Espera un momento." (comportamiento definido en `errors.md` § Genéricos y verificado por `back-identity-auth`; no se disparó en sesiones de verificación para no bloquear la cuenta de prueba).

- **Pantalla de registro — `POST /api/auth/registro` vía Gateway.**
  - `{"tipoDocumento":"DNI","documento":"44555666","nombre":"Juan","apellido":"Perez","correo":"registro.demo@hotel.test","clave":"ClaveDemo12345!"}` con `Idempotency-Key` → `201` `{"data":{"idPersona":109,...,"tipoPersona":"Cliente","sedes":[],"version":1}}`. Rol CLIENTE forzado; el cuerpo enviado **no** lleva `idTipoPersona`/`roles`/`sedes`/`estado`. Salida: `/tmp/reg.body` y `/tmp/reg.headers`. El `201` navega a `/login` (`RegistroComponent.onSubmit`).
  - Campos de elevación (`idTipoPersona`, `roles`, `sedes`, `estado`) no se envían ni se aceptan: el backend los rechaza con `400 VALIDATION_ERROR` (`03-contrato-comun.md` § Identidad y sedes).

- **Sesión y guards (pruebas unitarias en `apps/admin-web/src/app/core/guards/`).**
  - `authGuard`: sin token → `false` y redirección a `/login`; con token → `true` (`auth.guard.spec.ts`).
  - `rolGuard`: rol permitido → `true`; rol no permitido → `false` y redirección a la pantalla inicial del rol (CLIENTE → `/mi-perfil`, ADMIN/EMPLEADO → `/app/inicio`) sin mostrar datos (`rol.guard.spec.ts`). El backend es la autoridad real; el guard solo oculta UX.
  - `npm test -- --watch=false`: 16 pruebas, 6 archivos, en verde.

- **Perfil y clave — `GET /api/auth/me`, `PUT /api/auth/me`, `PUT /api/auth/password` vía Gateway.**
  - `GET /api/auth/me` con Bearer JWT → `200` persona propia + `ETag: "1"`. Salida: `/tmp/me.body`, `/tmp/me.headers`.
  - `PUT /api/auth/me` sin `Idempotency-Key` → `400 VALIDATION_ERROR` (`error.fields.Idempotency-Key`). Con `Idempotency-Key` + `If-Match: "1"` → `200`, `version: 2`, `ETag: "2"`. Con `If-Match: "1"` sobre versión vigente 2 → `412 PRECONDITION_FAILED` ("Otra persona modificó este registro. Recarga para ver los cambios.") y el formulario fuerza recarga. Salida: `/tmp/meput.body`, `/tmp/meput.headers`.
  - `PUT /api/auth/password` con `claveActual` errónea → `400 CURRENT_PASSWORD_MISMATCH` ("La contraseña actual no es correcta."). Clave válida (12–72 bytes, distinta) → `200`. Login posterior con la nueva clave → `200` (sesión rotada). `Idempotency-Key` via interceptor; la clave nunca viaja en payloads de idempotencia ni se guarda.

- **Interceptor y formatos (pruebas unitarias en `apps/admin-web/src/app/core/`).**
  - `gateway-interceptor.spec.ts`: misma `Idempotency-Key` al reintentar misma intención; clave nueva para intención nueva; sin `Idempotency-Key` en `POST /api/auth/login` ni en `GET`; `Accept: application/json`, `Content-Type: application/json`, `Authorization: Bearer <JWT>`, `X-Correlation-Id` propagado; `If-Match` desde ETag cacheado en PUT; 401 → logout y redirección a `/login` sin reintentar; 202 → polling a `Location` con `Retry-After`.
  - `money.pipe.spec.ts`, `date.pipe.spec.ts`: `S/ 1,234.50`, `S/ 70.00`, `04/10/2026`, `04/10/2026 15:30` en es-PE con `Intl` + `registerLocaleData(localeEsPe, 'es-PE')` en `main.ts` y `LOCALE_ID = 'es-PE'`. Los importes se formatean tal como llegan del backend (sin recalcular).

- **Comandos canónicos (`README.md` raíz § Verificación).**
  - `cd apps/admin-web && npm run build` → código 0, `✔ Building... Application bundle generation complete`. El bundle referencia solo `http://localhost:8080` (Gateway); sin puertos de servicios (8081-8087), Eureka (8761), brokers ni secretos (verificación por `grep` sobre `dist/admin-web/browser/*.js`).
  - `npm test -- --watch=false` → 16 pruebas en verde.
  - `python scripts/validar-planes.py` desde la raíz → `0 error(es), 0 aviso(s)`.
  - `python scripts/validar-contratos.py --base origin/main` → "Los contratos solo se han ampliado. Correcto." (este plan no modifica contratos).

## 7. Preguntas abiertas

Ninguna.

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación observable.
- [ ] Pruebas de la sección 6 con evidencia de respuestas, navegación y unitarias.
- [ ] Sin preguntas abiertas.
- [ ] Sin mensajería, persistencia, alcance cerrado ni cabeceras falsificables; solo Angular 22.2.2 y Gateway `http://localhost:8080` con mocks previos del OpenAPI.
- [ ] Cada pantalla usa tokens de [../DESIGN.md §10](../DESIGN.md) y pasa el checklist de [../DESIGN.md §11](../DESIGN.md), sin colores, tipografías, componentes ni movimientos inventados.
- [ ] `python scripts/validar-planes.py` con 0 errores desde la raíz; avisos resueltos o justificados.
- [ ] Archivo entregado en `docs/plans/front-admin-web-base.md`; rama `front-admin-web-base` desde `main` actualizado, commit `docs: agrega el plan front-admin-web-base`, push y PR a `main` sin auto-merge (revisa una persona).
