# IA-back-inventory-stock-02 — Hotel rechaza el token máquina por la audiencia

- **Plan:** `back-inventory-stock`
- **Paso:** 6 y 14 de la sección 4. Pasos (alta de producto y `POST /api/producto/{id}/sedes`)
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-19

## Qué se encontró

Con `hotel-service` ya arreglado y en marcha (`GET /actuator/health/readiness` → 200, Flyway V1–V4 en
`success`), el alta de producto sigue fallando con 503 `DEPENDENCY_UNAVAILABLE`. La causa es el
Feign saliente `GET /internal/hoteles/{id}`: hotel responde **401 `UNAUTHENTICATED`** al token
máquina que inventory obtiene de identity.

La cadena, reproducida en este orden:

```text
1) allowlist sembrada por identity (identity_db):
   inventory-service -> {"hotel": ["hoteles:read"]}

2) identity emite el token con audience=hotel:
   {"sub":"inventory-service","aud":"hotel","scope":"hoteles:read","iss":"hotel-identity"}

3) hotel, con ese token:
   GET http://localhost:8082/internal/hoteles/1
   HTTP 401 {"error":{"code":"UNAUTHENTICATED"}}

4) identity con audience=hotel-service (lo que hotel espera):
   HTTP 400 {"error":{"code":"VALIDATION_ERROR","fields":{"audience":"destino no autorizado para este cliente"}}}
```

El origen es una discrepancia de nombres: `docs/especificacion/03-contrato-comun.md` § Servicio a
servicio y `docs/contracts/openapi/identity.yaml` fijan `aud=<destino>` con destinos cortos
(`hotel`, `reception`, `sales`, `inventory`), y la allowlist sembrada por `back-identity-personas`
usa esos nombres; pero `hotel-service` valida `Set.of("hotel-api", spring.application.name)` y
`spring.application.name` es `hotel-service`. El mismo patrón existe en `reception-service` e
`identity-service`, así que afecta a **todas** las llamadas máquina entre servicios, no solo a
inventory.

## Qué se decidió

No se toca `hotel-service` (ni `identity-service`) ni se reconfigura su arranque para esquivarlo
(por ejemplo, forzando `spring.application.name=hotel`) porque produciría evidencia que no
representa `main`. Se dejan sin marcar los pasos 6 y 14 y el plan queda bloqueado, con la
reproducción anterior.

## Por qué

La norma limita la zona de escritura de un plan `back` a `services/<servicio>-service/**` del propio
servicio y la campaña prohíbe tocar servicios ajenos. La corrección real es de contrato/plataforma:
o hotel (y reception/identity) aceptan el destino corto que emite identity, o identity y todos los
llamadores pasan al nombre largo —y entonces hay que ajustar también la allowlist y el contrato—.
Ninguna de las dos cabe en este plan.

## Alternativas descartadas

- **Cambiar `HotelClient` para pedir `audience=hotel-service`.** Identity responde 400 porque ese
  destino no está en la allowlist del cliente `inventory-service`; no depende de este plan.
- **Arrancar hotel con `SPRING_APPLICATION_NAME=hotel`.** Haría pasar la prueba alterando la
  configuración del servicio ajeno y su nombre en Eureka; es exactamente el tipo de evidencia
  fabricada que la campaña prohíbe.
- **Sustituir el Feign por un stub o un token humano.** Prohibido por la campaña; el token humano
  tampoco tiene `scope` y hotel lo rechazaría en `/internal/**`.

## A quién afecta

- `inventory-service` (pasos 6 y 14 de este plan) y, por el mismo motivo, cualquier llamada máquina
  de `reception-service`, `identity-service`, `hotel-service` y `sales-service` a `/internal/**`.
- El arreglo corresponde a un plan de arreglo o a un PR de plataforma que unifique la audiencia
  (`aud`) entre identity, la allowlist sembrada y los validadores de cada servicio.

## Resolución (2026-10-20)

El plan de arreglo [`back-arreglo-audiencia-maquina`](../../plans/back-arreglo-audiencia-maquina.md)
(PR #79, ya en `main`) alineó los siete validadores con la spec: aceptan el destino corto que emite
identity (`aud=hotel`) además del nombre largo. Verificado en esta rama con el token máquina real de
`inventory-service`: `GET /internal/hoteles/{id}` responde 200 con `aud=hotel` y `scope=hoteles:read`.

Al reejecutar el alta aparecieron dos defectos propios de `inventory-service`, ya corregidos: la
secuencia de `producto` no se ajustaba tras los seeds con id explícito (migración `V5`) y
`MachineTokenProvider` exigía el client id completo mientras `HotelClient` pasa el destino corto
`inventory` (ahora acepta corto y completo y autentica con las credenciales configuradas). Con eso,
`POST /api/producto/registrar` y `POST /api/producto/{id}/sedes` responden 201.
