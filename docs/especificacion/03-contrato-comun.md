# Contrato común obligatorio

## Transporte, notación y tipos

Base externa local `http://localhost:8080`; en despliegue HTTPS. `A`=ADMIN, `E`=EMPLEADO, `C`=CLIENTE, `P`=sin login, `S(nombre)`=identidad de servicio autorizada. Todas las rutas son deny-by-default salvo una fila pública explícita.

IDs comerciales: entero positivo de 32 bits, compatible con Integer actual; IDs de operación, evento, comando, cierre y bloqueo: UUID. Strings recortados, correo lowercase. Campos comunes `version` bigint >0; contadores de proyección/roomVersion admiten 0 antes del primer evento, pero ningún evento tiene versión 0; `estado` boolean en maestros es activo/inactivo, no ocupación.

`L` en tablas de rutas = query `page=0,size=20` (máximo 100); devuelve data[] y meta de paginación. Listas de maestros admiten además estado:boolean opcional (default true); los estados de estadía/venta solo se filtran cuando su ruta lo define. Orden estable por ID ascendente; reportes por clave de agrupación y cobros por fechaCierre, idRecepcion. No se acepta SQL/sort arbitrario del cliente. `H` = `idHotel` requerido en listados por sede; para recursos por ID se deriva de BD y se valida. Excepción: producto por sede usa clave compuesta (idProducto,idHotel), por lo que siempre requiere idHotel en la ubicación indicada por la ruta. `V` = header `If-Match: "<version>"` obligatorio en PUT/DELETE de recursos con version; sin él 428, versión distinta 412. Rutas internas idempotentes no requieren V.

## Límites de campos comunes

| Campo | Límite | Dónde aplica |
|---|---|---|
| `correo` | ≤ 254 | identity (correo), hotel (correoContacto), notification (destinatario) |
| `telefono` | ≤ 20 | hotel (HotelInput.telefono) |
| `descripcion` | ≤ 300 | hotel (sede, piso), inventory (motivo de ajuste) |
| `detalle` | ≤ 500 | hotel (categoría, habitación), inventory (producto), reception (observacion), cierre (motivoPenalidad) |
| URL (`fotoUrl`, `imagenUrl`, `imagenAlt`, `urlsImagenes[].url`) | ≤ 500 la URL, ≤ 150 el alt | alineado con `foto_url` de identity; https allowlist o `/assets/`. Toda URL de imagen viaja con su `alt`: una sin descripción se rechaza con 400 |
| `clave` | 12–72 bytes UTF-8 | identity; nunca truncar silenciosamente (BCrypt no acepta más) |

Los strings se recortan (trim) y se validan en backend; exceder un límite devuelve 400 `VALIDATION_ERROR` con el campo en `error.fields`. Las columnas varchar de las tablas de cada servicio respetan estos máximos. Otros límites (nombre, SKU, motivo) ya están declarados en su propia tabla o DTO.

## Headers

| Header | Uso |
|---|---|
| Accept: application/json | Todas las APIs salvo JWKS |
| Content-Type: application/json | Requests con JSON |
| Authorization: Bearer <token> | Toda ruta A/E/C/S; nunca en query |
| X-Correlation-Id | UUID opcional cliente; Gateway genera si falta y propaga/responde |
| Idempotency-Key | UUID obligatorio en POST/PUT/DELETE de negocio, salvo login/token/social; mismo valor al reintentar |
| If-Match | V cuando indicado; ETag se devuelve en lecturas/mutaciones del recurso |
| Retry-After | En 429/503 y 202 con operación aún no terminada |

No se confía en `X-User-Id`, `X-Role`, `X-Hotel-Id` ni un idPersona del body para autorizar. Gateway elimina headers de identidad falsificables.

Idempotencia: 202 es provisional; al terminar se persiste la respuesta terminal para siguientes reintentos. El request_hash usa HMAC si el request contiene credenciales; nunca se guarda clave/token en payload de idempotencia.

Idempotencia: clave única `(actor, método, plantillaRuta, key)`, hash del path+query+body canónico+If-Match original y respuesta persistidos. Misma clave y distinto request → 409 IDEMPOTENCY_CONFLICT. Misma clave en curso → 202 + referencia del recurso/operación; completada → mismo status/body, no repite efectos. Retención mínima 30 días; claves de venta/stock/cierre se conservan con el historial. Error validado antes de comenzar no deja operación inconclusa. DELETE repetido con la misma clave devuelve el resultado original aunque el recurso cambie de estado. Orden: autenticar y verificar acceso actual; consultar idempotencia; si existe resultado compatible devolverlo; solo para una intención nueva evaluar If-Match. Así un retry válido no falla por la versión que su primer intento ya incrementó. Los rechazos de negocio después de crear una operación durable son resultados terminales persistidos.

## Respuestas

Mantener `success/message/data` del proyecto; agregar meta. Los nuevos contratos homogenizan carrito, antes texto o array sin envelope; Angular debe adaptarse.

```json
{"success":true,"message":"OK","data":{"idHabitacion":101,"version":1},"meta":{"correlationId":"11111111-1111-4111-8111-111111111111"}}
```

Listas: data array, meta `page,size,totalElements`. Internas: mismo envelope excepto token/JWKS. 204 no tiene cuerpo. Mutaciones CRUD: 201 creación, 200 actualización/baja; excepciones indicadas en cada ruta. Un 202 asíncrono no significa “venta confirmada” ni “salida completada”.

```json
{"success":false,"message":"La habitación no admite check-in","data":null,"error":{"code":"ROOM_NOT_READY","fields":{}},"meta":{"correlationId":"11111111-1111-4111-8111-111111111111"}}
```

400 validación/formato; 401 no autenticado/expirado; 403 rol no autorizado; 404 inexistente o recurso ajeno a sede/propietario (evitar enumeración); 409 regla de negocio/duplicado; 410 ruta retirada; 412 versión; 428 precondición ausente; 429 límite; 503 dependencia no disponible. No stacktrace ni SQL en respuesta.

El catálogo único de códigos (`error.code`), HTTP, servicio, campo afectado, mensaje para UI y acción esperada vive en [`errors.md`](../contracts/errors.md); esta sección solo fija la forma del envelope y los status genéricos. El frontend usa ese catálogo como fuente de mensajes y no los redacta por su cuenta.

## Identidad y sedes

JWT RS256 emitido por identity. Persona claim `tipoPersona` = Administrador/Empleado/Cliente, `roles` = array de un código, por ejemplo ["ADMIN"] o ["CLIENTE"], `sub`=idPersona string, `sedes` array de ids, `iss=hotel-identity`, `aud=hotel-api`, `iat,exp,jti`. Un mapper usa el código inmutable del rol para generar tipoPersona/roles, nunca su etiqueta editable; los servicios autorizan con roles. TTL 15min; sin refresh en MVP, re-login. Token solo en memoria del frontend; logout borra token. Deshabilitación/cambio de rol puede tardar hasta 15min: limitación aceptada; claves deben poder rotarse.

ADMIN tiene alcance global; EMPLEADO solo `sedes` asignadas; CLIENTE solo recursos cuyo idCliente coincide con sub. No confiar en filtro frontend. Si servicio necesita saber propiedad de una estadía consulta a reception por Feign. Ante dependencia caída: 503, nunca permitir por defecto.

Empleados pueden crear clientes, no otros empleados. Lectura de datos personales limitada y auditada; búsquedas exactas/paginadas, no exportación masiva para empleados. Se auditan en `audit_log` de identity las consultas humanas de PII ajena: action `PERSONA_SEARCH` en `GET /api/persona/listar` (A/E) y action `PERSONA_READ` en `GET /api/persona/buscar/{id}` (A/E), con actor, resource_id, id_hotel y correlation_id; el `changes` de esas filas no incluye documento ni correo. Quedan excluidas la lectura propia (`GET /api/auth/me`, C) y la lectura máquina `GET /internal/clientes/{id}` (ya cubierta por scope y log de acceso).

Login público y registro público con rate limit y validación; contraseñas BCrypt, input write-only, jamás en respuesta/eventos/logs. Registro rechaza campos `idTipoPersona,roles,sedes,estado`: no permite elevación.

## Servicio a servicio

Feign resuelve nombres en Eureka. No entra por Gateway. Cada servicio obtiene JWT máquina en `POST /internal/auth/token` usando Basic clientId:secret por TLS (loopback dev permitido), form `grant_type=client_credentials&audience=<destino>`. Identity verifica allowlist de audiencia y emite TTL 5min, `sub`=nombre servicio, `aud`=destino, `scope`=string de permisos separados por espacios, limitado por audiencia; no roles humanos. Validar issuer fijo y jwk-set-uri explícito: este MVP no necesita descubrimiento OIDC. Credenciales de servicio y JWKS se resuelven desde configuración; evitar dependencia circular durante el arranque. Un token de usuario no basta para /internal/**.

Caller valida usuario/sede primero; payload interno incluye actorId para auditoría, nunca para otorgar permisos al servicio. Consumers usan credenciales propias. Secretos y clave privada fuera de Git. JWT validado por Gateway y cada servicio, con algoritmo/issuer/audience/exp/scope explícitos. JWKS público sin privada. Internal solo en red privada; servicios tampoco exponen CRUD público directamente.

Scopes:

- reception → identity `clientes:read`; hotel `habitaciones:read`; sales `cierres:write`.
- sales → reception `recepciones:read operaciones:write`; inventory `stock:read stock:debit`.
- hotel → reception `bloqueos:write`.
- identity e inventory → hotel `hoteles:read`.
- reporting/notification → ninguna API de negocio en operación normal.

Feign connect timeout 2s, read timeout 5s como valores iniciales (10s en lecturas de reportes); circuit breaker con ventana deslizante de 10 llamadas, umbral de fallo 50%, mínimo 5 llamadas para evaluar, apertura 10s y 3 llamadas de prueba en half-open. No retry ciego POST; reintentar solo con misma clave/id de negocio. No mantener transacción SQL abierta durante llamada remota.

## Persistencia transversal

En tablas de datos los campos sin ? son NOT NULL; versiones inician en 1, fechas de creación se fijan en servidor y timestamps de finalización son null hasta ocurrir. Las firmas con tipos/proyecciones describen el contrato a implementar, no un DDL ejecutado.

Cada DB usa usuario runtime propio sin acceso a otras bases; usuario migrador separado. Dinero numeric(12,2); timestamp timestamptz; índices por idHotel y fechas. FK solo local. Baja lógica `estado=false` en maestros; conservar snapshots y auditoría.

Dinero: redondeo HALF_UP a 2 decimales por componente con BigDecimal (nunca double); el importe declarado por el usuario se compara de forma exacta contra el cálculo del servidor: cualquier diferencia, aunque sea 0.01, devuelve 409 `AMOUNT_MISMATCH` con nueva cotización. El frontend muestra los importes del backend y no recalcula.

Tablas técnicas según necesidad, definidas una vez:

- `api_idempotency(actor varchar(100),method varchar(8),route varchar(160),key uuid,request_hash char(64),status varchar(16),resource_id varchar(80)?,http_status int?,response jsonb?,created_at timestamptz)`; PK actor+method+route+key.
- `outbox(id uuid PK,destination varchar(8),channel varchar(160),partition_key varchar(100)?,aggregate_id varchar(80),aggregate_version bigint,type varchar(80),payload jsonb,created_at timestamptz,published_at timestamptz?,attempts int default 0,next_attempt_at timestamptz)`. Una fila por destino; no marcar dos brokers con un mismo flag.
- `inbox(consumer varchar(80),message_id uuid,processed_at timestamptz)` PK consumer+message_id, persistida en la misma transacción que el efecto.
- `audit_log(id uuid PK,actor varchar(100),action varchar(80),resource_id varchar(80),id_hotel int?,correlation_id uuid,occurred_at timestamptz,changes jsonb)`; excluir claves/tokens, minimizar PII.

Outbox/inbox — valores por defecto sobrescribibles por entorno (ver [configuracion.md](infraestructura/configuracion.md)):

- Poller del outbox cada 5s, lote de 50 filas; reintento con backoff exponencial por `next_attempt_at` (1s, 2s, 4s… tope 5min). Alerta si una fila lleva > 15min pendiente o `attempts` > 10.
- Orden por agregado: no publicar la versión N+1 si hay una N pendiente en el mismo destino; el consumidor aplica solo versiones mayores del mismo agregado (skip-if-older), además de inbox.
- Inbox con retención de 30 días, alineada con la idempotencia; la purga es operativa y nunca borra pendientes.

DDL de referencia de las tablas técnicas (fuente única; los scripts Flyway se derivan de aquí):

```sql
CREATE TABLE api_idempotency (
  actor        varchar(100) NOT NULL,
  method       varchar(8)   NOT NULL,
  route        varchar(160) NOT NULL,
  key          uuid         NOT NULL,
  request_hash char(64)     NOT NULL,
  status       varchar(16)  NOT NULL CHECK (status IN ('EN_CURSO','COMPLETADA')),
  resource_id  varchar(80),
  http_status  int,
  response     jsonb,
  created_at   timestamptz  NOT NULL DEFAULT now(),
  PRIMARY KEY (actor, method, route, key)
);

CREATE TABLE outbox (
  id                uuid PRIMARY KEY,
  destination       varchar(8)   NOT NULL CHECK (destination IN ('KAFKA','RABBIT')),
  channel           varchar(160) NOT NULL,
  partition_key     varchar(100),
  aggregate_id      varchar(80)  NOT NULL,
  aggregate_version bigint       NOT NULL,
  type              varchar(80)  NOT NULL,
  payload           jsonb        NOT NULL,
  created_at        timestamptz  NOT NULL DEFAULT now(),
  published_at      timestamptz,
  attempts          int          NOT NULL DEFAULT 0,
  next_attempt_at   timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_outbox_pendiente ON outbox (destination, next_attempt_at) WHERE published_at IS NULL;

CREATE TABLE inbox (
  consumer     varchar(80) NOT NULL,
  message_id   uuid        NOT NULL,
  processed_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (consumer, message_id)
);

CREATE TABLE audit_log (
  id             uuid PRIMARY KEY,
  actor          varchar(100) NOT NULL,
  action         varchar(80)  NOT NULL,
  resource_id    varchar(80)  NOT NULL,
  id_hotel       int,
  correlation_id uuid         NOT NULL,
  occurred_at    timestamptz  NOT NULL DEFAULT now(),
  changes        jsonb
);
```

Esquema JSONB `changes` de auditoría: `{"campos":["estado","sedes"],"antes":{"estado":true},"despues":{"estado":false}}`. `campos` obligatorio (array no vacío de nombres de campo); `antes`/`despues` opcionales (presentes solo para los campos modificados; en altas solo `despues`, en bajas solo `antes`). Nunca incluye claves, hashes ni tokens; en `PERSONA_SEARCH`/`PERSONA_READ` no incluye documento ni correo.

Procedimientos no hacen HTTP, publican a brokers ni COMMIT interno. Spring abre transacción local que contiene mutación + outbox + idempotencia. Funciones/procedimientos con SQLSTATE traducido a error de negocio cuando se requiere rollback; parámetros vinculados, nunca concatenación. Los rechazos que deben dejar lápida/resultado durable (stock, admisión, bloqueo y cierre) se devuelven como resultado de negocio y se hace commit antes de responder 409; no lanzar una excepción que revierta esa evidencia.

JSON de request cerrado; los campos calculados se rechazan (precioUnitario/subTotal/total en una venta, estadoOperacion, idCliente ajeno). El precio de un maestro sí se admite en sus DTO administrativos explícitos. Las únicas excepciones transitorias se documentan en migración. No hay garantía exactly-once end-to-end; hay entrega al menos una vez y efectos idempotentes.

## Salud, readiness y resiliencia

Todo servicio expone `GET /actuator/health/liveness` (200 si el proceso vive, sin consultar dependencias) y `GET /actuator/health/readiness` (200 solo con su DB arriba). Las dependencias críticas por servicio — Kafka para los consumidores, RabbitMQ para notification y los consumidores de comandos, identity para la validación de JWT máquina — se reflejan como degradación (`DOWN`/`OUT_OF_SERVICE` visible al operador); una dependencia degradada no habilita por sí sola una operación de negocio que la necesite (`503 DEPENDENCY_UNAVAILABLE`). El Gateway no expone `/actuator/**` al exterior. Propiedades y variables por servicio: [configuracion.md](infraestructura/configuracion.md).

## Privacidad y retención

Sin job automático de borrado en el MVP; cada procedimiento es manual y auditado:

- identity: personas inactivas > 5 años se anonimizan con un procedimiento manual auditado (nombre, documento y correo se reemplazan por valores de anonimización; se conserva el id para el historial contable). El correo/documento no se usa para reidentificar en reportes.
- reception: conserva snapshots de cliente y habitación solo mientras exista la estadía o su cierre; las proyecciones y eventos no exponen documento ni correo.
- reporting: mantiene la minimización actual; los reportes usan nombre/apellido, nunca documento ni correo.
- notification: conserva su política ya definida (destinatario/variables a NULL en jobs ENVIADA > 30 días; estado/timestamps 90 días).

Claves, hashes y tokens no se guardan en snapshots, eventos, jobs ni `audit_log`.
