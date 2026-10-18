# IA-back-identity-auth-03 — Nombres de los secretos de servicio y de la clave HMAC de idempotencia

- **Plan:** `back-identity-auth`
- **Paso:** 2 y 13 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-18

## Qué se encontró

`docs/especificacion/infraestructura/configuracion.md` § Comunes a todos los servicios declara
`hotel.demo.password` ← `DEMO_PASSWORD` y `hotel.security.machine.client-id/secret` ←
`SERVICE_CLIENT_ID`/`SERVICE_CLIENT_SECRET`. La spec **no dice NADA** de:

- los secretos de los cinco clientes de servicio que identity siembra (`service_client` exige
  «credenciales diferentes desde entorno» y solo hay un par de variables documentado);
- la clave del HMAC con el que se calcula el `request_hash` de idempotencia de las rutas con
  credenciales, que 03-contrato-comun.md § Headers exige («usa HMAC si el request contiene
  credenciales») sin nombrar dónde vive.

Además `docs/especificacion/**` es zona bloqueada: el cambio de nombre de variable corresponde a un
PR de spec, no a este plan.

## Qué se decidió

- `hotel.demo.service-client-secrets.<servicio>` con `identity: SERVICE_CLIENT_SECRET`,
  `reception: SERVICE_CLIENT_RECEPTION_SECRET`, `hotel: SERVICE_CLIENT_HOTEL_SECRET`,
  `inventory: SERVICE_CLIENT_INVENTORY_SECRET` y `sales: SERVICE_CLIENT_SALES_SECRET`. Se reaprovecha
  el nombre ya documentado para el secreto propio de identity.
- `hotel.identity.idempotency-hmac-key` ← `IDENTITY_IDEMPOTENCY_HMAC_KEY`, con **predeterminado**
  `${SERVICE_CLIENT_SECRET}`. Ninguna variable nueva es obligatoria: la que se usa por defecto ya es
  obligatoria y vive fuera de Git.
- Si ninguna de las dos está definida, identity genera una clave HMAC efímera por arranque y lo deja
  en el log con un aviso; en un perfil que empiece por `prod` el arranque falla. Ver
  [IA-back-identity-auth-05](IA-back-identity-auth-05.md).

## Por qué

Jerarquía de [`../README.md`](../README.md) §1 aplicada como criterio de least surprise: se reutiliza
el nombre existente siempre que puede, y el nombre nuevo sigue la convención ya escrita
(`hotel.<dominio>.<propiedad>` en kebab-case, variable en SCREAMING_SNAKE_CASE). Reutilizar
`SERVICE_CLIENT_SECRET` como clave HMAC evita que un despliegue nuevo falle por una variable que no
figura en `.env.example`, que además este plan no puede tocar (zona `infra`).

## Alternativas descartadas

- **Pedir un nombre nuevo obligatorio** (`IDENTITY_HMAC_KEY`): añadiría una variable obligatoria que
  no está en `.env.example` ni en la spec, y el plan no puede editar ese archivo.
- **Usar `DEMO_PASSWORD` como clave HMAC**: es de las personas sembradas, disappears en cuanto se
  ejecuta el seeder y es un valor compartido con las cuentas de demo.
- **Guardar el hash con SHA-256 como hace la convención por defecto**: el cuerpo lleva la clave en
  claro, y un hash plano permite recuperarla por fuerza bruta desde la tabla. La spec lo prohíbe.

## A quién afecta

`services/identity-service/src/main/resources/application.yml`, `IdentityProperties`,
`DemoProperties` e `IdentityIdempotencyCustomizer`. Ningún contrato cambia. Pendiente para otro
plan: añadir estas variables a `docs/especificacion/infraestructura/configuracion.md` y a
`.env.example` cuando esa zona vuelva a estar disponible.