-- Esquema de reporting_db: read model de Kafka.
-- Fuente: docs/especificacion/microservicios/reporting/base-de-datos.md § DDL de referencia.
-- Sin outbox ni api_idempotency: reporting solo consulta y su replay es operacional offline.
-- Sin FK entre proyecciones de dominios distintos (los snapshots llegan por topics distintos).

CREATE TABLE persona_projection (
  id_persona      int PRIMARY KEY,
  nombre          varchar(100) NOT NULL,
  apellido        varchar(100) NOT NULL,
  id_tipo_persona int          NOT NULL,
  estado          boolean      NOT NULL,
  source_version  bigint       NOT NULL
);

CREATE TABLE hotel_projection (
  id_hotel       int PRIMARY KEY,
  nombre         varchar(120) NOT NULL,
  ciudad         varchar(80)  NOT NULL,
  estado         boolean      NOT NULL,
  source_version bigint       NOT NULL
);

CREATE TABLE habitacion_projection (
  id_habitacion    int PRIMARY KEY,
  id_hotel         int           NOT NULL,
  numero           varchar(10)   NOT NULL,
  categoria_nombre varchar(80)   NOT NULL,
  precio           numeric(12,2) NOT NULL,
  estado           boolean       NOT NULL,
  estado_fisico    varchar(24)   NOT NULL,
  ocupada          boolean,
  hotel_version    bigint        NOT NULL,
  room_version     bigint        NOT NULL
);

CREATE TABLE gate_projection (
  id_habitacion     int PRIMARY KEY,
  estado            varchar(24) NOT NULL CHECK (estado IN ('LIBRE','OCUPADA','LIMPIEZA_PENDIENTE','BLOQUEADA')),
  cleaning_cycle_id uuid,
  block_id          uuid,
  hotel_version     bigint      NOT NULL,
  room_version      bigint      NOT NULL
);

CREATE TABLE recepcion_projection (
  id_recepcion        int PRIMARY KEY,
  id_hotel            int           NOT NULL,
  id_habitacion       int           NOT NULL,
  id_cliente          int           NOT NULL,
  numero              varchar(10)   NOT NULL,
  nombre_cliente      varchar(200)  NOT NULL,
  fecha_entrada       date          NOT NULL,
  fecha_salida        date          NOT NULL,
  fecha_cierre        timestamptz,
  estado_estadia      varchar(16)   NOT NULL CHECK (estado_estadia IN ('ACTIVA','CERRANDO','CERRADA')),
  precio_inicial      numeric(12,2) NOT NULL,
  adelanto            numeric(12,2) NOT NULL,
  penalidad           numeric(12,2) NOT NULL,
  total_alojamiento   numeric(12,2),
  total_consumos      numeric(12,2),
  total_general       numeric(12,2),
  cobro_ahora         numeric(12,2),
  medio_pago_adelanto varchar(12),
  medio_pago_cierre   varchar(12),
  closure_id          uuid,
  archivada           boolean       NOT NULL,
  source_version      bigint        NOT NULL,
  CHECK (estado_estadia <> 'CERRADA' OR (total_alojamiento IS NOT NULL AND total_consumos IS NOT NULL
    AND total_general IS NOT NULL AND cobro_ahora IS NOT NULL))
);
CREATE INDEX ix_recepcion_projection_cierre ON recepcion_projection (id_hotel, fecha_cierre);

CREATE TABLE stock_projection (
  id_hotel       int           NOT NULL,
  id_producto    int           NOT NULL,
  nombre         varchar(120)  NOT NULL,
  precio         numeric(12,2) NOT NULL,
  cantidad       int           NOT NULL,
  umbral         int           NOT NULL,
  estado         boolean       NOT NULL,
  source_version bigint        NOT NULL,
  PRIMARY KEY (id_hotel, id_producto)
);
CREATE INDEX ix_stock_projection_cantidad ON stock_projection (id_hotel, cantidad);

CREATE TABLE venta_projection (
  id_venta         int PRIMARY KEY,
  id_hotel         int           NOT NULL,
  id_recepcion     int           NOT NULL,
  estado           varchar(12)   NOT NULL,
  estado_operacion varchar(24)   NOT NULL,
  total            numeric(12,2) NOT NULL,
  medio_pago       varchar(12),
  created_at       timestamptz   NOT NULL,
  paid_at          timestamptz,
  source_version   bigint        NOT NULL
);
CREATE INDEX ix_venta_projection_paid ON venta_projection (id_hotel, paid_at);

CREATE TABLE venta_detalle_projection (
  id_venta    int           NOT NULL REFERENCES venta_projection(id_venta),
  id_producto int           NOT NULL,
  nombre      varchar(120)  NOT NULL,
  cantidad    int           NOT NULL,
  precio      numeric(12,2) NOT NULL,
  subtotal    numeric(12,2) NOT NULL,
  PRIMARY KEY (id_venta, id_producto)
);

CREATE TABLE limpieza_completada_projection (
  cleaning_cycle_id     uuid PRIMARY KEY,
  id_habitacion         int         NOT NULL,
  cleaning_room_version bigint      NOT NULL,
  hotel_version         bigint      NOT NULL,
  completed_at          timestamptz NOT NULL
);

CREATE TABLE projection_checkpoint (
  topic         varchar(120) NOT NULL,
  partition     int          NOT NULL,
  last_offset   bigint       NOT NULL DEFAULT -1,
  last_event_at timestamptz,
  updated_at    timestamptz  NOT NULL DEFAULT now(),
  degraded      boolean      NOT NULL DEFAULT false,
  PRIMARY KEY (topic, partition)
);

CREATE TABLE projection_bootstrap (
  id           int PRIMARY KEY CHECK (id = 1),
  estado       varchar(16) NOT NULL CHECK (estado IN ('CARGANDO','LISTO')),
  manifest     jsonb       NOT NULL,
  completed_at timestamptz
);

-- Inbox común (docs/especificacion/03-contrato-comun.md § Persistencia transversal): se escribe
-- en la misma transacción del efecto para deduplicar por (consumer, message_id).
CREATE TABLE inbox (
  consumer     varchar(80) NOT NULL,
  message_id   uuid        NOT NULL,
  processed_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (consumer, message_id)
);

-- ---------------------------------------------------------------------------
-- Funciones de reporte (docs/especificacion/microservicios/reporting/base-de-datos.md
-- § Funciones SQL). Devuelven filas; la paginación se aplica al consultar.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_reporte_productos_bajo_stock(p_hotel int, p_limite int)
RETURNS TABLE(id_producto int, nombre_producto text, cantidad int, precio numeric, estado boolean)
LANGUAGE sql STABLE AS $$
  SELECT s.id_producto, s.nombre::text, s.cantidad, s.precio, s.estado
  FROM stock_projection s
  WHERE s.id_hotel = p_hotel AND s.estado AND s.cantidad <= p_limite
  ORDER BY s.id_producto
$$;

CREATE OR REPLACE FUNCTION fn_reporte_ventas(p_hotel int, p_inicio timestamptz, p_fin timestamptz)
RETURNS TABLE(id_producto int, nombre_producto text, cantidad_total bigint, total_ingresado numeric)
LANGUAGE sql STABLE AS $$
  SELECT d.id_producto, min(d.nombre)::text, sum(d.cantidad)::bigint, sum(d.subtotal)
  FROM venta_projection v
  JOIN venta_detalle_projection d ON d.id_venta = v.id_venta
  WHERE v.id_hotel = p_hotel
    AND v.estado = 'PAGADO'
    AND v.paid_at >= p_inicio
    AND v.paid_at < p_fin
  GROUP BY d.id_producto
  ORDER BY d.id_producto
$$;

CREATE OR REPLACE FUNCTION fn_reporte_ocupacion(p_hotel int, p_inicio timestamptz, p_fin timestamptz)
RETURNS TABLE(id_habitacion int, numero_habitacion text, descripcion_categoria text, veces_alquilada bigint)
LANGUAGE sql STABLE AS $$
  SELECT h.id_habitacion,
         h.numero::text,
         h.categoria_nombre::text,
         count(r.id_recepcion)::bigint
  FROM habitacion_projection h
  LEFT JOIN recepcion_projection r
    ON r.id_habitacion = h.id_habitacion
   AND r.id_hotel = h.id_hotel
   AND r.fecha_entrada >= (p_inicio AT TIME ZONE 'America/Lima')::date
   AND r.fecha_entrada <  (p_fin AT TIME ZONE 'America/Lima')::date
  WHERE h.id_hotel = p_hotel
  GROUP BY h.id_habitacion, h.numero, h.categoria_nombre
  ORDER BY h.id_habitacion
$$;

CREATE OR REPLACE FUNCTION fn_reporte_cobros(p_hotel int, p_inicio timestamptz, p_fin timestamptz)
RETURNS TABLE(id_recepcion int, numero_habitacion text, nombre_cliente text,
              total_alojamiento numeric, total_consumos numeric, total_general numeric,
              medio_pago_adelanto varchar, medio_pago_cierre varchar, fecha_cierre timestamptz)
LANGUAGE sql STABLE AS $$
  SELECT r.id_recepcion,
         r.numero::text,
         r.nombre_cliente::text,
         r.total_alojamiento,
         r.total_consumos,
         r.total_general,
         r.medio_pago_adelanto,
         r.medio_pago_cierre,
         r.fecha_cierre
  FROM recepcion_projection r
  WHERE r.id_hotel = p_hotel
    AND r.estado_estadia = 'CERRADA'
    AND r.fecha_cierre IS NOT NULL
    AND r.fecha_cierre >= p_inicio
    AND r.fecha_cierre < p_fin
  ORDER BY r.fecha_cierre, r.id_recepcion
$$;

CREATE OR REPLACE FUNCTION fn_dashboard(p_hoteles int[], p_dia date)
RETURNS jsonb
LANGUAGE sql STABLE AS $$
  WITH alcance AS (
    SELECT h.id_hotel, h.id_habitacion, h.estado, h.estado_fisico,
           COALESCE(g.estado, 'LIBRE') AS gate
    FROM habitacion_projection h
    LEFT JOIN gate_projection g ON g.id_habitacion = h.id_habitacion
    WHERE p_hoteles IS NULL OR h.id_hotel = ANY (p_hoteles)
  ),
  metricas AS (
    SELECT
      (SELECT count(*) FROM alcance
        WHERE gate = 'OCUPADA') AS ocupadas,
      (SELECT count(*) FROM alcance
        WHERE estado AND estado_fisico = 'LISTA'
          AND gate NOT IN ('OCUPADA','LIMPIEZA_PENDIENTE')) AS disponibles,
      (SELECT count(*) FROM stock_projection s
        WHERE (p_hoteles IS NULL OR s.id_hotel = ANY (p_hoteles))
          AND s.estado AND s.cantidad <= 5) AS bajo_stock,
      (SELECT COALESCE(sum(r.adelanto), 0) FROM recepcion_projection r
        WHERE (p_hoteles IS NULL OR r.id_hotel = ANY (p_hoteles))
          AND r.fecha_entrada = p_dia
          AND (r.estado_estadia <> 'CERRADA' OR r.fecha_cierre IS NOT NULL)) AS adelantos,
      (SELECT COALESCE(sum(r.total_alojamiento - r.adelanto), 0) FROM recepcion_projection r
        WHERE (p_hoteles IS NULL OR r.id_hotel = ANY (p_hoteles))
          AND r.estado_estadia = 'CERRADA'
          AND r.fecha_cierre IS NOT NULL
          AND (r.fecha_cierre AT TIME ZONE 'America/Lima')::date = p_dia) AS cobros_finales,
      (SELECT COALESCE(sum(v.total), 0) FROM venta_projection v
        WHERE (p_hoteles IS NULL OR v.id_hotel = ANY (p_hoteles))
          AND v.estado = 'PAGADO'
          AND v.paid_at IS NOT NULL
          AND (v.paid_at AT TIME ZONE 'America/Lima')::date = p_dia) AS ventas
  )
  SELECT jsonb_build_object(
    'habitacionesOcupadas', ocupadas,
    'habitacionesDisponibles', disponibles,
    'productosBajoStock', bajo_stock,
    'ingresosHoy', round(adelantos + cobros_finales + ventas, 2),
    'moneda', 'PEN'
  )
  FROM metricas
$$;
