-- Tabla técnica común de auditoría (docs/especificacion/03-contrato-comun.md § Persistencia
-- transversal). La CLI del operador deja aquí sus reintentos, descartes y purgas.
CREATE TABLE audit_log (
  id             uuid PRIMARY KEY,
  actor          varchar(100) NOT NULL,
  action         varchar(80)  NOT NULL,
  resource_id    varchar(80)  NOT NULL,
  id_hotel       int,
  correlation_id uuid         NOT NULL,
  occurred_at    timestamptz  NOT NULL DEFAULT now(),
  changes        jsonb
);