# Identity — contrato API

Usa [headers, JWT, errores, L/V e idempotencia comunes](../../03-contrato-comun.md). GET sin body. Ninguna API devuelve clave ni claveHash, ni con null. Persona pública no existe.

## REST

| Método y ruta | Acceso | Entrada | Éxito / efecto |
|---|---|---|---|
| POST /api/auth/login · LEGACY | P | Login | 200 Auth; sin idempotencia |
| POST /api/auth/registro · NUEVA | P | Registro, Idempotency-Key | 201 Persona; fuerza CLIENTE; bienvenida |
| GET /api/auth/me · NUEVA | A/E/C | — | 200 Persona propia |
| PUT /api/auth/me · NUEVA | A/E/C,V | Perfil | 200 Persona; no rol/sede/documento/estado |
| PUT /api/auth/password · NUEVA | A/E/C | claveActual,claveNueva | 200 data=null; verifica contraseña |
| POST /api/auth/social · RETIRADA | P | — | 410 SOCIAL_LOGIN_DISABLED; sin login alternativo |
| GET /api/persona/listar · LEGACY | A/E | L; idTipoPersona?; documento? | 200 Persona[]; E solo clientes y búsqueda exacta por documento obligatoria |
| GET /api/persona/buscar/{id} · LEGACY | A/E/C | id; E requiere documento exacto | 200 Persona; C solo propio; E solo CLIENTE con documento coincidente |
| POST /api/persona/registrar · LEGACY | A/E | PersonaAlta | 201 Persona; E solo CLIENTE sin sedes |
| PUT /api/persona/actualizar/{id} · LEGACY | A,V | PersonaUpdate | 200 Persona, cambios de rol/sedes auditados |
| DELETE /api/persona/eliminar/{id} · LEGACY | A,V | id | 200 Persona inactiva; no último ADMIN |
| GET /api/tipopersona/listar · LEGACY | A/E | — | 200 TipoPersona[] |
| GET /api/tipopersona/buscar/{id} · LEGACY | A/E | id | 200 TipoPersona |
| PUT /api/tipopersona/actualizar/{id} · LEGACY | A,V | descripcion:string[1..80] | 200 TipoPersona; solo etiqueta |
| POST /api/tipopersona/registrar · RETIRADA | A | — | 409 FIXED_ROLE_CATALOG |
| DELETE /api/tipopersona/eliminar/{id} · RETIRADA | A | — | 409 FIXED_ROLE_CATALOG |
| GET /.well-known/jwks.json · NUEVA | P | — | 200 estándar JWKS; claves públicas |
| POST /internal/auth/token · NUEVA | Basic servicio | form grant_type=client_credentials,audience | 200 token máquina; ver contrato común |
| GET /internal/clientes/{id} · NUEVA | S(reception),clientes:read | id | 200 ClienteVigente; 404 si no activo/Cliente |

Para GET persona de EMPLEADO se exige query documento exacto y se comprueba contra la persona de destino; no se confía en que la UI haya hecho una búsqueda antes. Sin documento:400. Nunca permite leer ADMIN/EMPLEADO ajeno. C no puede enumerar clientes.

Login:401 genérico sin distinguir correo ausente/clave inválida,429 tras5 intentos/min/IP+cuenta. Registro: máximo3/min/IP y validación antiabuso al desplegar públicamente. El MVP no verifica correo para operar como personal: el registro solo da rol cliente sin potestades administrativas.

## DTO de entrada

```json
{"correo":"cliente.uno@hotel.test","clave":"<entrada-del-usuario>"}
```

Login correo y clave requeridos. Clave de registro/cambio12–72 bytes UTF-8 (límite BCrypt), nunca truncar silenciosamente.

Registro: `{tipoDocumento,documento,nombre,apellido,correo,clave}`, todos requeridos. PersonaAlta mismos campos + idTipoPersona y sedes[]; clave opcional solo para cliente presencial sin cuenta; ADMIN/EMPLEADO requieren clave. PersonaUpdate: `{nombre,apellido,correo,fotoUrl?,idTipoPersona,sedes,estado}`; no cambiar documento ni contraseña por ese DTO. Perfil: `{nombre,apellido,fotoUrl?}`. Tanto PUT como DELETE impiden quitar/desactivar al último ADMIN activo. Promoción a personal exige clave_hash existente; un cliente presencial sin credenciales devuelve409 STAFF_CREDENTIAL_REQUIRED. EMPLEADO requiere al menos una sede; CLIENTE sedes=[]; ADMIN sedes=[] significa alcance global. Foto URL de origen permitido; no descarga de URL arbitraria por backend.

TipoPersona: `{idTipoPersona,descripcion,codigo,estado,version}`. codigo es ADMIN/EMPLEADO/CLIENTE y no cambia; estado de estos tres roles permanece true. tipoPersona en Persona/Auth es el nombre canónico Administrador/Empleado/Cliente derivado del código, no la descripcion editable. Campos con límites de BD.

## Respuestas data

Persona: `{idPersona,tipoDocumento,documento,nombre,apellido,correo,fotoUrl,idTipoPersona,tipoPersona,sedes,estado,fechaCreacion,version}`. E y C reciben datos mínimos de su consulta; sedes vacías en Cliente.

Auth mantiene forma legacy: `{idPersona,nombre,apellido,correo,tipoPersona,token,expiresIn:900,sedes}`. No refresh token. /me recupera sesión mientras token válido. Logout es local; revocación inmediata no prometida.

ClienteVigente: `{idPersona,tipoDocumento,documento,nombre,apellido,correo,estado:true,idTipoPersona:3,version}`.

Token máquina respuesta estándar sin envelope: `{access_token,token_type:"Bearer",expires_in:300}`. Basic contra hash por clientId, no credenciales del hotel.

## Feign saliente

Al asignar sedes, identity llama `GET /internal/hoteles/{id}` de hotel con scope hoteles:read. Solo sedes existentes/activas; hotel caído503, no asignación optimista. Scope se concede exclusivamente a identity/inventory; se añade a la allowlist de client tokens.

## Kafka y RabbitMQ

Kafka `identity.persona.v1`, PersonaSnapshot, key=idPersona. Payload `{idPersona,nombre,apellido,idTipoPersona,estado,version}`. Reporting consume; nombre es dato personal y se limita a uso interno. No documento/correo/clave/sedes.

Rabbit `notificacion.bienvenida` solo después de registro público confirmado; payload del [catálogo](../../05-mensajeria.md). No enviar correo desde transacción de registro. No consume brokers.

## Pruebas

Registro con roles/sedes/estado extras400; E alta ADMIN403; C lectura ajena404; sin token listado401; claims de sede falsificados401; código SQL en documento tratado como dato; refresh inexistente404; social410 y botón retirado.
