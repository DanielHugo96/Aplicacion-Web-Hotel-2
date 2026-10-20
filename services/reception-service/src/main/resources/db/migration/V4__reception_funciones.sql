-- Procedimientos y funciones de reception_db (docs/especificacion/microservicios/reception/base-de-datos.md
-- § Procedimientos / funciones). No hacen HTTP ni COMMIT: la transacción y el outbox los abre Spring.

-- Bootstrap de gate desde hotel.habitacion.v1: solo crea el gate ausente en LIBRE si la habitación
-- es LISTA y activa. Nunca sobreescribe un gate existente.
CREATE OR REPLACE FUNCTION sp_gate_inicializar(
  p_habitacion int,
  p_hotel      int,
  p_version    bigint
) RETURNS jsonb
LANGUAGE plpgsql AS $$
BEGIN
  INSERT INTO room_gate (id_habitacion, id_hotel, estado, room_version, hotel_version, updated_at)
  VALUES (p_habitacion, p_hotel, 'LIBRE', 0, p_version, now())
  ON CONFLICT (id_habitacion) DO NOTHING;
  RETURN jsonb_build_object('ok', true);
END;
$$;

-- Seguimiento informativo del catálogo: actualiza hotel_version sin tocar el estado del gate.
CREATE OR REPLACE FUNCTION sp_gate_snapshot(
  p_habitacion int,
  p_hotel      int,
  p_version    bigint
) RETURNS jsonb
LANGUAGE plpgsql AS $$
BEGIN
  UPDATE room_gate
     SET hotel_version = p_version, updated_at = now()
   WHERE id_habitacion = p_habitacion AND id_hotel = p_hotel;
  RETURN jsonb_build_object('ok', true);
END;
$$;

-- Libera solo el ciclo de limpieza vigente del gate. Un ciclo viejo no libera uno posterior.
CREATE OR REPLACE FUNCTION sp_gate_liberar_ciclo(
  p_habitacion int,
  p_ciclo      uuid,
  p_version    bigint
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_room_version bigint;
BEGIN
  UPDATE room_gate
     SET estado = 'LIBRE', id_recepcion = NULL, cleaning_cycle_id = NULL, block_id = NULL,
         room_version = room_version + 1, hotel_version = p_version, updated_at = now()
   WHERE id_habitacion = p_habitacion
     AND estado = 'LIMPIEZA_PENDIENTE'
     AND cleaning_cycle_id = p_ciclo
  RETURNING room_version INTO v_room_version;
  IF v_room_version IS NULL THEN
    RETURN jsonb_build_object('ok', false, 'code', 'NOT_MATCHING');
  END IF;
  RETURN jsonb_build_object('ok', true, 'roomVersion', v_room_version);
END;
$$;

-- Libera solo el bloqueo coincidente y deja el bloqueo durable en LIBERADO.
CREATE OR REPLACE FUNCTION sp_gate_liberar_bloqueo(
  p_habitacion int,
  p_block      uuid,
  p_version    bigint
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_room_version bigint;
BEGIN
  UPDATE room_gate
     SET estado = 'LIBRE', id_recepcion = NULL, cleaning_cycle_id = NULL, block_id = NULL,
         room_version = room_version + 1, hotel_version = p_version, updated_at = now()
   WHERE id_habitacion = p_habitacion
     AND estado = 'BLOQUEADA'
     AND block_id = p_block
  RETURNING room_version INTO v_room_version;
  IF v_room_version IS NULL THEN
    RETURN jsonb_build_object('ok', false, 'code', 'NOT_MATCHING');
  END IF;
  UPDATE bloqueo_habitacion
     SET estado = 'LIBERADO', released_at = now()
   WHERE block_id = p_block AND estado = 'ACTIVO';
  RETURN jsonb_build_object('ok', true, 'roomVersion', v_room_version);
END;
$$;

-- Check-in inmediato: lock del gate, exige LIBRE, inserta ACTIVA, ocupa el gate y sube roomVersion.
CREATE OR REPLACE FUNCTION fn_checkin(
  p_request           jsonb,
  p_cliente_snapshot  jsonb,
  p_habitacion_snapshot jsonb
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_habitacion int := (p_request->>'idHabitacion')::int;
  v_hotel      int := (p_request->>'idHotel')::int;
  v_cliente    int := (p_request->>'idCliente')::int;
  v_entrada    date := (p_request->>'fechaEntrada')::date;
  v_salida     date := (p_request->>'fechaSalida')::date;
  v_adelanto   numeric(12,2) := (p_request->>'adelanto')::numeric;
  v_medio      text := NULLIF(p_request->>'medioPago', '');
  v_obs        text := NULLIF(p_request->>'observacion', '');
  v_tarifa     numeric(12,2) := (p_habitacion_snapshot->>'precio')::numeric;
  v_noches     int;
  v_precio     numeric(12,2);
  v_gate       room_gate%ROWTYPE;
  v_id         int;
  v_room_version bigint;
BEGIN
  SELECT * INTO v_gate FROM room_gate WHERE id_habitacion = v_habitacion FOR UPDATE;
  IF NOT FOUND OR v_gate.id_hotel <> v_hotel THEN
    RETURN jsonb_build_object('ok', false, 'code', 'ROOM_NOT_SYNCED');
  END IF;
  IF v_gate.estado = 'OCUPADA' THEN
    RETURN jsonb_build_object('ok', false, 'code', 'ROOM_OCCUPIED');
  END IF;
  IF v_gate.estado IN ('LIMPIEZA_PENDIENTE','BLOQUEADA') THEN
    RETURN jsonb_build_object('ok', false, 'code', 'ROOM_NOT_READY');
  END IF;

  v_noches := v_salida - v_entrada;
  IF v_noches < 1 OR v_noches > 365 THEN
    RETURN jsonb_build_object('ok', false, 'code', 'VALIDATION_ERROR', 'field', 'fechaSalida');
  END IF;
  v_precio := ROUND(v_tarifa * v_noches, 2);
  IF v_adelanto < 0 OR (v_adelanto > 0 AND v_medio IS NULL) OR v_adelanto > v_precio THEN
    RETURN jsonb_build_object('ok', false, 'code', 'VALIDATION_ERROR', 'field', 'adelanto');
  END IF;

  INSERT INTO recepcion (
    id_hotel, id_habitacion, id_cliente, cliente_snapshot, habitacion_snapshot, tarifa,
    fecha_entrada, fecha_salida, precio_inicial, adelanto, medio_pago_adelanto,
    precio_restante, total_pagado, estado_estadia, observacion, version)
  VALUES (
    v_hotel, v_habitacion, v_cliente, p_cliente_snapshot, p_habitacion_snapshot, v_tarifa,
    v_entrada, v_salida, v_precio, v_adelanto, v_medio,
    v_precio - v_adelanto, v_adelanto, 'ACTIVA', v_obs, 1)
  RETURNING id INTO v_id;

  UPDATE room_gate
     SET estado = 'OCUPADA', id_recepcion = v_id, cleaning_cycle_id = NULL, block_id = NULL,
         room_version = room_version + 1,
         hotel_version = COALESCE((p_habitacion_snapshot->>'version')::bigint, hotel_version),
         updated_at = now()
   WHERE id_habitacion = v_habitacion
  RETURNING room_version INTO v_room_version;

  RETURN jsonb_build_object(
    'ok', true,
    'idRecepcion', v_id,
    'roomVersion', v_room_version,
    'tarifa', v_tarifa,
    'precioInicial', v_precio,
    'fechaEntrada', v_entrada,
    'fechaSalida', v_salida);
EXCEPTION WHEN unique_violation THEN
  RETURN jsonb_build_object('ok', false, 'code', 'ROOM_OCCUPIED');
END;
$$;

-- Edición de salida: solo ACTIVA, sin operaciones abiertas, recalcula precio sin tocar adelanto.
CREATE OR REPLACE FUNCTION sp_recepcion_actualizar(
  p_id          int,
  p_salida      date,
  p_observacion varchar,
  p_version     bigint
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_rec         recepcion%ROWTYPE;
  v_noches      int;
  v_precio      numeric(12,2);
  v_room_version bigint;
BEGIN
  SELECT * INTO v_rec FROM recepcion WHERE id = p_id FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;
  IF v_rec.estado_estadia <> 'ACTIVA' THEN
    RETURN jsonb_build_object('ok', false, 'code', 'STAY_NOT_ACTIVE');
  END IF;
  IF v_rec.version <> p_version THEN
    RETURN jsonb_build_object('ok', false, 'code', 'PRECONDITION_FAILED');
  END IF;
  IF EXISTS (SELECT 1 FROM operacion_consumo WHERE id_recepcion = p_id AND estado = 'ABIERTA') THEN
    RETURN jsonb_build_object('ok', false, 'code', 'OPERATIONS_IN_PROGRESS');
  END IF;

  v_noches := p_salida - v_rec.fecha_entrada;
  IF v_noches < 1 OR v_noches > 365 THEN
    RETURN jsonb_build_object('ok', false, 'code', 'VALIDATION_ERROR', 'field', 'fechaSalida');
  END IF;
  v_precio := ROUND(v_rec.tarifa * v_noches, 2);
  IF v_precio < v_rec.adelanto THEN
    RETURN jsonb_build_object('ok', false, 'code', 'ADVANCE_EXCEEDS_NEW_TOTAL');
  END IF;

  UPDATE recepcion
     SET fecha_salida = p_salida,
         observacion = p_observacion,
         precio_inicial = v_precio,
         precio_restante = v_precio - adelanto,
         version = version + 1
   WHERE id = p_id;

  UPDATE room_gate
     SET room_version = room_version + 1, updated_at = now()
   WHERE id_recepcion = p_id
  RETURNING room_version INTO v_room_version;

  RETURN jsonb_build_object(
    'ok', true, 'version', v_rec.version + 1,
    'roomVersion', COALESCE(v_room_version, 0), 'precioInicial', v_precio);
END;
$$;

-- Bloqueo administrativo durable por blockId. Mismo blockId + mismo hash conserva la decisión.
CREATE OR REPLACE FUNCTION sp_habitacion_bloquear(
  p_block      uuid,
  p_habitacion int,
  p_hotel      int,
  p_motivo     varchar,
  p_hash       char(64)
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_gate      room_gate%ROWTYPE;
  v_existente bloqueo_habitacion%ROWTYPE;
  v_room_version bigint;
BEGIN
  SELECT * INTO v_existente FROM bloqueo_habitacion WHERE block_id = p_block FOR UPDATE;
  IF FOUND THEN
    IF v_existente.request_hash <> p_hash
       OR v_existente.id_habitacion <> p_habitacion
       OR v_existente.id_hotel <> p_hotel THEN
      RETURN jsonb_build_object('ok', false, 'code', 'IDEMPOTENCY_CONFLICT');
    END IF;
    IF v_existente.estado = 'ACTIVO' THEN
      SELECT room_version INTO v_room_version FROM room_gate WHERE id_habitacion = p_habitacion;
      RETURN jsonb_build_object('ok', true, 'decision', 'ACTIVO', 'roomVersion', v_room_version);
    END IF;
    RETURN jsonb_build_object('ok', false, 'code', 'GATE_NOT_AVAILABLE', 'decision', v_existente.estado);
  END IF;

  SELECT * INTO v_gate FROM room_gate WHERE id_habitacion = p_habitacion FOR UPDATE;
  IF NOT FOUND OR v_gate.id_hotel <> p_hotel THEN
    INSERT INTO bloqueo_habitacion (block_id, id_habitacion, id_hotel, motivo, request_hash, estado, error_code)
    VALUES (p_block, p_habitacion, p_hotel, p_motivo, p_hash, 'RECHAZADO', 'GATE_NOT_AVAILABLE');
    RETURN jsonb_build_object('ok', false, 'code', 'GATE_NOT_AVAILABLE', 'decision', 'RECHAZADO');
  END IF;

  IF v_gate.estado <> 'LIBRE' THEN
    INSERT INTO bloqueo_habitacion (block_id, id_habitacion, id_hotel, motivo, request_hash, estado, error_code)
    VALUES (p_block, p_habitacion, p_hotel, p_motivo, p_hash, 'RECHAZADO', 'GATE_NOT_AVAILABLE');
    RETURN jsonb_build_object('ok', false, 'code', 'GATE_NOT_AVAILABLE', 'decision', 'RECHAZADO');
  END IF;

  INSERT INTO bloqueo_habitacion (block_id, id_habitacion, id_hotel, motivo, request_hash, estado)
  VALUES (p_block, p_habitacion, p_hotel, p_motivo, p_hash, 'ACTIVO');

  UPDATE room_gate
     SET estado = 'BLOQUEADA', id_recepcion = NULL, cleaning_cycle_id = NULL, block_id = p_block,
         room_version = room_version + 1, updated_at = now()
   WHERE id_habitacion = p_habitacion
  RETURNING room_version INTO v_room_version;

  RETURN jsonb_build_object('ok', true, 'decision', 'ACTIVO', 'roomVersion', v_room_version);
END;
$$;

-- Admisión durable de operación de consumo. Orden de locks: recepción y después operación.
CREATE OR REPLACE FUNCTION sp_operacion_admitir(
  p_operation uuid,
  p_recepcion int,
  p_venta     int,
  p_tipo      varchar
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_rec recepcion%ROWTYPE;
  v_op  operacion_consumo%ROWTYPE;
BEGIN
  SELECT * INTO v_rec FROM recepcion WHERE id = p_recepcion FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;

  SELECT * INTO v_op FROM operacion_consumo WHERE operation_id = p_operation FOR UPDATE;
  IF FOUND THEN
    IF v_op.id_recepcion <> p_recepcion OR v_op.id_venta <> p_venta OR v_op.tipo <> p_tipo THEN
      RETURN jsonb_build_object('ok', false, 'code', 'VALIDATION_ERROR');
    END IF;
    IF v_op.estado = 'FINALIZADA' THEN
      RETURN jsonb_build_object('ok', false, 'code', 'STAY_NOT_ACTIVE', 'resultado', v_op.resultado);
    END IF;
    RETURN jsonb_build_object('ok', true, 'estado', 'ABIERTA', 'tipo', v_op.tipo);
  END IF;

  IF v_rec.estado_estadia <> 'ACTIVA' THEN
    INSERT INTO operacion_consumo (operation_id, id_recepcion, id_venta, tipo, estado, resultado, finished_at)
    VALUES (p_operation, p_recepcion, p_venta, p_tipo, 'FINALIZADA', 'RECHAZADA', now())
    ON CONFLICT (operation_id) DO NOTHING;
    RETURN jsonb_build_object('ok', false, 'code', 'STAY_NOT_ACTIVE', 'resultado', 'RECHAZADA');
  END IF;

  INSERT INTO operacion_consumo (operation_id, id_recepcion, id_venta, tipo, estado)
  VALUES (p_operation, p_recepcion, p_venta, p_tipo, 'ABIERTA');
  RETURN jsonb_build_object('ok', true, 'estado', 'ABIERTA', 'tipo', p_tipo);
EXCEPTION WHEN unique_violation THEN
  SELECT * INTO v_op FROM operacion_consumo WHERE operation_id = p_operation;
  IF v_op.estado = 'FINALIZADA' THEN
    RETURN jsonb_build_object('ok', false, 'code', 'STAY_NOT_ACTIVE', 'resultado', v_op.resultado);
  END IF;
  RETURN jsonb_build_object('ok', true, 'estado', 'ABIERTA', 'tipo', v_op.tipo);
END;
$$;

-- Finalización con lápida completa. Acepta llegar antes de la admisión y nunca revive una FINALIZADA.
CREATE OR REPLACE FUNCTION sp_operacion_finalizar(
  p_operation uuid,
  p_recepcion int,
  p_venta     int,
  p_tipo      varchar,
  p_resultado varchar
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_rec recepcion%ROWTYPE;
  v_op  operacion_consumo%ROWTYPE;
BEGIN
  IF NOT (
    (p_tipo = 'VENTA' AND p_resultado IN ('CONFIRMADA','RECHAZADA','CANCELADA')) OR
    (p_tipo = 'PAGO' AND p_resultado IN ('PAGO_REGISTRADO','RECHAZADA')) OR
    (p_tipo = 'ANULACION' AND p_resultado IN ('CANCELADA','RECHAZADA'))
  ) THEN
    RETURN jsonb_build_object('ok', false, 'code', 'INVALID_RESULT');
  END IF;

  SELECT * INTO v_rec FROM recepcion WHERE id = p_recepcion FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;

  SELECT * INTO v_op FROM operacion_consumo WHERE operation_id = p_operation FOR UPDATE;
  IF FOUND THEN
    IF v_op.id_recepcion <> p_recepcion OR v_op.id_venta <> p_venta OR v_op.tipo <> p_tipo THEN
      RETURN jsonb_build_object('ok', false, 'code', 'TUPLE_MISMATCH');
    END IF;
    IF v_op.estado = 'FINALIZADA' THEN
      IF v_op.resultado = p_resultado THEN
        RETURN jsonb_build_object('ok', true, 'estado', 'FINALIZADA', 'resultado', v_op.resultado);
      END IF;
      RETURN jsonb_build_object('ok', false, 'code', 'CONTRADICTION');
    END IF;
    UPDATE operacion_consumo
       SET estado = 'FINALIZADA', resultado = p_resultado, finished_at = now()
     WHERE operation_id = p_operation;
    RETURN jsonb_build_object('ok', true, 'estado', 'FINALIZADA', 'resultado', p_resultado);
  END IF;

  INSERT INTO operacion_consumo (operation_id, id_recepcion, id_venta, tipo, estado, resultado, finished_at)
  VALUES (p_operation, p_recepcion, p_venta, p_tipo, 'FINALIZADA', p_resultado, now());
  RETURN jsonb_build_object('ok', true, 'estado', 'FINALIZADA', 'resultado', p_resultado, 'lapida', true);
END;
$$;
