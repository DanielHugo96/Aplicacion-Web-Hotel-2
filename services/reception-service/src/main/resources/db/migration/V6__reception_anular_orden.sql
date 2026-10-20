-- Orden de rechazos de la anulación correctiva (back-reception-cierre).
-- La anulación distingue el cierre vigente (CLOSURE_IN_PROGRESS) antes de STAY_NOT_ACTIVE,
-- tal como fija docs/contracts/openapi/reception.yaml y reception/api.md § Anular check-in.
-- Se mantiene la misma firma y el resto de invariantes de V5.

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
  IF v_rec.version <> p_version THEN
    RETURN jsonb_build_object('ok', false, 'code', 'PRECONDITION_FAILED');
  END IF;
  IF EXISTS (SELECT 1 FROM cierre WHERE id_recepcion = p_id AND estado <> 'RECHAZADO') THEN
    RETURN jsonb_build_object('ok', false, 'code', 'CLOSURE_IN_PROGRESS');
  END IF;
  IF v_rec.estado_estadia <> 'ACTIVA' THEN
    RETURN jsonb_build_object('ok', false, 'code', 'STAY_NOT_ACTIVE');
  END IF;
  IF EXISTS (SELECT 1 FROM operacion_consumo WHERE id_recepcion = p_id AND estado = 'ABIERTA') THEN
    RETURN jsonb_build_object('ok', false, 'code', 'OPERATIONS_IN_PROGRESS');
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
