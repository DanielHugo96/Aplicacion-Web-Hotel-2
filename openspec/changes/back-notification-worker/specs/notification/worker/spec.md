# Spec Delta

## Purpose

Worker de correo de demostración que consume los tres comandos de notificación, persiste un trabajo idempotente por comando, lo despacha por SMTP con lease y reintentos, y lo opera con una CLI local auditada y purga por retención.

## ADDED Requirements

### Requirement: Consumo idempotente de las tres colas

notification SHALL consumir solamente las colas `notificacion.bienvenida`, `notificacion.checkin` y `notificacion.checkout` del exchange `hotel.commands.v1`, con acuse solo después de persistir el efecto y el inbox en la misma transacción. Una `commandId` SHALL producir un único job; la redelivery con el mismo payload SHALL NOT duplicarlo. La misma `commandId` con distinto hash de payload SHALL tratarse como error permanente a DLQ; una plantilla desconocida SHALL ir directo a DLQ sin reintentos TTL. Los errores antes de persistir SHALL usar los reintentos TTL de Rabbit (`retry.5s`, `retry.30s`, `retry.120s`) y luego la DLQ de su cola mediante el exchange `hotel.dlx.v1`; una vez persistido el job, el mensaje SHALL confirmarse y los reintentos SHALL pertenecer al job, nunca a Rabbit.

#### Scenario: Comando duplicado

- **WHEN** se entrega dos veces el mismo comando de bienvenida con la misma `commandId` y el mismo payload
- **THEN** existe un solo job con esa `commandId` y el segundo consumo no crea ni altera otro

#### Scenario: Mismo ID con distinto payload

- **WHEN** llega un comando con una `commandId` ya persistida pero con payload distinto
- **THEN** no se crea un segundo job y el mensaje termina en la DLQ de su cola

#### Scenario: Plantilla desconocida

- **WHEN** llega un comando con un tipo o versión de plantilla fuera de la allowlist
- **THEN** va directo a la DLQ sin pasar por los reintentos TTL

### Requirement: Validación de envelope y payload

notification SHALL validar cada comando contra su envelope (`commandId`, tipo, `schemaVersion` 1, `idHotel`, `correlationId`, `issuedAt`, payload) y su JSON Schema antes de encolar. Bienvenida SHALL traer `idHotel` nulo y payload con persona, destinatario, nombre y versión de plantilla 1; checkin SHALL traer `idHotel` obligatorio y payload con persona, recepción, destinatario, nombre, hotel, fecha de salida y versión 1; checkout SHALL traer `idHotel` obligatorio y payload con persona, recepción, destinatario, nombre, hotel, totales de alojamiento y consumos y versión 1. El destinatario SHALL ser un correo válido de hasta 254 caracteres; el mensaje SHALL NOT aceptar campos secretos, plantillas desconocidas, HTML arbitrario del emisor, adjuntos ni URLs a descargar.

#### Scenario: Bienvenida válida

- **WHEN** llega una bienvenida con `idHotel` nulo y payload completo con versión 1
- **THEN** el job queda persistido en estado PENDIENTE con su destinatario y variables

#### Scenario: Checkin sin sede

- **WHEN** llega un checkin sin `idHotel` o sin fecha de salida
- **THEN** no se crea el job y el mensaje sigue el camino de error permanente a DLQ

#### Scenario: Destinatario inválido

- **WHEN** el destinatario no es un correo válido o supera el límite fijado
- **THEN** no se crea el job y el mensaje termina en la DLQ sin reintentos de envío

### Requirement: Despacho con lease y reintentos SMTP

notification SHALL despachar cada job con un solo poseedor mediante lease persistido de 60 s tomado con bloqueo que salta filas tomadas. El envío SMTP SHALL ocurrir fuera de la transacción con timeout de 10 s y `Message-ID` estable por comando; el resultado SHALL registrarse solo con CAS sobre estado ENVIANDO con lease vigente y token coincidente. El éxito SHALL dejar ENVIADA con marca de envío; el fallo SHALL reprogramar a 1 min, 5 min y 30 min (4 intentos contando el inicial) y luego a ERROR. Un lease vencido SHALL volver a PENDIENTE eliminando su token; la respuesta tardía del poseedor anterior SHALL ignorarse. Un envío puede duplicarse si el worker cae entre enviar y registrar; ese duplicado SMTP SHALL tratarse como limitación explícita, nunca como doble venta o cobro. ENVIADA SHALL significar que SMTP aceptó el mensaje, nunca una entrega garantizada.

#### Scenario: Envío exitoso al primer intento

- **WHEN** el planificador toma un job PENDIENTE y Mailpit acepta el mensaje
- **THEN** el job queda ENVIADA con su marca de envío y no se reprograma

#### Scenario: SMTP caído con agotamiento (NOT01)

- **WHEN** Mailpit no acepta el mensaje y se repite el comando tras cerrar la estadía
- **THEN** el negocio que lo originó sigue en 200, existe un solo job por `commandId`, los reintentos ocurren a 1 min, 5 min y 30 min y al agotarse queda en ERROR

#### Scenario: Lease vencido con respuesta tardía (NOT02)

- **WHEN** un job ENVIANDO supera su lease, otro worker lo retoma y luego responde el worker anterior
- **THEN** el token anterior no modifica el job nuevo y el estado final lo decide el poseedor vigente

### Requirement: Plantillas y seeds

notification SHALL servir solo las plantillas BIENVENIDA, CHECKIN y CHECKOUT en versión 1, con asunto y cuerpo en texto plano y el aviso explícito de demostración sin valor fiscal. BIENVENIDA SHALL usar el nombre; CHECKIN SHALL usar nombre, hotel, recepción y fecha de salida; CHECKOUT SHALL usar nombre, hotel, recepción y totales de alojamiento y consumos. Las variables SHALL escaparse y SHALL rechazarse claves desconocidas o valores no escalares. Los seeds SHALL crear las tres plantillas y SHALL dejar jobs e inbox vacíos, sin destinatarios reales ni envíos. En desarrollo el SMTP SHALL apuntar a Mailpit; en producción el envío SHALL permanecer deshabilitado por defecto hasta configurar proveedor y autorización.

#### Scenario: Contenido de bienvenida

- **WHEN** se despacha una bienvenida con nombre
- **THEN** el correo lleva el asunto de bienvenida, el nombre escapado y el aviso de demostración sin valor fiscal

#### Scenario: Seeds limpios

- **WHEN** se aplican los seeds de demostración
- **THEN** existen las tres plantillas v1 y no existe ningún job ni envío

### Requirement: Herramienta de operador auditada

notification SHALL exponer la operación manual solo mediante la CLI local con perfil `admin`, sin red ni Gateway, contra `notification_db` con credencial de entorno, registrando cada acción en `audit_log` con actor, comando y resultado. La lista por estado SHALL mostrar comando, intentos y último código de error; el reintento SHALL reencolar preservando la `commandId` pasando por el inbox sin duplicar el job; el descarte SHALL pasar a estado terminal con motivo sin borrar el histórico; la purga por fecha SHALL seguir la política de retención. Si el payload es inválido, la corrección SHALL crear una `commandId` nueva con referencia a la original en auditoría; nunca SHALL editar el payload ni saltarse el inbox.

#### Scenario: Reintento desde ERROR

- **WHEN** el operador reintenta un job en ERROR con la CLI
- **THEN** el mismo comando vuelve a PENDIENTE sin duplicar el job y la acción queda auditada

#### Scenario: Descarte con motivo

- **WHEN** el operador descarta un job en ERROR
- **THEN** el job queda en estado terminal con motivo, conserva su histórico y la acción queda auditada

### Requirement: Retención y purga

notification SHALL poner a NULL el destinatario y las variables de los jobs ENVIADA con más de 30 días mediante la purga, conservando comando, estado y marcas 90 días para auditoría. La purga SHALL NOT tocar jobs PENDIENTE ni en ERROR. Cada purga SHALL quedar registrada en `audit_log` sin destinatario ni contenido privado.

#### Scenario: Purga de ENVIADA antigua (NOT03)

- **WHEN** se ejecuta la purga sobre una ENVIADA de más de 30 días y se intenta purgar una PENDIENTE y una en ERROR
- **THEN** la ENVIADA conserva su hash y estado pero pierde destinatario y variables, las otras dos quedan intactas y la acción queda en `audit_log`

### Requirement: Salud, degradación y privacidad operativa

notification SHALL exponer `GET /actuator/health/liveness` sin consultar dependencias y `GET /actuator/health/readiness` solo con PostgreSQL y RabbitMQ disponibles. Una caída de SMTP SHALL mostrarse como degradación visible sin bloquear el negocio; los jobs quedan en reintento y el resto del sistema sigue respondiendo. No SHALL habilitarse recolección de métricas prometheus en el MVP; la operación SHALL usar logs estructurados y health internos. Los logs SHALL NOT imprimir el destinatario completo ni contenido privado.

#### Scenario: Readiness con SMTP caído

- **WHEN** PostgreSQL y RabbitMQ responden pero Mailpit no acepta conexiones
- **THEN** readiness sigue en 200, la degradación SMTP es visible al operador y los cierres y registros siguen en 200

#### Scenario: Readiness sin Rabbit

- **WHEN** RabbitMQ no está disponible
- **THEN** readiness no está en 200 y ninguna operación que necesite la cola se declara lista

### Requirement: Sin negocio, sin Feign y sin Kafka

notification SHALL NOT exponer rutas de negocio, login propio ni endpoint de envío; el Gateway SHALL NOT enrutarlo y Angular SHALL NOT consumirlo. No SHALL consultar identity al enviar ni bloquear el cierre por la notificación; el destinatario y los datos mínimos SHALL venir en el comando de origen. La recepción del comando SHALL confirmar solo persistencia del job, nunca entrega del correo. El servicio SHALL derivar la identidad, cuando la necesite, del JWT validado y SHALL NOT confiar en headers de identidad.

#### Scenario: Comando recibido con SMTP caído

- **WHEN** llega un checkout válido mientras Mailpit está caído
- **THEN** el job queda persistido, el acuse Rabbit se confirma y el cierre que lo originó ya respondió 200 sin revertirse
