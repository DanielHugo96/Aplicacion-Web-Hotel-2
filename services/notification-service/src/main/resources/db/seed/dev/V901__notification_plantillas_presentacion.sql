-- Presentación real de notification_db: plantillas v1 con la marca Casa Sillar.
-- Seeds: docs/especificacion/microservicios/notification/base-de-datos.md#seeds
--
-- La migración es correctiva sobre las filas que ya sembró V900__notification_plantillas.sql.
-- No se edita V900 porque su checksum ya está aplicado en las bases migradas y Flyway rechazaría
-- el cambio (docs/implementacion/decisiones/IA-back-notification-presentacion-real-01.md).
--
-- Cada UPDATE reconoce la fila semilla por su clave fija (tipo, version) y conserva
-- templateVersion = 1 y las variables de cada plantilla, que son las que fija la spec en
-- § Esquemas JSONB: {{nombre}} en las tres; {{hotel}}, {{idRecepcion}} y {{fechaSalida}} en
-- CHECKIN; {{hotel}}, {{idRecepcion}}, {{totalAlojamiento}} y {{totalConsumos}} en CHECKOUT.
-- El pie es el de § Seeds: «Notificación automática de la cadena; no es un comprobante fiscal».

-- 1) BIENVENIDA: asunto y cuerpo con la marca Casa Sillar, sin «Hotel Demo» ni «demostración».
UPDATE template
SET subject   = 'Bienvenido a Casa Sillar',
    body_text = E'Bienvenido a Casa Sillar\n\nHola {{nombre}},\n\nGracias por registrarte. Este correo confirma tu alta en Casa Sillar.\n\nNotificación automática de la cadena; no es un comprobante fiscal.\n'
WHERE tipo = 'BIENVENIDA'
  AND version = 1;

-- 2) CHECKIN: mismo asunto; cuerpo con sede, estadía y fecha de salida prevista.
UPDATE template
SET subject   = 'Estadía registrada',
    body_text = E'Estadía registrada\n\nHola {{nombre}},\n\nTu check-in en {{hotel}} quedó registrado (estadía {{idRecepcion}}).\nFecha de salida prevista: {{fechaSalida}}.\n\nNotificación automática de la cadena; no es un comprobante fiscal.\n'
WHERE tipo = 'CHECKIN'
  AND version = 1;

-- 3) CHECKOUT: mismo asunto; cuerpo con sede, estadía y los dos totales del cierre.
UPDATE template
SET subject   = 'Salida registrada',
    body_text = E'Salida registrada\n\nHola {{nombre}},\n\nTu salida de {{hotel}} quedó registrada (estadía {{idRecepcion}}).\nTotal de alojamiento: {{totalAlojamiento}}.\nTotal de consumos: {{totalConsumos}}.\n\nNotificación automática de la cadena; no es un comprobante fiscal.\n'
WHERE tipo = 'CHECKOUT'
  AND version = 1;
