-- Cierre de estadía de reception_db (docs/especificacion/microservicios/reception/base-de-datos.md
-- § DDL de referencia y § Procedimientos / funciones). La numeración real es V5 porque
-- back-reception-operacion dejó V1..V4 (idempotencia, outbox/inbox, tablas y funciones de operación).

CREATE TABLE cierre (
  closure_id        uuid          PRIMARY KEY,
  id_recepcion      int           NOT NULL REFERENCES recepcion(id),
  estado            varchar(20)   NOT NULL CHECK (estado IN ('VALIDANDO','CONFIRMANDO','COMPLETADO','RECHAZADO')),
  request_hash      char(64)      NOT NULL,
  request           jsonb         NOT NULL,
  cobro_declarado   numeric(12,2) NOT NULL CHECK (cobro_declarado >= 0),
  penalidad         numeric(12,2) NOT NULL CHECK (penalidad >= 0),
  medio_pago        varchar(12)   CHECK (medio_pago IN ('EFECTIVO','TARJETA','OTRO')),
  calculo           jsonb,
  receipt_sales     jsonb,
  cleaning_cycle_id uuid          NOT NULL,
  attempts          int           NOT NULL DEFAULT 0,
  next_attempt_at   timestamptz   NOT NULL DEFAULT now(),
  last_error_code   varchar(80),
  completed_at      timestamptz,
  CHECK (estado NOT IN ('CONFIRMANDO','COMPLETADO') OR calculo IS NOT NULL),
  CHECK (estado <> 'COMPLETADO' OR (receipt_sales IS NOT NULL AND completed_at IS NOT NULL))
);
CREATE UNIQUE INDEX uq_cierre_vigente ON cierre (id_recepcion) WHERE estado <> 'RECHAZADO';
CREATE INDEX ix_cierre_recuperacion ON cierre (estado, next_attempt_at);

-- Inicio del cierre: lock de la recepción, cero operaciones ABIERTAS, ACTIVA -> CERRANDO y cierre
-- VALIDANDO con solicitud inmutable. Misma closureId reanuda; otra clave sobre CERRANDO responde
-- CLOSURE_IN_PROGRESS sin tocar nada.
CREATE OR REPLACE FUNCTION sp_cierre_iniciar(
  p_closure   uuid,
  p_recepcion int,
  p_request   jsonb
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_rec     recepcion%ROWTYPE;
  v_cierre  cierre%ROWTYPE;
  v_hash    char(64);
  v_room    bigint;
BEGIN
  v_hash := encode(sha256(convert_to(p_request::text, 'UTF8')), 'hex');

  SELECT * INTO v_rec FROM recepcion WHERE id = p_recepcion FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;

  -- Idempotencia antes que estado: la misma clave reanuda el cierre que ya existe.
  SELECT * INTO v_cierre FROM cierre WHERE closure_id = p_closure FOR UPDATE;
  IF FOUND THEN
    IF v_cierre.id_recepcion <> p_recepcion OR v_cierre.request_hash <> v_hash THEN
      RETURN jsonb_build_object('ok', false, 'code', 'IDEMPOTENCY_CONFLICT');
    END IF;
    SELECT room_version INTO v_room FROM room_gate WHERE id_habitacion = v_rec.id_habitacion;
    RETURN jsonb_build_object(
      'ok', true, 'resume', true, 'closureId', p_closure, 'idRecepcion', p_recepcion,
      'estado', v_cierre.estado, 'roomVersion', COALESCE(v_room, 0));
  END IF;

  IF v_rec.estado_estadia = 'ACTIVA' THEN
    IF EXISTS (SELECT 1 FROM operacion_consumo WHERE id_recepcion = p_recepcion AND estado = 'ABIERTA') THEN
      RETURN jsonb_build_object('ok', false, 'code', 'OPERATIONS_IN_PROGRESS');
    END IF;
    IF EXISTS (SELECT 1 FROM cierre WHERE id_recepcion = p_recepcion AND estado <> 'RECHAZADO') THEN
      RETURN jsonb_build_object('ok', false, 'code', 'CLOSURE_IN_PROGRESS');
    END IF;

    UPDATE recepcion SET estado_estadia = 'CERRANDO', version = version + 1 WHERE id = p_recepcion;

    INSERT INTO cierre (
      closure_id, id_recepcion, estado, request_hash, request, cobro_declarado, penalidad,
      medio_pago, cleaning_cycle_id, attempts, next_attempt_at)
    VALUES (
      p_closure, p_recepcion, 'VALIDANDO', v_hash, p_request,
      COALESCE((p_request->>'montoRecibido')::numeric, 0),
      COALESCE((p_request->>'costoPenalidad')::numeric, 0),
      NULLIF(p_request->>'medioPago', ''), gen_random_uuid(), 0, now());

    SELECT room_version INTO v_room FROM room_gate WHERE id_habitacion = v_rec.id_habitacion;
    RETURN jsonb_build_object(
      'ok', true, 'resume', false, 'closureId', p_closure, 'idRecepcion', p_recepcion,
      'estado', 'VALIDANDO', 'roomVersion', COALESCE(v_room, 0));
  ELSIF v_rec.estado_estadia = 'CERRANDO' THEN
    RETURN jsonb_build_object('ok', false, 'code', 'CLOSURE_IN_PROGRESS');
  END IF;

  RETURN jsonb_build_object('ok', false, 'code', 'STAY_NOT_ACTIVE');
END;
$$;

-- Rechazo exacto de monto: CAS VALIDANDO -> RECHAZADO y CERRANDO -> ACTIVA, conservando la lápida.
-- Ningún pago remoto se invocó. Repetir sobre un RECHAZADO devuelve la misma decisión.
CREATE OR REPLACE FUNCTION sp_cierre_rechazar_monto(p_closure uuid)
RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_cierre cierre%ROWTYPE;
  v_rec    recepcion%ROWTYPE;
BEGIN
  SELECT * INTO v_cierre FROM cierre WHERE closure_id = p_closure FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;
  IF v_cierre.estado = 'RECHAZADO' THEN
    RETURN jsonb_build_object('ok', true, 'estado', 'RECHAZADO');
  END IF;
  IF v_cierre.estado <> 'VALIDANDO' THEN
    RETURN jsonb_build_object('ok', false, 'code', 'INVALID_STATE');
  END IF;

  SELECT * INTO v_rec FROM recepcion WHERE id = v_cierre.id_recepcion FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;

  UPDATE cierre
     SET estado = 'RECHAZADO', last_error_code = 'AMOUNT_MISMATCH', next_attempt_at = now()
   WHERE closure_id = p_closure;

  IF v_rec.estado_estadia = 'CERRANDO' THEN
    UPDATE recepcion SET estado_estadia = 'ACTIVA', version = version + 1 WHERE id = v_rec.id;
  END IF;

  RETURN jsonb_build_object('ok', true, 'estado', 'RECHAZADO');
END;
$$;

-- CAS VALIDANDO -> CONFIRMANDO con el cálculo validado e inmutable antes del HTTP a sales.
CREATE OR REPLACE FUNCTION sp_cierre_preparar_confirmacion(
  p_closure uuid,
  p_calculo jsonb
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_cierre cierre%ROWTYPE;
BEGIN
  SELECT * INTO v_cierre FROM cierre WHERE closure_id = p_closure FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;
  IF v_cierre.estado IN ('CONFIRMANDO','COMPLETADO') THEN
    RETURN jsonb_build_object('ok', true, 'estado', v_cierre.estado, 'calculo', v_cierre.calculo);
  END IF;
  IF v_cierre.estado <> 'VALIDANDO' THEN
    RETURN jsonb_build_object('ok', false, 'code', 'INVALID_STATE');
  END IF;

  PERFORM 1 FROM recepcion WHERE id = v_cierre.id_recepcion FOR UPDATE;

  UPDATE cierre
     SET estado = 'CONFIRMANDO',
         calculo = p_calculo,
         attempts = attempts + 1,
         last_error_code = NULL,
         next_attempt_at = now() + interval '30 seconds'
   WHERE closure_id = p_closure;

  RETURN jsonb_build_object('ok', true, 'estado', 'CONFIRMANDO', 'calculo', p_calculo);
END;
$$;

-- Completar: solo CONFIRMANDO, valida el recibo contra el cálculo congelado y completa la estadía,
-- el gate y deja el ciclo de limpieza. Una vez: devuelve el resultado guardado en reintentos.
CREATE OR REPLACE FUNCTION sp_cierre_completar(
  p_closure uuid,
  p_receipt jsonb
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_cierre  cierre%ROWTYPE;
  v_rec     recepcion%ROWTYPE;
  v_room    bigint;
  v_expected numeric(12,2);
  v_paid     numeric(12,2);
BEGIN
  SELECT * INTO v_cierre FROM cierre WHERE closure_id = p_closure FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;
  IF v_cierre.estado = 'COMPLETADO' THEN
    SELECT room_version INTO v_room FROM room_gate WHERE cleaning_cycle_id = v_cierre.cleaning_cycle_id;
    RETURN jsonb_build_object(
      'ok', true, 'estado', 'COMPLETADO', 'closureId', p_closure,
      'cleaningCycleId', v_cierre.cleaning_cycle_id, 'roomVersion', COALESCE(v_room, 0),
      'alreadyCompleted', true);
  END IF;
  IF v_cierre.estado <> 'CONFIRMANDO' THEN
    RETURN jsonb_build_object('ok', false, 'code', 'INVALID_STATE');
  END IF;

  SELECT * INTO v_rec FROM recepcion WHERE id = v_cierre.id_recepcion FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;

  v_expected := (v_cierre.calculo->>'expectedPendingTotal')::numeric;
  v_paid := (p_receipt->>'totalPagadoAhora')::numeric;
  IF (p_receipt->>'closureId')::uuid <> p_closure
     OR (p_receipt->>'idRecepcion')::int <> v_cierre.id_recepcion
     OR (p_receipt->>'idHotel')::int <> v_rec.id_hotel
     OR v_paid IS NULL OR v_expected IS NULL OR v_paid <> v_expected THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RECEIPT_MISMATCH');
  END IF;

  UPDATE cierre
     SET estado = 'COMPLETADO', receipt_sales = p_receipt, completed_at = now(),
         attempts = attempts + 1, last_error_code = NULL, next_attempt_at = now()
   WHERE closure_id = p_closure;

  UPDATE recepcion
     SET estado_estadia = 'CERRADA',
         fecha_salida_confirmacion = now(),
         precio_restante = 0,
         total_pagado = precio_inicial + v_cierre.penalidad,
         costo_penalidad = v_cierre.penalidad,
         motivo_penalidad = NULLIF(v_cierre.request->>'motivoPenalidad', ''),
         version = version + 1
   WHERE id = v_cierre.id_recepcion;

  UPDATE room_gate
     SET estado = 'LIMPIEZA_PENDIENTE', id_recepcion = NULL,
         cleaning_cycle_id = v_cierre.cleaning_cycle_id, block_id = NULL,
         room_version = room_version + 1, updated_at = now()
   WHERE id_habitacion = v_rec.id_habitacion
  RETURNING room_version INTO v_room;

  RETURN jsonb_build_object(
    'ok', true, 'estado', 'COMPLETADO', 'closureId', p_closure,
    'cleaningCycleId', v_cierre.cleaning_cycle_id, 'roomVersion', COALESCE(v_room, 0),
    'alreadyCompleted', false);
END;
$$;

-- Archivado lógico: solo CERRADA, conserva importes y snapshots.
CREATE OR REPLACE FUNCTION sp_recepcion_archivar(
  p_id      int,
  p_version bigint
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_rec recepcion%ROWTYPE;
BEGIN
  SELECT * INTO v_rec FROM recepcion WHERE id = p_id FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;
  IF v_rec.estado_estadia <> 'CERRADA' THEN
    RETURN jsonb_build_object('ok', false, 'code', 'STAY_NOT_ACTIVE');
  END IF;
  IF v_rec.version <> p_version THEN
    RETURN jsonb_build_object('ok', false, 'code', 'PRECONDITION_FAILED');
  END IF;

  UPDATE recepcion SET archivada = true, version = version + 1 WHERE id = p_id;

  RETURN jsonb_build_object('ok', true, 'version', v_rec.version + 1);
END;
$$;

-- Anulación correctiva del check-in: CAS ACTIVA -> CERRADA archivada, gate a LIBRE con roomVersion++,
-- sin limpieza, recibo ni cobro; auditoría en audit_log.
CREATE OR REPLACE FUNCTION sp_recepcion_anular_checkin(
  p_id          int,
  p_motivo      varchar,
  p_version     bigint,
  p_actor       varchar DEFAULT 'sistema',
  p_correlation uuid    DEFAULT gen_random_uuid()
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_rec   recepcion%ROWTYPE;
  v_room  bigint;
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
  IF EXISTS (SELECT 1 FROM cierre WHERE id_recepcion = p_id AND estado <> 'RECHAZADO') THEN
    RETURN jsonb_build_object('ok', false, 'code', 'CLOSURE_IN_PROGRESS');
  END IF;

  UPDATE recepcion
     SET estado_estadia = 'CERRADA', archivada = true, version = version + 1
   WHERE id = p_id;

  UPDATE room_gate
     SET estado = 'LIBRE', id_recepcion = NULL, cleaning_cycle_id = NULL, block_id = NULL,
         room_version = room_version + 1, updated_at = now()
   WHERE id_habitacion = v_rec.id_habitacion AND id_recepcion = p_id
  RETURNING room_version INTO v_room;

  INSERT INTO audit_log (id, actor, action, resource_id, id_hotel, correlation_id, changes)
  VALUES (gen_random_uuid(), p_actor, 'ANULAR_CHECKIN', p_id::text, v_rec.id_hotel, p_correlation,
          jsonb_build_object('motivo', p_motivo, 'version', p_version));

  RETURN jsonb_build_object(
    'ok', true, 'version', v_rec.version + 1, 'roomVersion', COALESCE(v_room, 0));
END;
$$;
