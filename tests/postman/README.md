# Colecciones Postman

Una sola colección, `Hotel.postman_collection.json`, con todas las rutas del proyecto organizadas
por carpetas, y un entorno sin secretos, `Hotel-local.postman_environment.json`.

| Archivo | Qué es |
|---|---|
| `Hotel.postman_collection.json` | Toda la API. Carpetas `00` a `08`; la `06`–`08` son los tres tramos del ciclo del circuit breaker. |
| `Hotel-local.postman_environment.json` | `baseUrl`, `hotelActuatorUrl`, `correoDemo`, `claveDemo`, `idHotel`, `idCliente`, `idProducto`. Nada más. |

## Puertos

| Puerto | Qué es |
|---|---|
| 8080 | `api-gateway`, único punto de entrada de la API (`{{baseUrl}}`) |
| 8082 | `hotel-service`; su actuator publica el estado del breaker (`{{hotelActuatorUrl}}`) |
| 8081–8087 | identity, hotel, reception, inventory, sales, reporting y notification (solo perfil debug) |
| 8761 | discovery-server |

## Por qué `127.0.0.1` y no `localhost`

`infra/compose.yaml` publica el Gateway solo en IPv4:

```yaml
ports:
  - "127.0.0.1:8080:8080"
```

En Windows `localhost` resuelve primero a `[::1]` (IPv6) y la conexión se rechaza. Por eso el
entorno, las variables de la colección y los ejemplos usan `http://127.0.0.1:8080`.

Si al ejecutar aparece *connection refused* en `[::1]`, en Postman hay seleccionado un entorno
ajeno con otro `baseUrl`. La petición `00.1` lo detecta y muestra el nombre del entorno activo.

## Uso

1. Levanta el stack y espera a que los servicios estén registrados en Eureka.
2. Importa la colección y el entorno.
3. Rellena `claveDemo` con la `DEMO_PASSWORD` de tu `.env` (no se versiona).
4. Ejecuta las carpetas en orden con el Collection Runner, o por CLI:

```bash
# Flujo funcional completo (00 a 05)
npx newman run tests/postman/Hotel.postman_collection.json \
  -e tests/postman/Hotel-local.postman_environment.json

# Ciclo del circuit breaker, tramo 1 (reception arriba)
npx newman run tests/postman/Hotel.postman_collection.json \
  -e tests/postman/Hotel-local.postman_environment.json \
  --folder '06 · Circuit breaker — tramo 1 (reception ARRIBA)'
```

La colección no depende del entorno: todas las variables viven en la propia colección y el entorno
solo aporta los valores de conexión. Así funciona aunque no haya ningún entorno activo.

## Ciclo del circuit breaker

`06`, `07` y `08` son el mismo ciclo partido en tres porque Postman no puede detener servicios.

1. **Tramo 1** (reception arriba): CB-01 a CB-03. Al empezar libera las habitaciones que quedaron
   con operación vigente.
2. **Parada:** `docker compose -f infra/compose.yaml --env-file .env stop reception-service`
3. **Tramo 2** (reception detenido): CB-04 a CB-06. El breaker pasa a `OPEN`.
4. **Reanudación:** `docker compose -f infra/compose.yaml --env-file .env start reception-service`
   y esperar 10 s (`open-state-duration`).
5. **Tramo 3** (reception arriba): CB-07 a CB-10. El breaker cierra y la operación de CB-04 queda
   `APLICADA`.

Cada tramo hace su propio login, resuelve sus ids y, si hace falta, libera el ciclo: se puede
ejecutar suelto, pero no saltarse el orden ni la parada de reception.

## Habitaciones que usa la colección

`00 · Preparación` crea, solo si faltan, las habitaciones `901`…`909` de la sede `{{idHotel}}`
(ciclo del breaker) y la `940` (check-in). Se crean con `POST /api/habitacion/registrar`,
`Idempotency-Key`, el primer piso y la primera categoría de la base, así que funcionan con los seeds
actuales (que solo traen `101`, `102`, `201`, `202`, `301` y `302`).

Una habitación sirve para el ciclo solo si no tiene operación vigente: en
`OperacionHabitacionRepository.existsVigente` cuentan `PREPARANDO` y `APLICADA`, y
`HabitacionService.toView` publica ese `blockId`. Con `blockId` la ruta responde
`409 ROOM_OPERATION_IN_PROGRESS` antes de llegar a reception. Por eso `00.3` completa esos
mantenimientos antes de empezar.

## Diagnóstico

| Síntoma | Causa | Arreglo |
|---|---|---|
| `connection refused` en `[::1]` | Entorno ajeno seleccionado, o `localhost` | Selecciona `Hotel-local`; usa `127.0.0.1` |
| `401 INVALID_CREDENTIALS` | `claveDemo` vacía o distinta de `DEMO_PASSWORD` | Rellénala en el entorno |
| `429 RATE_LIMITED` | identity limita a 5 logins/min por IP+cuenta | Espera un minuto; los tramos conservan el token previo |
| `409 ROOM_OPERATION_IN_PROGRESS` | Habitación con operación `PREPARANDO`/`APLICADA` | Ejecuta `00.3` con reception arriba |
| `409 ROOM_OCCUPIED` | Estadía ACTIVA en la habitación del check-in | Ejecuta `00.3` (anula los check-ins de prueba) |
| `409 IDEMPOTENCY_CONFLICT` | Clave de idempotencia reutilizada con otro payload | `00.1` reinicia las claves en cada corrida |
