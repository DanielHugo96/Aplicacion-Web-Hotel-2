# Estado de implementación

Registro de los planes. **No es fuente de verdad:** manda el campo `Estado` del plan en `origin/main`.

Cada plan ejecutado se registra aquí en un PR `docs:` aparte, después de que su PR de implementación se mergea.
Así varias PRs de implementación no chocan en esta tabla. El reclamo de un plan en curso vive en su rama y en su
PR en borrador (ver [`README.md` §3](README.md#3-cómo-se-implementa-un-plan)).

Los **planes de arreglo** (`<ámbito>-arreglo-<tema>`, norma §6.11.5) también se registran aquí cuando se
cierran, con la fase del plan reparado y tanda `—`.

| Fase | Plan | Tanda | Estado en `main` | Rama | PR |
|---|---|---|---|---|---|
| F0 | [`infra-monorepo`](../plans/infra-monorepo.md) | T0 | `cerrado` | `infra-monorepo` | [#43](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/43) |
| F1 | [`infra-compose`](../plans/infra-compose.md) | T1 | `cerrado` | `infra-compose` | [#45](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/45) |
| F1 | [`back-plataforma-comunes`](../plans/back-plataforma-comunes.md) | T2 | `cerrado` | `back-plataforma-comunes` | [#46](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/46) |
| F1 | [`infra-gateway-eureka`](../plans/infra-gateway-eureka.md) | T2 | `cerrado` | `infra-gateway-eureka` | [#48](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/48) |
| F1 | [`back-plataforma-mensajeria`](../plans/back-plataforma-mensajeria.md) | T3 | `cerrado` | `back-plataforma-mensajeria` | [#52](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/52) |
| F2 | [`back-identity-auth`](../plans/back-identity-auth.md) | T4 | `cerrado` | `back-identity-auth` | [#61](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/61) |
| F4 | [`back-notification-worker`](../plans/back-notification-worker.md) | T4 | `cerrado` | `back-notification-worker` | [#63](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/63) |
| F2 | [`back-hotel-catalogo`](../plans/back-hotel-catalogo.md) | T5 | `cerrado` | `back-hotel-catalogo` | [#66](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/66) |
| F2 | [`front-admin-web-base`](../plans/front-admin-web-base.md) | T5 | `cerrado` | `front-admin-web-base` | [#68](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/68) |
| F2 | [`back-identity-personas`](../plans/back-identity-personas.md) | T6 | `cerrado` | `back-identity-personas` | [#70](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/70) |
| F3 | [`back-inventory-stock`](../plans/back-inventory-stock.md) | T6 | `propuesto` | `back-inventory-stock` | [#72](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/72) (cerrado sin mergear) |
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

**Bloqueos de la campaña.** Un plan que la campaña no pudo cerrar conserva aquí su `Estado en main`
real y, en las columnas de rama y PR, la tentativa: la rama queda en `origin` y el PR se cierra sin
mergear por la regla 6.11.3 de [`../plans/README.md`](../plans/README.md). El motivo completo está en
[`decisiones/`](decisiones/).

- `back-identity-auth` (T4): **desbloqueado y mergeado el 18/10/2026.** Quedó bloqueado en la
  campaña por reincidencia del agente revisor (PR [#58](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/58),
  cerrado sin mergear), pero el bloqueo era documental y no funcional: el código estaba verificado
  y en verde. Se mergeó a `main` en el PR
  [#61](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/61) corrigiendo los recuentos
  del plan, una vez que la campaña dejó de tener agente revisor (PR #60). Detalle del bloqueo
  original: [`decisiones/IA-back-identity-auth-11.md`](decisiones/IA-back-identity-auth-11.md).

- `back-inventory-stock` (T6): PR [#72](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/72)
  cerrado sin mergear. **El bloqueo no es de este plan: `hotel-service` no arranca desde `main`**, por
  dos defectos del plan ya cerrado `back-hotel-catalogo` (PR #66), y `back-hotel-catalogo` es
  dependencia directa o transitiva de todos los planes que quedan. La evidencia HTTP que declara ese
  plan cerrado (`LP01`, `SEC03`, `CRUD01`, `OCC04`) no puede ser real y conviene revisarla. Detalle y
  cómo desbloquearlo: [`decisiones/IA-back-inventory-stock-02.md`](decisiones/IA-back-inventory-stock-02.md).
