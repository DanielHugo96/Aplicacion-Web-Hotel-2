# Tasks

Los 12 tasks corresponden, en el mismo orden, a los 12 pasos de `docs/plans/back-notification-worker.md` §4. Cada uno incluye su forma de verificación.

## 1. Servicio y persistencia

- [x] 1.1 Dar de alta `services/notification-service/` con la estructura del monorepo y las versiones fijadas (Java 25, Spring Boot 4.1.1, Spring Cloud 2025.1.3, PostgreSQL 18.6, RabbitMQ 4.3.6): perfil dev, Flyway, registro en Eureka y convenciones mergeadas de `back-plataforma-comunes` y `back-plataforma-mensajeria`. Verificación: el build del monorepo compila el módulo; con PostgreSQL arriba el servicio arranca en dev, se registra en Eureka y `GET /actuator/health/liveness` responde 200.
- [ ] 1.2 Migración V1 de `notification_db` con `notification_job` y `template` del DDL de referencia, con sus CHECK, el índice por estado y `next_attempt_at` y la unicidad de `message_id`, más `inbox` y `audit_log` del DDL común y los tres procedimientos con las firmas fijadas. Verificación: la migración aplica sobre una base vacía; pruebas SQL demuestran que ENVIANDO sin lease falla por CHECK, que ENVIADA sin destinatario pasa pero PENDIENTE sin destinatario falla, y que dos jobs con el mismo `message_id` fallan por unicidad.

## 2. Seguridad sin rutas de negocio

- [ ] 2.1 Configurar el servicio sin rutas de negocio, sin login propio y sin endpoint de envío, con el Gateway sin enrutarle nada, deny-by-default y sin confiar en headers de identidad, y sin Feign ni Kafka. Verificación: el Gateway no expone ninguna ruta hacia notification; `GET /actuator/health/liveness` responde sin token; una búsqueda de `X-User-Id`, `X-Role` y `X-Hotel-Id` en el código devuelve 0 usos; no existe cliente Feign ni consumidor Kafka en el módulo.

## 3. Consumo idempotente

- [ ] 3.1 Implementar el consumo de `notificacion.bienvenida`, `notificacion.checkin` y `notificacion.checkout` con validación contra los JSON Schemas, `sp_notificacion_encolar` con inbox en la misma transacción del efecto y ack solo tras el commit. Verificación: tras publicar los tres comandos válidos hay un job PENDIENTE por `commandId`; el duplicado con el mismo payload no crea otro; el mismo ID con distinto hash va a la DLQ de su cola; la plantilla desconocida va directo a DLQ; con Rabbit caído antes de persistir el mensaje queda en reintento TTL y con el job ya persistido el ack ya se hizo.
- [ ] 3.2 Verificar `idHotel` nulo en bienvenida y obligatorio en checkin y checkout, destinatario como correo de hasta 254 caracteres y rechazo de secretos, plantillas desconocidas, HTML arbitrario, adjuntos y descargas. Verificación: bienvenida con sede responde a DLQ; checkin sin sede o sin fecha responde a DLQ; destinatario no correo o con campo secreto responde a DLQ; los mensajes publicados validan contra sus tres JSON Schemas.

## 4. Despacho con lease

- [ ] 4.1 Implementar el despacho con `fn_notificacion_tomar` con salto de filas tomadas, lease de 60 s con token nuevo, envío SMTP fuera de la transacción con timeout de 10 s y `Message-ID` estable, y `sp_notificacion_resultado` con CAS sobre ENVIANDO con lease vigente y token coincidente. Verificación: un job PENDIENTE pasa a ENVIANDO con token y vencimiento; si Mailpit acepta queda ENVIADA con marca de envío; si rechaza se reprograma a 1 min, 5 min y 30 min y al cuarto fallo queda en ERROR; el chequeo contra Mailpit muestra el `Message-ID` estable por comando.
- [ ] 4.2 Probar lease vencido y respuesta tardía del poseedor anterior. Verificación: caso NOT02 — con un job ENVIANDO vencido, el nuevo worker lo retoma y la respuesta tardía del anterior con su token viejo no modifica el job; evidencia en la fila del job y en el log; el posible duplicado SMTP queda documentado como limitación sin doble venta ni cobro.

## 5. Plantillas, CLI y retención

- [ ] 5.1 Sembrar las plantillas v1 BIENVENIDA, CHECKIN y CHECKOUT con asunto y cuerpo en texto plano, el aviso de demostración sin valor fiscal y variables escapadas, con jobs e inbox vacíos, SMTP a Mailpit en dev y deshabilitado por defecto en producción. Verificación: las tres plantillas existen con su versión 1; un despacho usa solo variables de su plantilla y rechaza claves desconocidas o valores no escalares; en seed no hay jobs ni envíos; con perfil productivo sin proveedor no se intenta ningún envío.
- [ ] 5.2 Implementar la CLI local con perfil `admin`, sin red ni Gateway, con `list --estado ERROR`, `retry`, `discard` y `purge --before`, cada acción auditada con actor, comando y resultado, y creación de comando nuevo con referencia auditada ante payload inválido. Verificación: `list` muestra comando, intentos y último error; `retry` preserva la `commandId` pasando por inbox sin duplicar; `discard` deja estado terminal con motivo sin borrar el histórico; la corrección de un payload inválido crea comando nuevo con referencia; cada acción deja su fila en `audit_log` sin destinatario ni contenido privado.
- [ ] 5.3 Implementar la purga por retención con destinatario y variables a NULL en ENVIADA de más de 30 días, conservación de estado y marcas 90 días y sin tocar PENDIENTE ni ERROR. Verificación: caso NOT03 — la purga sobre ENVIADA antigua conserva hash y estado pero deja NULL destinatario y variables, PENDIENTE y ERROR quedan intactos y la acción queda en `audit_log`.

## 6. Salud y cierre

- [ ] 6.1 Configurar `GET /actuator/health/liveness` sin dependencias y `GET /actuator/health/readiness` con PostgreSQL y RabbitMQ, con SMTP como degradación visible sin bloquear el negocio, sin prometheus y con logs sin destinatario completo ni contenido privado. Verificación: liveness 200 con todo caído; readiness 200 con base y Rabbit arriba y Mailpit caído mostrando la degradación; readiness no 200 sin Rabbit; el log de un despacho no contiene el correo completo ni el cuerpo privado.
- [ ] 6.2 Probar NOT01 con SMTP caído y comando repetido tras el cierre. Verificación: caso NOT01 — el cierre responde 200 con Mailpit caído, hay un solo job por `commandId`, los reintentos siguen 1 min, 5 min y 30 min hasta ERROR, y el buzón de Mailpit al recuperarse muestra el envío con el `Message-ID` estable; evidencia en `notification_job` y Mailpit adjunta al PR.
- [ ] 6.3 Cierre: `npx openspec validate back-notification-worker --strict` sin errores, los comandos canónicos de la sección «Verificación» del `README.md` raíz (build, pruebas y Compose con PostgreSQL, RabbitMQ y Mailpit) en verde, `python scripts/validar-planes.py` sin errores y la evidencia de NOT01–NOT03 adjunta al PR; al mergear, el plan pasa a `cerrado` y el change se archiva con el comando de OpenSpec. Verificación: las salidas de los dos validadores y las respuestas de los casos quedan adjuntas al PR de ejecución.
