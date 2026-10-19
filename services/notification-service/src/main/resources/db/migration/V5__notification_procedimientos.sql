-- Procedimientos de notification (docs/especificacion/microservicios/notification/base-de-datos.md
-- § Procedimientos). Firmas fijadas por la spec; sin HTTP, sin brokers y sin COMMIT interno.
--
-- Errores permanentes (no reintentables) se señalan con SQLSTATE propio y el listener los manda
-- directo a la DLQ: NT010 plantilla desconocida, NT011 mismo commandId con distinto payload_hash,
-- NT012 payload sin los campos obligatorios de su plantilla.
--
-- Valores configurables por entorno (configuracion.md § notification) leídos como GUC de sesión
-- con respaldo en el valor fijado por la spec: hotel.notification.lease_seconds (60) y
-- hotel.notification.max_attempts (3).

CREATE OR REPLACE FUNCTION notification_setting(p_name text, p_default text)
RETURNS text AS $$
  SELECT coalesce(nullif(current_setting(p_name, true), ''), p_default);
$$ LANGUAGE sql STABLE;

-- Inbox + job en una sola transacción (la abre Spring). Idempotente: repetir el mismo comando no
-- crea otro job ni cambia nada. `p_payload` es el cuerpo del comando (envelope + payload): de ahí
-- salen la sede, el destinatario y las variables de la plantilla.
CREATE OR REPLACE PROCEDURE sp_notificacion_encolar(
  p_command uuid,
  p_tipo varchar,
  p_payload jsonb
) AS $$
DECLARE
  v_cuerpo       jsonb;
  v_tipo         varchar(40);
  v_version      int;
  v_hash         char(64);
  v_previo       char(64);
  v_destinatario varchar(254);
  v_variables    jsonb;
BEGIN
  v_cuerpo := coalesce(p_payload -> 'payload', '{}'::jsonb);
  v_version := coalesce((v_cuerpo ->> 'templateVersion')::int, 0);
  v_tipo := upper(p_tipo);
  IF v_tipo NOT IN ('BIENVENIDA','CHECKIN','CHECKOUT')
     OR NOT EXISTS (SELECT 1 FROM template t WHERE t.tipo = v_tipo AND t.version = v_version) THEN
    RAISE EXCEPTION 'plantilla desconocida: tipo=% version=%', p_tipo, v_version
      USING ERRCODE = 'NT010';
  END IF;

  v_destinatario := v_cuerpo ->> 'destinatario';
  IF v_destinatario IS NULL
     OR (v_cuerpo ->> 'idPersona') IS NULL
     OR (v_cuerpo ->> 'nombre') IS NULL
     OR (v_tipo <> 'BIENVENIDA' AND ((v_cuerpo ->> 'hotel') IS NULL
         OR (v_cuerpo ->> 'idRecepcion') IS NULL)) THEN
    RAISE EXCEPTION 'payload sin los campos obligatorios de su plantilla: %', v_tipo
      USING ERRCODE = 'NT012';
  END IF;

  -- Solo las variables de su plantilla (base-de-datos.md § Esquemas JSONB).
  v_variables := CASE v_tipo
    WHEN 'BIENVENIDA' THEN jsonb_build_object('nombre', v_cuerpo -> 'nombre')
    WHEN 'CHECKIN' THEN jsonb_build_object(
        'nombre', v_cuerpo -> 'nombre',
        'hotel', v_cuerpo -> 'hotel',
        'idRecepcion', v_cuerpo -> 'idRecepcion',
        'fechaSalida', v_cuerpo -> 'fechaSalida')
    ELSE jsonb_build_object(
        'nombre', v_cuerpo -> 'nombre',
        'hotel', v_cuerpo -> 'hotel',
        'idRecepcion', v_cuerpo -> 'idRecepcion',
        'totalAlojamiento', v_cuerpo -> 'totalAlojamiento',
        'totalConsumos', v_cuerpo -> 'totalConsumos')
  END;

  v_hash := encode(sha256(convert_to(p_payload::text, 'UTF8')), 'hex');

  SELECT payload_hash INTO v_previo FROM notification_job WHERE command_id = p_command;
  IF FOUND THEN
    IF v_previo = v_hash THEN
      RETURN; -- redelivery del mismo comando: un solo job
    END IF;
    RAISE EXCEPTION 'commandId % ya persistida con payload distinto', p_command
      USING ERRCODE = 'NT011';
  END IF;

  INSERT INTO inbox (consumer, message_id, processed_at)
  VALUES ('notification-worker', p_command, now())
  ON CONFLICT (consumer, message_id) DO NOTHING;

  INSERT INTO notification_job (
      command_id, tipo, id_hotel, id_persona, id_recepcion, destinatario, template_version,
      variables, payload_hash, estado, attempts, next_attempt_at, message_id)
  VALUES (
      p_command, v_tipo, (p_payload ->> 'idHotel')::int, (v_cuerpo ->> 'idPersona')::int,
      (v_cuerpo ->> 'idRecepcion')::int, v_destinatario, v_version,
      v_variables, v_hash, 'PENDIENTE', 0, now(), p_command || '@hotel.test')
  ON CONFLICT (command_id) DO NOTHING;
END;
$$ LANGUAGE plpgsql;

-- Toma un job por worker con SKIP LOCKED. Antes recupera los leases vencidos a PENDIENTE (CAS que
-- elimina su token): la respuesta tardía del poseedor anterior ya no casa con ningún lease.
CREATE OR REPLACE FUNCTION fn_notificacion_tomar(p_ahora timestamptz)
RETURNS SETOF notification_job AS $$
DECLARE
  v_lease   int := coalesce(notification_setting('hotel.notification.lease_seconds', '60'), '60')::int;
  v_command uuid;
BEGIN
  UPDATE notification_job
     SET estado = 'PENDIENTE', lease_token = NULL, lease_until = NULL
   WHERE estado = 'ENVIANDO' AND lease_until < p_ahora;

  SELECT j.command_id INTO v_command
    FROM notification_job j
   WHERE j.estado = 'PENDIENTE' AND j.next_attempt_at <= p_ahora
   ORDER BY j.next_attempt_at
   FOR UPDATE SKIP LOCKED
   LIMIT 1;
  IF v_command IS NULL THEN
    RETURN;
  END IF;

  RETURN QUERY
  UPDATE notification_job j
     SET estado = 'ENVIANDO',
         attempts = j.attempts + 1,
         lease_token = gen_random_uuid(),
         lease_until = p_ahora + make_interval(secs => v_lease)
   WHERE j.command_id = v_command
  RETURNING j.*;
END;
$$ LANGUAGE plpgsql;

-- CAS sobre ENVIANDO con lease vigente y token coincidente. Éxito -> ENVIADA con marca de envío.
-- Fallo -> hasta 3 reintentos a 1 min, 5 min y 30 min (4 intentos contando el inicial); agotados,
-- ERROR. Una respuesta tardía (token viejo o lease vencido) no modifica nada.
CREATE OR REPLACE PROCEDURE sp_notificacion_resultado(
  p_command uuid,
  p_lease_token uuid,
  p_exito boolean,
  p_error varchar DEFAULT NULL
) AS $$
DECLARE
  v_attempts int;
  v_max      int := coalesce(notification_setting('hotel.notification.max_attempts', '3'), '3')::int + 1;
  v_espera   interval;
BEGIN
  SELECT attempts INTO v_attempts
    FROM notification_job
   WHERE command_id = p_command
     AND estado = 'ENVIANDO'
     AND lease_token = p_lease_token
     AND lease_until >= now()
   FOR UPDATE;
  IF NOT FOUND THEN
    RETURN;
  END IF;

  IF p_exito THEN
    UPDATE notification_job
       SET estado = 'ENVIADA', sent_at = now(), last_error_code = NULL,
           lease_token = NULL, lease_until = NULL
     WHERE command_id = p_command;
  ELSIF v_attempts >= v_max THEN
    UPDATE notification_job
       SET estado = 'ERROR', last_error_code = left(p_error, 80),
           lease_token = NULL, lease_until = NULL
     WHERE command_id = p_command;
  ELSE
    v_espera := CASE v_attempts
      WHEN 1 THEN interval '1 minute'
      WHEN 2 THEN interval '5 minutes'
      ELSE interval '30 minutes'
    END;
    UPDATE notification_job
       SET estado = 'PENDIENTE', next_attempt_at = now() + v_espera,
           last_error_code = left(p_error, 80),
           lease_token = NULL, lease_until = NULL
     WHERE command_id = p_command;
  END IF;
END;
$$ LANGUAGE plpgsql;