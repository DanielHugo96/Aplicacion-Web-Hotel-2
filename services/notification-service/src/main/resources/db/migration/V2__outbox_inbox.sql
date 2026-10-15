-- Tablas técnicas de entrega transaccional (plataforma de mensajería).
-- DDL de referencia: docs/especificacion/03-contrato-comun.md § Persistencia transversal.
-- Una fila de outbox por destino (KAFKA o RABBIT) con su propio published_at; inbox idempotente.
CREATE TABLE outbox (
  id                uuid PRIMARY KEY,
  destination       varchar(8)   NOT NULL CHECK (destination IN ('KAFKA','RABBIT')),
  channel           varchar(160) NOT NULL,
  partition_key     varchar(100),
  aggregate_id      varchar(80)  NOT NULL,
  aggregate_version bigint       NOT NULL,
  type              varchar(80)  NOT NULL,
  payload           jsonb        NOT NULL,
  created_at        timestamptz  NOT NULL DEFAULT now(),
  published_at      timestamptz,
  attempts          int          NOT NULL DEFAULT 0,
  next_attempt_at   timestamptz  NOT NULL DEFAULT now()
);

-- Índice parcial de pendientes (outbox no publicado): sostiene el poller con SKIP LOCKED.
CREATE INDEX ix_outbox_pendiente ON outbox (destination, next_attempt_at) WHERE published_at IS NULL;

CREATE TABLE inbox (
  consumer     varchar(80) NOT NULL,
  message_id   uuid        NOT NULL,
  processed_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (consumer, message_id)
);
