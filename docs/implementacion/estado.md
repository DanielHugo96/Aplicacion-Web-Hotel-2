# Estado de implementación

Una fila por plan. **Se actualiza al mergear**, no en cada push (ver
[`README.md` §1](README.md#1-reglas-de-este-registro)). El estado oficial es el campo `Estado` del
propio plan; esta tabla solo lo refleja.

Regla de la tabla: cada quien toca **solo su fila**.

| Fase | Plan | Tanda | En paralelo con | Bloqueado por | Dueño | Rama | PR | Estado |
|---|---|---|---|---|---|---|---|---|
| F0 | [`infra-monorepo`](../plans/infra-monorepo.md) | T0 | — | — | — | — | — | `propuesto` |
| F1 | [`infra-compose`](../plans/infra-compose.md) | T1 | — | `infra-monorepo` | — | — | — | `propuesto` |
| F1 | [`infra-gateway-eureka`](../plans/infra-gateway-eureka.md) | T1 | `back-plataforma-comunes` | `infra-compose` | — | — | — | `propuesto` |
| F1 | [`back-plataforma-comunes`](../plans/back-plataforma-comunes.md) | T1 | `infra-gateway-eureka` | `infra-compose` | — | — | — | `propuesto` |
| F1 | [`back-plataforma-mensajeria`](../plans/back-plataforma-mensajeria.md) | T1 | — | `infra-compose`, `back-plataforma-comunes` | — | — | — | `propuesto` |
| F2 | [`back-identity-auth`](../plans/back-identity-auth.md) | T2 | — | `back-plataforma-mensajeria` | — | — | — | `propuesto` |
| F2 | [`back-hotel-catalogo`](../plans/back-hotel-catalogo.md) | T2 | `front-admin-web-base` | `back-identity-auth`, `back-plataforma-mensajeria` | — | — | — | `propuesto` |
| F2 | [`front-admin-web-base`](../plans/front-admin-web-base.md) | T2 | `back-hotel-catalogo` | `back-identity-auth` | — | — | — | `propuesto` |
| F2 | [`back-identity-personas`](../plans/back-identity-personas.md) | T2 | `front-public-web-landing` | `back-identity-auth`, `back-hotel-catalogo` | — | — | — | `propuesto` |
| F2 | [`front-public-web-landing`](../plans/front-public-web-landing.md) | T2 | `back-identity-personas` | `back-hotel-catalogo` | — | — | — | `propuesto` |
| F2 | `front-admin-web-catalogo` (sin archivo en `main` todavía) | T2 | — | `front-admin-web-base`, `back-identity-personas`, `back-hotel-catalogo`, **plan no mergeado en `main`** | — | — | — | `propuesto` |
| F3 | [`back-reception-operacion`](../plans/back-reception-operacion.md) | T3 | `back-inventory-stock` | `back-identity-personas`, `back-hotel-catalogo`, `back-plataforma-mensajeria` | — | — | — | `propuesto` |
| F3 | [`back-inventory-stock`](../plans/back-inventory-stock.md) | T3 | `back-reception-operacion` | `back-hotel-catalogo`, `back-plataforma-mensajeria` | — | — | — | `propuesto` |
| F3 | [`back-hotel-operaciones`](../plans/back-hotel-operaciones.md) | T3 | `back-sales-ventas` | `back-reception-operacion` | — | — | — | `propuesto` |
| F3 | [`back-sales-ventas`](../plans/back-sales-ventas.md) | T3 | `back-hotel-operaciones` | `back-reception-operacion`, `back-inventory-stock`, `back-plataforma-mensajeria` | — | — | — | `propuesto` |
| F3 | [`front-admin-web-recepcion`](../plans/front-admin-web-recepcion.md) | T3 | `front-admin-web-ventas` | `front-admin-web-base`, `back-reception-operacion`, `back-hotel-operaciones` | — | — | — | `propuesto` |
| F3 | [`front-admin-web-ventas`](../plans/front-admin-web-ventas.md) | T3 | `front-admin-web-recepcion` | `front-admin-web-base`, `back-sales-ventas`, `back-inventory-stock` | — | — | — | `propuesto` |
| F4 | [`back-notification-worker`](../plans/back-notification-worker.md) | T4 (comodín: puede ir en T2) | `back-reception-cierre`, `back-reporting-proyecciones` | `back-plataforma-mensajeria` | — | — | — | `propuesto` |
| F4 | [`back-reception-cierre`](../plans/back-reception-cierre.md) | T4 | `back-reporting-proyecciones` | `back-reception-operacion`, `back-sales-ventas` | — | — | — | `propuesto` |
| F4 | [`back-reporting-proyecciones`](../plans/back-reporting-proyecciones.md) | T4 | `back-reception-cierre` | `back-identity-personas`, `back-hotel-catalogo`, `back-hotel-operaciones`, `back-reception-cierre`, `back-inventory-stock`, `back-sales-ventas`, `back-plataforma-mensajeria` | — | — | — | `propuesto` |
| F4 | [`front-admin-web-cierre`](../plans/front-admin-web-cierre.md) | T4 | `front-admin-web-area-cliente`, `front-admin-web-reportes` | `front-admin-web-recepcion`, `back-reception-cierre` | — | — | — | `propuesto` |
| F4 | [`front-admin-web-area-cliente`](../plans/front-admin-web-area-cliente.md) | T4 | `front-admin-web-cierre`, `front-admin-web-reportes` | `front-admin-web-base`, `back-reception-operacion`, `back-sales-ventas` | — | — | — | `propuesto` |
| F4 | [`front-admin-web-reportes`](../plans/front-admin-web-reportes.md) | T4 | `front-admin-web-cierre`, `front-admin-web-area-cliente` | `front-admin-web-base`, `back-reporting-proyecciones` | — | — | — | `propuesto` |
| F5 | [`qa-runbooks`](../plans/qa-runbooks.md) | T5 | `qa-matriz-aceptacion` | `back-plataforma-mensajeria`, `back-reception-cierre`, `back-hotel-operaciones`, `back-notification-worker`, `back-reporting-proyecciones` | — | — | — | `propuesto` |
| F5 | [`qa-matriz-aceptacion`](../plans/qa-matriz-aceptacion.md) | T5 | `qa-runbooks` | F1–F4 completas | — | — | — | `propuesto` |

## Leyenda

- **Estado:** `propuesto` (escrito, sin empezar) · `en curso` (rama abierta) · `cerrado` (PR
  implementado mergeado).
- **Bloqueado por:** todos sus `Depende de` deben estar `cerrado`. El aviso *"plan no mergeado en
  `main`"* significa que el archivo del plan todavía no existe en `main` y no se puede ejecutar.
- **Rama:** el nombre del plan, sin prefijo (por ejemplo `back-identity-auth`).
- Tandas y paralelismo: [`orden.md`](orden.md).
