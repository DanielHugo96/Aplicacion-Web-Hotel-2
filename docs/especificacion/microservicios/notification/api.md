# Notification — contrato de worker

## REST

No rutas de negocio externas, ni login propio, ni endpoint “enviar correo”. Gateway no enruta a este servicio. Angular no lo consume.

Endpoints técnicos internos:

- GET /actuator/health/liveness: 200 UP o 503; red privada, sin datos sensibles.
- GET /actuator/health/readiness: 200 si DB y Rabbit están disponibles; SMTP degradado se muestra al operador sin bloquear todo el negocio.
No se habilita /actuator/prometheus en el MVP; operación mediante logs estructurados y health internos.

Estos endpoints no sustituyen una API pública ni requieren BD adicional. No POST de reenvío expuesto; el operador recupera jobs con la CLI local definida abajo.

## Herramienta del operador (CLI local)

CLI local en notification-service, perfil `admin` (p. ej. `java -jar notification-service.jar --admin`). Corre contra `notification_db` con credencial de entorno, **no se expone en red ni por Gateway**, y cada acción escribe en `audit_log` (actor, commandId, resultado):

| Subcomando | Efecto |
|---|---|
| `list --estado ERROR` | Lista jobs en ERROR/DLQ con commandId, intentos y último código de error |
| `retry <commandId>` | Reencola el job preservando `commandId` y pasando por el inbox (no duplica el job) |
| `discard <commandId>` | Marca el job como descartado (estado terminal) con motivo; no borra el histórico |
| `purge --before <fecha>` | Purga destinatario/variables de ENVIADA anteriores a la fecha según retención; nunca toca PENDIENTE/ERROR |

Si el payload es inválido, se crea un `commandId` nuevo con referencia al original en auditoría; no se edita el payload ni se salta el inbox.

## RabbitMQ

Consume solamente:

1. notificacion.bienvenida → plantilla BIENVENIDA.
2. notificacion.checkin → plantilla CHECKIN.
3. notificacion.checkout → plantilla CHECKOUT.

Envelope/headers completos en [mensajería](../../05-mensajeria.md). Esquemas de payload exactos:

```json
{"idPersona":100,"destinatario":"cliente.uno@hotel.test","nombre":"Cliente Uno","templateVersion":1}
```

```json
{"idPersona":100,"idRecepcion":1001,"destinatario":"cliente.uno@hotel.test","nombre":"Cliente Uno","hotel":"Casa Sillar Lima Centro","fechaSalida":"2026-10-05","templateVersion":1}
```

```json
{"idPersona":100,"idRecepcion":1001,"destinatario":"cliente.uno@hotel.test","nombre":"Cliente Uno","hotel":"Casa Sillar Lima Centro","totalAlojamiento":70.00,"totalConsumos":7.00,"templateVersion":1}
```

idHotel en envelope obligatorio para checkin/checkout, null bienvenida. No aceptar campos secretos ni plantillas desconocidas.

Recepción del comando solo confirma persistencia de job, no entrega de correo. Worker despacha con Message-ID fijo; reintentos SMTP documentados en BD. No publicar “correo entregado” sin confirmación real del proveedor; estado ENVIADA significa SMTP aceptó el mensaje.

## Feign y Kafka

Ninguno. Destinatario/datos mínimos ya vienen de la transacción de origen; no consultar identity al enviar. No bloquear checkout por notificación. No es necesario conectar todos los microservicios a ambos brokers.

## Seguridad y operación

Rabbit vhost privado; usuario notification solo consume sus 3 colas. Management no expuesto al público. SMTP dev Mailpit; los tests inspeccionan el buzón local, no envían mensajes externos.

Operador puede revisar ERROR/DLQ y reintentar con la CLI preservando commandId. Si debe corregirse un payload inválido, crear nuevo commandId con referencia original en auditoría; no saltar inbox silenciosamente.
