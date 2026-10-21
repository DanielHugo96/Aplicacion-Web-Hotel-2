# Spec Delta

## MODIFIED Requirements

### Requirement: Operaciones observables con recuperación cada 30 s

El sistema SHALL exponer cada operación VENTA, PAGO y ANULACION para polling, mantener una sola operación no terminal por venta, y recuperar cada 30 s: la venta PENDIENTE con operación VENTA en PREPARANDO de más de 120 s se rechaza; una operación PAGO o ANULACION en PREPARANDO se reintenta por su `next_attempt_at` con la misma admisión y el mismo CAS; y CANCELACION_PENDIENTE se reintenta con el mismo CAS que la respuesta HTTP. Un fallo de transporte al admitir un PAGO o una ANULACION SHALL dejar la operación en PREPARANDO y responder 202; solo un rechazo 409, 403 o 404 la termina.

#### Scenario: Operación en curso responde 202 con polling

- **WHEN** el resultado remoto es incierto o la operación sigue admitida
- **THEN** el sistema responde 202 con ubicación de la venta y reintento, y la respuesta terminal queda persistida para reintentos
- **AND** la operación sigue no terminal cuando el 202 se debe a un fallo de transporte, para que la recuperación pueda reintentarla

#### Scenario: Pago con reception caído se recupera

- **WHEN** la admisión de un PAGO falla por timeout o 503 y el sistema responde 202
- **THEN** la operación PAGO queda en PREPARANDO y la venta conserva su estado y versión
- **AND** al volver reception, el recuperador reintenta la misma admisión y aplica el pago local, dejando la venta PAGADO con `paid_at` y `medio_pago`
- **AND** el pago no genera compensación ni movimiento de inventario

#### Scenario: Rechazo de admisión termina la operación

- **WHEN** reception rechaza la admisión de un PAGO o de una ANULACION con 409, 403 o 404
- **THEN** el sistema marca la operación TERMINAL y devuelve el error correspondiente
- **AND** no la reintenta ni aplica el pago o la anulación

#### Scenario: Recuperador compensa sin confirmar tras compensar

- **WHEN** el trabajo cada 30 s encuentra PENDIENTE con débito solicitado y edad mayor a 120 s
- **THEN** el sistema la pasa a CANCELACION_PENDIENTE con restitución y nunca la confirma después por antigüedad

#### Scenario: Anulación en PREPARANDO reanuda su CAS

- **WHEN** una ANULACION queda en PREPARANDO por un fallo de transporte
- **THEN** el recuperador reintenta su admisión y, si procede, su CAS a CANCELACION_PENDIENTE con `stock.restituir`
- **AND** si la venta ya está CANCELADA, termina la operación sin volver a admitirla; si está en CANCELACION_PENDIENTE, no vuelve a admitirla y deja la restitución al flujo existente
