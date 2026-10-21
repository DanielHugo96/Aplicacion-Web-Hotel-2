# Comandos canónicos ejecutados — qa-matriz-aceptacion

Salida literal (recortada a las líneas de resultado) de los comandos de la sección «Verificación»
del `README.md` raíz y de los validadores, ejecutados en esta rama el 2026-10-21 sobre el Compose
real. Los registros completos quedan en `_runtime/`.

## 1. Build del backend

```console
$ bash scripts/mvn.sh -f services/pom.xml verify
[INFO] Reactor Summary for Hotel services (agregador) 0.0.1-SNAPSHOT:
[INFO] Hotel services (agregador) ......................... SUCCESS [  0.003 s]
[INFO] identity-service ................................... SUCCESS [ 51.728 s]
[INFO] hotel-service ...................................... SUCCESS [ 22.501 s]
[INFO] reception-service .................................. SUCCESS [01:12 min]
[INFO] inventory-service .................................. SUCCESS [ 54.159 s]
[INFO] sales-service ...................................... SUCCESS [ 18.694 s]
[INFO] reporting-service .................................. SUCCESS [ 18.406 s]
[INFO] notification-service ............................... SUCCESS [ 13.377 s]
[INFO] api-gateway ........................................ SUCCESS [ 41.639 s]
[INFO] discovery-server ................................... SUCCESS [ 15.218 s]
[INFO] BUILD SUCCESS
```

## 2. Apps Angular

```console
$ cd apps/admin-web && npm run build
Application bundle generation complete. [13.9 seconds]
WARNING: bundle initial exceeded maximum budget. Budget 500.00 kB was not met by 12.51 kB with a total of 512.51 kB.
ADMIN_BUILD_EXIT=0

$ cd apps/admin-web && npm test -- --watch=false
 Test Files  49 passed (49)
      Tests  305 passed (305)
ADMIN_TEST_EXIT=0

$ cd apps/admin-web && TZ=UTC npm test -- --watch=false
 Test Files  49 passed (49)
      Tests  305 passed (305)
ADMIN_TEST_UTC_EXIT=0
```

```console
$ cd apps/public-web && npm run build
Application bundle generation complete. [13.699 seconds]
PUBLIC_BUILD_EXIT=0

$ cd apps/public-web && npm test -- --watch=false
 Test Files  10 passed (10)
      Tests  28 passed (28)
PUBLIC_TEST_EXIT=0
```

El único aviso es el presupuesto del bundle inicial de `admin-web` (512.51 kB frente a 500 kB),
que ya existía y no lo introduce este plan (solo toca `tests/` y `scripts/`).

## 3. Infraestructura

```console
$ docker compose -f infra/compose.yaml --env-file .env config --quiet
COMPOSE_CONFIG_EXIT=0

$ docker compose -f infra/compose.yaml --env-file .env ps
kafka      running/healthy
mailpit    running/healthy
postgres   running/healthy
rabbitmq   running/healthy
```

## 4. Validadores

```console
$ python scripts/validar-planes.py
32 plan(es) revisado(s); 32 previsto(s) en el catálogo; 0 error(es), 0 aviso(s).

$ python scripts/validar-contratos.py --base origin/main
Contratos: 8 OpenAPI, 20 schemas de evento. Regla: aditivos frente a origin/main.
Los contratos solo se han ampliado. Correcto.
```

## 5. Corrida de la matriz

```console
$ bash scripts/ejecutar-matriz-completa.sh
=== preflight, rep03, fixtures, rep01, fixtures-avanzados, sec, lp-crud,
    occ-cln, stk, pay-out, msg, not, rep02, rep03-sin-bootstrap, informe ===
matriz completa
```

```console
$ python scripts/qa/matriz.py informe
{
  "casos": 41,
  "conEvidencia": 41,
  "verdes": 41,
  "faltan": []
}
```
