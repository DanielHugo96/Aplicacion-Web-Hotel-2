-- Tabla técnica de auditoría (convención común de plataforma).
--
-- Comprobado en el paso 1 del plan back-identity-auth: api_idempotency (V1) y
-- outbox/inbox (V2) ya venían de back-plataforma-comunes/mensajeria; audit_log faltaba,
-- así que se añade aquí desde el DDL común. No duplica ninguna otra migración.
-- DDL de referencia: docs/especificacion/03-contrato-comun.md § Persistencia transversal.
--
-- Nunca guarda claves, hashes ni tokens; en PERSONA_SEARCH/PERSONA_READ el `changes`
-- va sin documento ni correo.
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

CREATE INDEX ix_audit_log_recurso ON audit_log (action, resource_id, occurred_at);