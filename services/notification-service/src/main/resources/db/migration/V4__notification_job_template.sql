-- Esquema de notification (docs/especificacion/microservicios/notification/base-de-datos.md
-- § DDL de referencia). Un job por commandId, idempotente por inbox, con lease persistido y
-- reintentos propios del job (nunca de Rabbit) una vez persistido.
CREATE TABLE notification_job (
  command_id       uuid PRIMARY KEY,
  tipo             varchar(40) NOT NULL,
  id_hotel         int,
  id_persona       int         NOT NULL,
  id_recepcion     int,
  destinatario     varchar(254),
  template_version int         NOT NULL,
  variables        jsonb,
  payload_hash     char(64)    NOT NULL,
  estado           varchar(16) NOT NULL CHECK (estado IN ('PENDIENTE','ENVIANDO','ENVIADA','ERROR','DESCARTADO')),
  attempts         int         NOT NULL DEFAULT 0,
  next_attempt_at  timestamptz NOT NULL DEFAULT now(),
  lease_until      timestamptz,
  lease_token      uuid,
  message_id       varchar(160) NOT NULL UNIQUE,
  created_at       timestamptz NOT NULL DEFAULT now(),
  sent_at          timestamptz,
  last_error_code  varchar(80),
  CHECK (estado <> 'ENVIANDO' OR (lease_token IS NOT NULL AND lease_until IS NOT NULL)),
  CHECK ((destinatario IS NOT NULL AND variables IS NOT NULL) OR estado = 'ENVIADA')
);
CREATE INDEX ix_notification_job_estado ON notification_job (estado, next_attempt_at);

CREATE TABLE template (
  tipo      varchar(40)  NOT NULL,
  version   int          NOT NULL,
  subject   varchar(150) NOT NULL,
  body_text text         NOT NULL,
  PRIMARY KEY (tipo, version)
);