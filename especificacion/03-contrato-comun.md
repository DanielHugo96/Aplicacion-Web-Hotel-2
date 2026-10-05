# Contrato común obligatorio

## Transporte, notación y tipos

Base externa local `http://localhost:8080`; en despliegue HTTPS. `A`=ADMIN, `E`=EMPLEADO, `C`=CLIENTE, `P`=sin login, `S(nombre)`=identidad de servicio autorizada. Todas las rutas son deny-by-default salvo una fila pública explícita.

IDs comerciales: entero positivo de 32 bits, compatible con Integer actual; IDs de operación, evento, comando, cierre y bloqueo: UUID. Strings recortados, correo lowercase. Campos comunes `version` bigint >0; `estado` boolean en maestros es activo/inactivo, no ocupación.

`L` en tablas de rutas = query `page=0,size=20` (máximo100); devuelve data[] y meta de paginación. Listas de maestros admiten además estado:boolean opcional (default true); los estados de estadía/venta solo se filtran cuando su ruta lo define. Orden estable por ID ascendente; reportes por clave de agrupación y cobros por fechaCierre,idRecepcion. No se acepta SQL/sort arbitrario del cliente. `H` = `idHotel` requerido en listados por sede; para recursos por ID se deriva de BD y se valida. `V` = header `If-Match: "<version>"` obligatorio en PUT/DELETE de recursos con version; sin él 428, versión distinta412. Rutas internas idempotentes no requieren V.

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

Idempotencia: clave única `(actor, método, plantillaRuta, key)`, hash del path+query+body canónico y respuesta persistidos. Misma clave y distinto request →409 IDEMPOTENCY_CONFLICT. Misma clave en curso →202 + referencia del recurso/operación; completada →mismo status/body, no repite efectos. Retención mínima30 días; claves de venta/stock/cierre se conservan con el historial. Error validado antes de comenzar no deja operación inconclusa. DELETE repetido con la misma clave devuelve el resultado original aunque el recurso cambie de estado.

## Respuestas

Mantener `success/message/data` del proyecto; agregar meta. Los nuevos contratos homogenizan carrito, antes texto o array sin envelope; Angular debe adaptarse.

```json
{"success":true,"message":"OK","data":{"idHabitacion":101,"version":1},"meta":{"correlationId":"11111111-1111-4111-8111-111111111111"}}
```

Listas: data array, meta `page,size,totalElements`. Internas: mismo envelope excepto token/JWKS. 204 no tiene cuerpo. Mutaciones CRUD:201 creación,200 actualización/baja; excepciones indicadas en cada ruta. Asíncrono202 no significa “venta confirmada” ni “salida completada”.

```json
{"success":false,"message":"La habitación no admite check-in","data":null,"error":{"code":"ROOM_NOT_READY","fields":{}},"meta":{"correlationId":"11111111-1111-4111-8111-111111111111"}}
```

400 validación/formato;401 no autenticado/expirado;403 rol no autorizado;404 inexistente o recurso ajeno a sede/propietario (evitar enumeración);409 regla de negocio/duplicado;410 ruta retirada;412 versión;428 precondición ausente;429 límite;503 dependencia no disponible. No stacktrace ni SQL en respuesta.

## Identidad y sedes

JWT RS256 emitido por identity. Persona claim `tipoPersona` = Administrador/Empleado/Cliente, `roles` = ADMIN/EMPLEADO/CLIENTE, `sub`=idPersona string, `sedes` array de ids, `iss=hotel-identity`, `aud=hotel-api`, `iat,exp,jti`. Un mapper usa el código inmutable del rol para generar tipoPersona/roles, nunca su etiqueta editable; los servicios autorizan con roles. TTL15min; sin refresh en MVP, re-login. Token solo en memoria del frontend; logout borra token. Deshabilitación/cambio de rol puede tardar hasta15min: limitación aceptada; claves deben poder rotarse.

ADMIN tiene alcance global; EMPLEADO solo `sedes` asignadas; CLIENTE solo recursos cuyo idCliente coincide con sub. No confiar en filtro frontend. Si servicio necesita saber propiedad de una estadía consulta a reception por Feign. Ante dependencia caída:503, nunca permitir por defecto.

Empleados pueden crear clientes, no otros empleados. Lectura de datos personales limitada y auditada; búsquedas exactas/paginadas, no exportación masiva para empleados.

Login público y registro público con rate limit y validación; contraseñas BCrypt, input write-only, jamás en respuesta/eventos/logs. Registro rechaza campos `idTipoPersona,roles,sedes,estado`: no permite elevación.

## Servicio a servicio

Feign resuelve nombres en Eureka. No entra por Gateway. Cada servicio obtiene JWT máquina en `POST /internal/auth/token` usando Basic clientId:secret por TLS (loopback dev permitido), form `grant_type=client_credentials&audience=<destino>`. Identity verifica allowlist de audiencia y emite TTL5min, `sub`=nombre servicio, `aud`=destino, `scope`=operaciones permitidas; no roles humanos. Un token de usuario no basta para /internal/**.

Caller valida usuario/sede primero; payload interno incluye actorId para auditoría, nunca para otorgar permisos al servicio. Consumers usan credenciales propias. Secretos y clave privada fuera de Git. JWT validado por Gateway y cada servicio, con algoritmo/issuer/audience/exp/scope explícitos. JWKS público sin privada. Internal solo en red privada; servicios tampoco exponen CRUD público directamente.

Scopes:
- reception → identity `clientes:read`; hotel `habitaciones:read`; sales `cierres:write`.
- sales → reception `recepciones:read operaciones:write`; inventory `stock:read stock:debit`.
- hotel → reception `bloqueos:write`.
- identity e inventory → hotel `hoteles:read`.
- reporting/notification → ninguna API de negocio en operación normal.

Feign connect timeout2s, read timeout5s como valores iniciales; circuit breaker. No retry ciego POST. Reintentar solo con misma clave/id de negocio. No mantener transacción SQL abierta durante llamada remota.

## Persistencia transversal

En tablas de datos los campos sin ? son NOT NULL; versiones inician en1, fechas de creación se fijan en servidor y timestamps de finalización son null hasta ocurrir. Las firmas con tipos/proyecciones describen el contrato a implementar, no un DDL ejecutado.

Cada DB usa usuario runtime propio sin acceso a otras bases; usuario migrador separado. Dinero numeric(12,2); timestamp timestamptz; índices por idHotel y fechas. FK solo local. Baja lógica `estado=false` en maestros; conservar snapshots y auditoría.

Tablas técnicas según necesidad, definidas una vez:
- `api_idempotency(actor varchar(100),method varchar(8),route varchar(160),key uuid,request_hash char(64),status varchar(16),resource_id varchar(80)?,http_status int?,response jsonb?,created_at timestamptz)`; PK actor+method+route+key.
- `outbox(id uuid PK,destination varchar(8),channel varchar(160),partition_key varchar(100)?,aggregate_id varchar(80),aggregate_version bigint,type varchar(80),payload jsonb,created_at timestamptz,published_at timestamptz?,attempts int default0,next_attempt_at timestamptz)`. Una fila por destino; no marcar dos brokers con un mismo flag.
- `inbox(consumer varchar(80),message_id uuid,processed_at timestamptz)` PK consumer+message_id, persistida en la misma transacción que el efecto.
- `audit_log(id uuid PK,actor varchar(100),action varchar(80),resource_id varchar(80),id_hotel int?,correlation_id uuid,occurred_at timestamptz,changes jsonb)`; excluir claves/tokens, minimizar PII.

Procedimientos no hacen HTTP, publican a brokers ni COMMIT interno. Spring abre transacción local que contiene mutación + outbox + idempotencia. Funciones/procedimientos con SQLSTATE traducido a error de negocio; parámetros vinculados, nunca concatenación.

JSON de request cerrado; los campos calculados se rechazan (precio,total,estadoOperacion,idCliente ajeno). Las únicas excepciones transitorias se documentan en migración. No hay garantía exactly-once end-to-end; hay entrega al menos una vez y efectos idempotentes.
