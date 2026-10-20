# IA-back-arreglo-audiencia-maquina-01 — el validador acepta el destino corto, la allowlist no se toca

- **Plan:** `back-arreglo-audiencia-maquina`
- **Paso:** 2 de la sección 4. Pasos (dirección del arreglo y alcance de la corrección)
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-19

## Qué se encontró

La cadena `inventory-service → hotel-service` falla con 401 `UNAUTHENTICATED`. La allowlist de
identity (sembrada por `back-identity-auth`, PR #61) está bien: `inventory-service` tiene
`{"hotel": ["hoteles:read"]}`, y el token que emite
identity lleva `sub=inventory-service`, `aud=hotel` y `scope=hoteles:read`. Lo que falla es el
validador de hotel, que compara `aud` contra `Set.of("hotel-api", spring.application.name)` y
`spring.application.name` es `hotel-service`.

El mismo patrón está en los siete servicios (`identity`, `hotel`, `reception`, `inventory`,
`sales`, `reporting`, `notification`), así que **todas** las llamadas máquina a `/internal/**`
responden 401, no solo la de inventory. `git blame` sitúa la línea en `ea2defe` («aplica la
convención de plataforma común en los siete servicios»), el commit de `back-plataforma-comunes`
(PR #46). `back-hotel-catalogo` (PR #66) y `back-identity-auth` (PR #61) solo consumieron esa
convención; el plan que posee la zona rota es `back-plataforma-comunes`.

## Qué se decidió

1. **Corregir los validadores, no la allowlist.** El destino canónico que fija la spec es el nombre
   corto (`hotel`, `identity`, `reception`, `inventory`, `sales`). Identity ya lo emite y la
   allowlist ya lo contiene; cambiar la allowlist a `hotel-service` contradiría el contrato y
   obligaría a tocar el seed, los ejemplos de `identity.yaml` y los planes cerrados.
2. **Aceptar el destino corto de forma aditiva.** En cada `SecurityConfig`, el conjunto de
   audiencias aceptadas pasa a contener `HUMAN_AUDIENCE` (`hotel-api`), `spring.application.name`
   (`hotel-service`, etc.) y el destino corto derivado de `spring.application.name` quitando el
   sufijo `-service` (`hotel`, etc.). Se mantiene el nombre largo aceptado para no romper la
   evidencia y las pruebas de `back-plataforma-comunes`; el valor canónico que emite identity es el
   corto. Cualquier otra `aud` sigue rechazada.
3. **Un solo plan de arreglo para los siete servicios.** La corrección es una sola convención
   duplicada, no siete cambios de negocio. El plan repara `back-plataforma-comunes`, cuya zona es
   `services/*-service`; hacer un plan por servicio dejaría el mismo fallo en los otros seis y
   multiplicaría los planes de arreglo para el mismo defecto.
4. **No se toca el emisor.** `MachineTokenService`, `TokenIssuer`, `ServiceClientRepository`,
   `DemoDataSeeder`, la migración `V4__identity_personas.sql` y `service_client` se quedan como
   están. Tampoco se añade una propiedad de configuración: el destino corto se deriva del nombre
   de la aplicación, que ya es la fuente del nombre en Eureka.

## Por qué

La jerarquía de [`../README.md`](../README.md) §1 da prioridad a la norma y a la spec. La
[`03-contrato-comun.md` § Servicio a servicio](../../especificacion/03-contrato-comun.md#servicio-a-servicio)
fija `audience=<destino>` y `aud=destino`, y enumera los destinos como `identity`, `hotel`,
`sales`, `reception` e `inventory`. El contrato de identity concreta el ejemplo `audience: hotel`
y el rango `hotel, reception, sales, inventory`. La allowlist sembrada sigue exactamente esa
convención. Por tanto, el lado que se aparta de la spec es el validador, no el emisor.

La opción elegida es la menos invasiva que no rompe ningún plan cerrado ni contrato existente:
añade una audiencia que ya se emite y mantiene las que ya se aceptaban. No cambia ningún schema,
ningún código de error, ninguna migración ni ningún valor de configuración.

## Alternativas descartadas

- **Cambiar la allowlist y los clientes a `audience=hotel-service`.** Contradice los destinos
  cortos de la spec y los ejemplos de `identity.yaml`; además obliga a tocar el seed y la evidencia
  de planes cerrados.
- **Forzar `spring.application.name=hotel` en hotel-service.** Cambia el nombre en Eureka y produce
  evidencia que no representa `main`; ya se descartó en `IA-back-inventory-stock-02`.
- **Aceptar solo el destino corto y dejar de aceptar el nombre largo.** Es más estricto, pero rompe
  las pruebas y la evidencia de `back-plataforma-comunes` sin necesidad; el nombre largo identifica
  al mismo servicio y no abre la puerta a una audiencia ajena.
- **Un plan de arreglo por servicio.** Serían siete planes para una sola convención; el mismo
  fallo quedaría repitiendo el 401 en los otros seis mientras se ejecuta cada uno.

## A quién afecta

- Los siete `SecurityConfig` y sus siete `JwtValidatorTests`.
- `back-inventory-stock`: sus pasos 6 y 14 dejan de fallar con 401 y pueden reanudarse.
- `back-reception-operacion`, `back-hotel-operaciones`, `back-sales-ventas`,
  `back-reception-cierre` y `back-reporting-proyecciones`, y por dependencia los `front-*` y
  `qa-*` que usan rutas `/internal/**`.
- No afecta a `docs/contracts/**`, `docs/especificacion/**`, a la allowlist ni al emisor.
