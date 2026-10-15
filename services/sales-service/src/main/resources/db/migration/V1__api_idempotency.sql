-- Tabla técnica de idempotencia REST (convención común).
-- DDL de referencia: docs/especificacion/03-contrato-comun.md § Persistencia transversal.
CREATE TABLE api_idempotency (
  actor        varchar(100) NOT NULL,
  method       varchar(8)   NOT NULL,
  route        varchar(160) NOT NULL,
  key          uuid         NOT NULL,
  request_hash char(64)     NOT NULL,
  status       varchar(16)  NOT NULL CHECK (status IN ('EN_CURSO','COMPLETADA')),
  resource_id  varchar(80),
  http_status  int,
  response     jsonb,
  created_at   timestamptz  NOT NULL DEFAULT now(),
  PRIMARY KEY (actor, method, route, key)
);
