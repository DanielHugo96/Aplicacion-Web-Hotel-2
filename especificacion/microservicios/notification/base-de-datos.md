# Notification — base de datos, procedimientos y seeds

BD `notification_db`. Worker RabbitMQ + SMTP. Persistencia de trabajos, no otro CRM.

## Tablas

| Tabla | Columnas |
|---|---|
| notification_job | command_id:uuid PK; tipo:varchar(40); id_hotel:int?; id_persona:int; id_recepcion:int?; destinatario:varchar(254); template_version:int; variables:jsonb; estado:varchar(16) PENDIENTE/ENVIANDO/ENVIADA/ERROR; attempts:int default0; next_attempt_at:timestamptz; lease_until:timestamptz?; message_id:varchar(160) UNIQUE; created_at:timestamptz; sent_at:timestamptz?; last_error_code:varchar(80)? |
| template | tipo:varchar(40); version:int; subject:varchar(150); body_text:text; PK(tipo,version) |
| inbox | Común; mismo commit que inserción de job |

No FK a identity/reception. Índice job(estado,next_attempt_at). Una commandId produce un único job. Destinatario validado email, plantilla allowlist; sin HTML arbitrario del emisor, archivos adjuntos ni URLs a descargar.

## Procedimientos

| Firma | Función |
|---|---|
| sp_notificacion_encolar(p_command uuid,p_tipo varchar,p_payload jsonb) | Inbox+job insert idempotente; plantilla conocida obligatoria |
| fn_notificacion_tomar(p_ahora timestamptz) RETURNS SETOF notification_job | SELECT FOR UPDATE SKIP LOCKED; lease60s; toma lote máximo20 |
| sp_notificacion_resultado(p_command uuid,p_exito boolean,p_error varchar?) | Enviada o reintento con1min,5min,30min; agotados3 →ERROR |

SMTP ocurre fuera de transacción de DB. Un lease vencido vuelve a PENDIENTE; un envío puede duplicarse si no alcanzó a persistir ENVIADA. Message-ID estable `<commandId@hotel.test>` reduce duplicados en clientes, no garantiza exactly-once.

Errores antes de persistir job usan retry/DLQ Rabbit; una vez persistido se hace ack y los reintentos de envío pertenecen al job, no a Rabbit. No tener dos mecanismos reenviando el mismo trabajo a la vez.

## Seeds

Plantillas v1:
- BIENVENIDA: “Bienvenido al Hotel Demo”, variable nombre.
- CHECKIN: “Estadía registrada”, nombre,hotel,idRecepcion,fechaSalida.
- CHECKOUT: “Salida registrada”, nombre,hotel,idRecepcion,totalAlojamiento,totalConsumos.

Texto explícito “Notificación de demostración, no comprobante fiscal”. Variables escapadas. Jobs/inbox vacíos; no destinatarios reales ni envío durante seed.

Dev SMTP=Mailpit. Producción SMTP deshabilitado por defecto hasta configurar proveedor/autorización; no se envía correo real como parte de estas especificaciones.

Retención propuesta: purgar destinatario/variables de jobs terminados a30d, conservar commandId,estado,timestamps90d para auditoría; nunca purgar PENDIENTE/ERROR sin revisar. Esto es política técnica inicial, no asesoría de cumplimiento legal.

## Validación

Command duplicado un job; plantilla desconocida DLQ; SMTP caído no bloquea check-in/checkout; reintento no crea venta ni cobro; log no imprime destinatario completo ni contenido privado.
