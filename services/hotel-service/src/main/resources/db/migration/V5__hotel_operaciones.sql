-- Operaciones administrativas y ciclo de limpieza de habitación (hotel_db).
-- DDL de referencia: docs/especificacion/microservicios/hotel/base-de-datos.md#ddl-de-referencia
-- La migración es aditiva: no toca las tablas de catálogo de F2.
-- IA: el plan lo llama «V2»; el historial ya tiene V1..V4, así que esta es V5
-- (docs/implementacion/decisiones/IA-back-hotel-operaciones-01.md).

CREATE TABLE limpieza (
  cleaning_cycle_id uuid PRIMARY KEY,
  id_habitacion     int         NOT NULL REFERENCES habitacion(id),
  id_recepcion      int         NOT NULL,
  room_version      bigint      NOT NULL,
  estado            varchar(20) NOT NULL CHECK (estado IN ('PENDIENTE','COMPLETADA')),
  created_at        timestamptz NOT NULL DEFAULT now(),
  completed_at      timestamptz,
  version           bigint      NOT NULL DEFAULT 1,
  CHECK (estado <> 'COMPLETADA' OR completed_at IS NOT NULL)
);
CREATE UNIQUE INDEX uq_limpieza_pendiente ON limpieza (id_habitacion) WHERE estado = 'PENDIENTE';
CREATE INDEX ix_limpieza_habitacion_estado ON limpieza (id_habitacion, estado);

CREATE TABLE operacion_habitacion (
  block_id        uuid PRIMARY KEY,
  id_habitacion   int         NOT NULL REFERENCES habitacion(id),
  tipo            varchar(20) NOT NULL CHECK (tipo IN ('MANTENIMIENTO','BAJA')),
  estado          varchar(20) NOT NULL CHECK (estado IN ('PREPARANDO','APLICADA','LIBERADA','RECHAZADA')),
  request         jsonb       NOT NULL,
  attempts        int         NOT NULL DEFAULT 0,
  next_attempt_at timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_operacion_vigente ON operacion_habitacion (id_habitacion)
  WHERE estado IN ('PREPARANDO','APLICADA');
CREATE INDEX ix_operacion_recuperacion ON operacion_habitacion (estado, next_attempt_at);

-- Solicitud de limpieza: inbox/efecto en la misma transacción del consumidor.
-- Orden de locks: habitación → tarea (nunca al revés).
-- Duplicados y ciclos viejos devuelven ok sin efecto; la contradicción de tupla se rechaza.
CREATE OR REPLACE FUNCTION sp_limpieza_solicitar(
  p_ciclo      uuid,
  p_habitacion int,
  p_recepcion  int,
  p_room_version bigint
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_hab      habitacion%ROWTYPE;
  v_tarea    limpieza%ROWTYPE;
  v_existe   limpieza%ROWTYPE;
BEGIN
  SELECT * INTO v_hab FROM habitacion WHERE id = p_habitacion FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;

  -- Mismo ciclo ya conocido: duplicado (aunque esté completado) sin reabrir nada.
  SELECT * INTO v_existe FROM limpieza WHERE cleaning_cycle_id = p_ciclo;
  IF FOUND THEN
    IF v_existe.id_habitacion <> p_habitacion OR v_existe.id_recepcion <> p_recepcion THEN
      RETURN jsonb_build_object('ok', false, 'code', 'TUPLE_MISMATCH');
    END IF;
    RETURN jsonb_build_object(
      'ok', true, 'created', false, 'duplicate', true,
      'cleaningCycleId', v_existe.cleaning_cycle_id,
      'idHabitacion', v_existe.id_habitacion,
      'idRecepcion', v_existe.id_recepcion,
      'roomVersion', v_existe.room_version,
      'version', v_existe.version,
      'estado', v_existe.estado);
  END IF;

  -- Una sola tarea PENDIENTE por habitación (uq_limpieza_pendiente).
  SELECT * INTO v_tarea
    FROM limpieza
   WHERE id_habitacion = p_habitacion AND estado = 'PENDIENTE'
   FOR UPDATE;

  IF FOUND THEN
    IF v_tarea.cleaning_cycle_id = p_ciclo THEN
      RETURN jsonb_build_object(
        'ok', true, 'created', false, 'duplicate', true,
        'cleaningCycleId', v_tarea.cleaning_cycle_id,
        'idHabitacion', v_tarea.id_habitacion,
        'idRecepcion', v_tarea.id_recepcion,
        'roomVersion', v_tarea.room_version,
        'version', v_tarea.version,
        'estado', v_tarea.estado);
    END IF;
    -- Un ciclo con roomVersion anterior o igual al último aplicado no reemplaza al vigente.
    IF p_room_version <= v_hab.last_cleaning_room_version THEN
      RETURN jsonb_build_object(
        'ok', true, 'created', false, 'ignored', true,
        'cleaningCycleId', p_ciclo,
        'idHabitacion', p_habitacion,
        'idRecepcion', p_recepcion,
        'roomVersion', p_room_version);
    END IF;
    -- Ciclo más nuevo: reemplaza la tarea pendiente anterior.
    DELETE FROM limpieza WHERE cleaning_cycle_id = v_tarea.cleaning_cycle_id;
  ELSE
    -- Sin tarea pendiente: un ciclo viejo (o el mismo roomVersion ya aplicado) se ignora.
    IF p_room_version <= v_hab.last_cleaning_room_version THEN
      RETURN jsonb_build_object(
        'ok', true, 'created', false, 'ignored', true,
        'cleaningCycleId', p_ciclo,
        'idHabitacion', p_habitacion,
        'idRecepcion', p_recepcion,
        'roomVersion', p_room_version);
    END IF;
  END IF;

  INSERT INTO limpieza (cleaning_cycle_id, id_habitacion, id_recepcion, room_version, estado, version)
  VALUES (p_ciclo, p_habitacion, p_recepcion, p_room_version, 'PENDIENTE', 1)
  RETURNING * INTO v_tarea;

  UPDATE habitacion
     SET last_cleaning_room_version = p_room_version,
         id_estado_habitacion = 2
   WHERE id = p_habitacion;

  RETURN jsonb_build_object(
    'ok', true, 'created', true, 'duplicate', false,
    'cleaningCycleId', v_tarea.cleaning_cycle_id,
    'idHabitacion', v_tarea.id_habitacion,
    'idRecepcion', v_tarea.id_recepcion,
    'roomVersion', v_tarea.room_version,
    'version', v_tarea.version,
    'estado', v_tarea.estado);
END;
$$;

-- Completado de limpieza: lock habitación → tarea; valida ciclo/habitación/version de tarea;
-- PENDIENTE → COMPLETADA, físico LISTA e incremento de ambas versiones.
CREATE OR REPLACE FUNCTION sp_limpieza_completar(
  p_ciclo      uuid,
  p_habitacion int,
  p_version    bigint
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_hab   habitacion%ROWTYPE;
  v_tarea limpieza%ROWTYPE;
BEGIN
  SELECT * INTO v_hab FROM habitacion WHERE id = p_habitacion FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;

  SELECT * INTO v_tarea
    FROM limpieza
   WHERE cleaning_cycle_id = p_ciclo
   FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;
  IF v_tarea.id_habitacion <> p_habitacion THEN
    RETURN jsonb_build_object('ok', false, 'code', 'TUPLE_MISMATCH');
  END IF;
  IF v_tarea.version <> p_version THEN
    RETURN jsonb_build_object('ok', false, 'code', 'PRECONDITION_FAILED', 'version', v_tarea.version);
  END IF;
  IF v_tarea.estado <> 'PENDIENTE' THEN
    RETURN jsonb_build_object('ok', false, 'code', 'TASK_NOT_PENDING', 'estado', v_tarea.estado);
  END IF;

  UPDATE limpieza
     SET estado = 'COMPLETADA',
         completed_at = now(),
         version = version + 1
   WHERE cleaning_cycle_id = p_ciclo
  RETURNING * INTO v_tarea;

  UPDATE habitacion
     SET id_estado_habitacion = 1,
         version = version + 1,
         last_cleaning_room_version = v_tarea.room_version
   WHERE id = p_habitacion
  RETURNING * INTO v_hab;

  RETURN jsonb_build_object(
    'ok', true,
    'cleaningCycleId', v_tarea.cleaning_cycle_id,
    'idHabitacion', v_tarea.id_habitacion,
    'idRecepcion', v_tarea.id_recepcion,
    'roomVersion', v_tarea.room_version,
    'version', v_tarea.version,
    'estado', v_tarea.estado,
    'completedAt', v_tarea.completed_at,
    'habitacionVersion', v_hab.version,
    'estadoFisico', 'LISTA');
END;
$$;

-- Aplicación local del bloqueo: solo después de que reception admitió el POST idempotente.
-- CAS PREPARANDO → APLICADA; LIBERADA/RECHAZADA no se reaplican.
CREATE OR REPLACE FUNCTION sp_habitacion_aplicar_bloqueo(
  p_block uuid,
  p_tipo  varchar
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_op  operacion_habitacion%ROWTYPE;
  v_hab habitacion%ROWTYPE;
BEGIN
  SELECT * INTO v_op FROM operacion_habitacion WHERE block_id = p_block FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;
  IF v_op.tipo <> p_tipo THEN
    RETURN jsonb_build_object('ok', false, 'code', 'TUPLE_MISMATCH');
  END IF;

  SELECT * INTO v_hab FROM habitacion WHERE id = v_op.id_habitacion FOR UPDATE;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;

  IF v_op.estado = 'APLICADA' THEN
    RETURN jsonb_build_object(
      'ok', true, 'alreadyApplied', true,
      'blockId', v_op.block_id,
      'idHabitacion', v_op.id_habitacion,
      'idHotel', v_hab.id_hotel,
      'tipo', v_op.tipo,
      'estado', v_op.estado,
      'habitacionVersion', v_hab.version,
      'estadoFisico', (SELECT codigo FROM estado_habitacion WHERE id = v_hab.id_estado_habitacion));
  END IF;
  IF v_op.estado <> 'PREPARANDO' THEN
    RETURN jsonb_build_object('ok', false, 'code', 'OPERATION_NOT_APPLICABLE', 'estado', v_op.estado);
  END IF;

  UPDATE operacion_habitacion
     SET estado = 'APLICADA'
   WHERE block_id = p_block AND estado = 'PREPARANDO'
  RETURNING * INTO v_op;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'OPERATION_NOT_APPLICABLE');
  END IF;

  IF p_tipo = 'MANTENIMIENTO' THEN
    UPDATE habitacion
       SET id_estado_habitacion = 3,
           version = version + 1
     WHERE id = v_op.id_habitacion
    RETURNING * INTO v_hab;
  ELSE
    UPDATE habitacion
       SET estado = false,
           version = version + 1
     WHERE id = v_op.id_habitacion
    RETURNING * INTO v_hab;
  END IF;

  RETURN jsonb_build_object(
    'ok', true, 'alreadyApplied', false,
    'blockId', v_op.block_id,
    'idHabitacion', v_op.id_habitacion,
    'idHotel', v_hab.id_hotel,
    'tipo', v_op.tipo,
    'estado', v_op.estado,
    'habitacionVersion', v_hab.version,
    'estadoFisico', (SELECT codigo FROM estado_habitacion WHERE id = v_hab.id_estado_habitacion));
END;
$$;

-- Habilitación correlacionada: exige operación APLICADA; deja estado=true y físico LISTA.
-- En una BAJA valida sede/piso/categoría activos con el orden de locks del catálogo.
CREATE OR REPLACE FUNCTION sp_habitacion_habilitar(
  p_block uuid
) RETURNS jsonb
LANGUAGE plpgsql AS $$
DECLARE
  v_op       operacion_habitacion%ROWTYPE;
  v_hab      habitacion%ROWTYPE;
  v_hotel    hotel%ROWTYPE;
  v_piso     piso%ROWTYPE;
  v_categoria categoria%ROWTYPE;
BEGIN
  SELECT * INTO v_op FROM operacion_habitacion WHERE block_id = p_block;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;
  IF v_op.estado <> 'APLICADA' THEN
    RETURN jsonb_build_object('ok', false, 'code', 'OPERATION_NOT_APPLICABLE', 'estado', v_op.estado);
  END IF;

  SELECT * INTO v_hab FROM habitacion WHERE id = v_op.id_habitacion;
  IF NOT FOUND THEN
    RETURN jsonb_build_object('ok', false, 'code', 'RESOURCE_NOT_FOUND');
  END IF;

  -- Reactivación de baja: lock de padres en orden hotel → categoría → piso, y comprobación
  -- de que siguen activos. La operación se bloquea después, como manda la disciplina local.
  IF v_op.tipo = 'BAJA' THEN
    SELECT * INTO v_hotel FROM hotel WHERE id = v_hab.id_hotel FOR UPDATE;
    IF NOT FOUND THEN
      RETURN jsonb_build_object('ok', false, 'code', 'PARENT_INACTIVE');
    END IF;
    SELECT * INTO v_categoria FROM categoria WHERE id = v_hab.id_categoria FOR UPDATE;
    IF NOT FOUND THEN
      RETURN jsonb_build_object('ok', false, 'code', 'PARENT_INACTIVE');
    END IF;
    SELECT * INTO v_piso FROM piso WHERE id = v_hab.id_piso FOR UPDATE;
    IF NOT FOUND THEN
      RETURN jsonb_build_object('ok', false, 'code', 'PARENT_INACTIVE');
    END IF;
    IF NOT v_hotel.estado OR NOT v_categoria.estado OR NOT v_piso.estado THEN
      RETURN jsonb_build_object('ok', false, 'code', 'PARENT_INACTIVE');
    END IF;
  END IF;

  SELECT * INTO v_op FROM operacion_habitacion WHERE block_id = p_block FOR UPDATE;
  IF v_op.estado <> 'APLICADA' THEN
    RETURN jsonb_build_object('ok', false, 'code', 'OPERATION_NOT_APPLICABLE', 'estado', v_op.estado);
  END IF;

  UPDATE operacion_habitacion
     SET estado = 'LIBERADA'
   WHERE block_id = p_block AND estado = 'APLICADA'
  RETURNING * INTO v_op;

  UPDATE habitacion
     SET estado = true,
         id_estado_habitacion = 1,
         version = version + 1
   WHERE id = v_op.id_habitacion
  RETURNING * INTO v_hab;

  RETURN jsonb_build_object(
    'ok', true,
    'blockId', v_op.block_id,
    'idHabitacion', v_op.id_habitacion,
    'idHotel', v_hab.id_hotel,
    'tipo', v_op.tipo,
    'estado', v_op.estado,
    'habitacionVersion', v_hab.version,
    'estadoFisico', 'LISTA');
END;
$$;
