# Tasks

Los tasks corresponden a los pasos de `docs/plans/back-arreglo-sales-pago-202.md` §4. Cada uno incluye su forma de verificación.

## 1. Reproducción

- [ ] 1.1 Reproducir el fallo desde `main` con `reception-service` detenido: `PUT /api/venta/{id}` responde 202 con `Location` y `Retry-After: 2`, 30 sondeos de 3 s dejan la venta PENDIENTE/CONFIRMADA y la operación PAGO queda TERMINAL. Verificación: la salida literal y la consulta a `sales_db` quedan en §6 del plan.

## 2. Tratamiento del fallo de admisión

- [ ] 2.1 En `SalesService.pagar`, terminar la operación solo ante 409, 403 o 404; en timeout, 503 o error de red dejarla en PREPARANDO y responder 202. Verificación: una prueba con la admisión simulada como 503 devuelve 202 y la operación sigue PREPARANDO; un 409 sigue devolviendo `STAY_NOT_ACTIVE`.
- [ ] 2.2 Aplicar el mismo tratamiento en `SalesService.anular`. Verificación: una prueba con 503 devuelve 202 y la operación ANULACION sigue PREPARANDO; un 409 de estadía no activa sigue devolviendo su error.

## 3. Recuperación de PAGO y ANULACION

- [ ] 3.1 Separar en `SalesRepository` la selección de operaciones PREPARANDO: VENTA mantiene `v.estado_operacion='PENDIENTE'` y `created_at` mayor a 120 s; PAGO se selecciona con la venta impaga y `next_attempt_at <= now()`; ANULACION con la venta en PENDIENTE o CONFIRMADA y `next_attempt_at <= now()`. Verificación: pruebas de repositorio con una operación PAGO PREPARANDO sobre venta CONFIRMADA y una VENTA vieja; la primera aparece y la segunda conserva el rechazo.
- [ ] 3.2 En `RecuperadorVentas`, enrutar PAGO y ANULACION en PREPARANDO a `reintentarAdmision`, sin rechazar la venta; si la venta ya está PAGADO o CANCELADA, terminar la operación y asegurar el finalizar sin nueva admisión. Verificación: el recuperador paga la venta en el primer ciclo; una venta ya pagada termina la operación sin emitir un segundo `recepcion.operacion-finalizar`.

## 4. Evidencia e integración

- [ ] 4.1 Con `sales-service` reiniciado desde la rama, repetir la reproducción: el 202 y el sondeo de 90 s terminan en PAGADO, con una sola operación PAGO TERMINAL y sin movimiento de inventario. Verificación: el script/§6 del plan sale 0 y la consulta a `sales_db` muestra `estado='PAGADO'`, `paid_at` no nulo y una sola operación PAGO.
- [ ] 4.2 Correr `bash scripts/mvn.sh -f services/pom.xml -pl sales-service test`, `bash scripts/mvn.sh -f services/pom.xml verify`, `python scripts/validar-planes.py`, `python scripts/validar-contratos.py --base origin/main` y `npx openspec validate back-arreglo-sales-pago-202 --strict`. Verificación: pruebas y build en verde, 0 errores del validador, contratos solo ampliados y change válido.
