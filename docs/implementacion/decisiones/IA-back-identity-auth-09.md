# IA-back-identity-auth-09 — Eureka: las credenciales van en el userinfo de `EUREKA_URL`

- **Plan:** `back-identity-auth`
- **Paso:** 18 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-18

## Qué se encontró

Para que el caso `SEC02` corriera de punta a punta por el Gateway hizo falta que `identity-service`
se registrara en Eureka. Con la configuración que fija
`docs/especificacion/infraestructura/configuracion.md` § Comunes a todos los servicios
(`eureka.client.username`/`password` ← `EUREKA_USERNAME`/`EUREKA_PASSWORD`, más
`EUREKA_URL=http://localhost:8761/eureka`), **todos** los clientes devolvían
`401` al registrarse, incluido el `api-gateway`, que lleva esa misma configuración desde el plan
`infra-gateway-eureka` (cerrado).

La causa está en el transporte de esta versión de Spring Cloud: los clientes HTTP de Eureka
(`RestTemplateTransportClientFactory` y `WebClientTransportClientFactory`) toman las credenciales del
**userinfo de la `serviceUrl`**, no de `eureka.client.username/password`.

## Qué se decidió

No se toca `services/api-gateway/`, `services/discovery-server/` ni `docs/especification/**`: son
zonas de `infra-gateway-eureka` y de la especificación. Para la corrida de aceptación se ajustó solo
el `.env` local (ignorado por Git):

```
EUREKA_URL=http://<EUREKA_USERNAME>:<EUREKA_PASSWORD>@localhost:8761/eureka
JWK_SET_URI=http://localhost:8081/.well-known/jwks.json
```

Con esa URL, `identity-service` y `api-gateway` se registran y SEC02 pasa.

## Por qué

La spec fija las variables y este plan no tiene autoridad para cambiar ni el código de otro
servicio ni la especificación. Registrar el hallazgo es lo que la norma pide: que no quede como una
sorpresa de la siguiente persona que monte el entorno en el host.

## Alternativas descartadas

- **Cambiar `application.yml` del Gateway y de Eureka**: fuera de la zona de este plan y
  `infra-gateway-eureka` ya está cerrado.
- **Añadir `eureka.client.basic-auth.*`**: es probar a ciegas una propiedad de la misma familia sin
  comprobar el código de la librería.
- **Levantar identity en Compose junto al Gateway**: el compose del proyecto no tiene contenedor de
  servicios de dominio, y añadirlos es de otro plan.

## A quién afecta

`infra-gateway-eureka` (cerrado) y, por extensión, cualquier servicio que se ejecute en el host en
lugar de dentro de la red de Compose, donde el nombre `identity-service` sí resuelve. En Compose el
valor por defecto de `configuracion.md` funciona tal cual. Ningún contrato cambia.