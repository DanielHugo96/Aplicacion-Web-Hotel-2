# Estado de implementación

Registro de los 25 planes. **No es fuente de verdad:** manda el campo `Estado` del plan en `origin/main`.

Cada plan ejecutado se registra aquí en un PR `docs:` aparte, después de que su PR de implementación se mergea.
Así varias PRs de implementación no chocan en esta tabla. El reclamo de un plan en curso vive en su rama y en su
PR en borrador (ver [`README.md` §3](README.md#3-cómo-se-implementa-un-plan)).

| Fase | Plan | Tanda | Estado en `main` | Rama | PR |
|---|---|---|---|---|---|
| F0 | [`infra-monorepo`](../plans/infra-monorepo.md) | T0 | `cerrado` | `infra-monorepo` | [#43](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/43) |
| F1 | [`infra-compose`](../plans/infra-compose.md) | T1 | `cerrado` | `infra-compose` | [#45](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/45) |
| F1 | [`back-plataforma-comunes`](../plans/back-plataforma-comunes.md) | T2 | `cerrado` | `back-plataforma-comunes` | [#46](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/46) |
| F1 | [`infra-gateway-eureka`](../plans/infra-gateway-eureka.md) | T2 | `cerrado` | `infra-gateway-eureka` | [#48](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/48) |
| F1 | [`back-plataforma-mensajeria`](../plans/back-plataforma-mensajeria.md) | T3 | `cerrado` | `back-plataforma-mensajeria` | [#52](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/52) |
| F2 | [`back-identity-auth`](../plans/back-identity-auth.md) | T4 | `propuesto` | — | — |
| F4 | [`back-notification-worker`](../plans/back-notification-worker.md) | T4 | `propuesto` | — | — |
| F2 | [`back-hotel-catalogo`](../plans/back-hotel-catalogo.md) | T5 | `propuesto` | — | — |
| F2 | [`front-admin-web-base`](../plans/front-admin-web-base.md) | T5 | `propuesto` | — | — |
| F2 | [`back-identity-personas`](../plans/back-identity-personas.md) | T6 | `propuesto` | — | — |
| F3 | [`back-inventory-stock`](../plans/back-inventory-stock.md) | T6 | `propuesto` | — | — |
| F2 | [`front-public-web-landing`](../plans/front-public-web-landing.md) | T6 | `propuesto` | — | — |
| F3 | [`back-reception-operacion`](../plans/back-reception-operacion.md) | T7 | `propuesto` | — | — |
| F2 | [`front-admin-web-catalogo`](../plans/front-admin-web-catalogo.md) | T7 | `propuesto` | — | — |
| F3 | [`back-hotel-operaciones`](../plans/back-hotel-operaciones.md) | T8 | `propuesto` | — | — |
| F3 | [`back-sales-ventas`](../plans/back-sales-ventas.md) | T8 | `propuesto` | — | — |
| F4 | [`back-reception-cierre`](../plans/back-reception-cierre.md) | T9 | `propuesto` | — | — |
| F4 | [`front-admin-web-area-cliente`](../plans/front-admin-web-area-cliente.md) | T9 | `propuesto` | — | — |
| F4 | [`back-reporting-proyecciones`](../plans/back-reporting-proyecciones.md) | T10 | `propuesto` | — | — |
| F3 | [`front-admin-web-recepcion`](../plans/front-admin-web-recepcion.md) | T10 | `propuesto` | — | — |
| F5 | [`qa-runbooks`](../plans/qa-runbooks.md) | T11 | `propuesto` | — | — |
| F3 | [`front-admin-web-ventas`](../plans/front-admin-web-ventas.md) | T11 | `propuesto` | — | — |
| F4 | [`front-admin-web-cierre`](../plans/front-admin-web-cierre.md) | T12 | `propuesto` | — | — |
| F4 | [`front-admin-web-reportes`](../plans/front-admin-web-reportes.md) | T13 | `propuesto` | — | — |
| F5 | [`qa-matriz-aceptacion`](../plans/qa-matriz-aceptacion.md) | T14 | `propuesto` | — | — |

Valores de `Estado` (norma §8): `propuesto`, `en curso`, `cerrado`. Las tandas y colas están en [`orden.md`](orden.md).
