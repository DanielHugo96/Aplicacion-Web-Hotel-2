-- Seeds de dev/demo: plantillas v1 de notification (docs/especificacion/microservicios/
-- notification/base-de-datos.md § Seeds). Idempotente. Jobs e inbox quedan vacíos: no hay
-- destinatarios reales ni envíos en el seed.
INSERT INTO template (tipo, version, subject, body_text) VALUES
  ('BIENVENIDA', 1, 'Bienvenido al Hotel Demo',
   E'Bienvenido al Hotel Demo\n\nHola {{nombre}},\n\nGracias por registrarte. Este correo confirma tu alta en la demo del hotel.\n\nNotificación de demostración, no comprobante fiscal.\n'),
  ('CHECKIN', 1, 'Estadía registrada',
   E'Estadía registrada\n\nHola {{nombre}},\n\nTu check-in en {{hotel}} quedó registrado (estadía {{idRecepcion}}).\nFecha de salida prevista: {{fechaSalida}}.\n\nNotificación de demostración, no comprobante fiscal.\n'),
  ('CHECKOUT', 1, 'Salida registrada',
   E'Salida registrada\n\nHola {{nombre}},\n\nTu salida de {{hotel}} quedó registrada (estadía {{idRecepcion}}).\nTotal de alojamiento: {{totalAlojamiento}}.\nTotal de consumos: {{totalConsumos}}.\n\nNotificación de demostración, no comprobante fiscal.\n')
ON CONFLICT (tipo, version) DO NOTHING;