# Estado de implementación

Registro de los planes. **No es fuente de verdad:** manda el campo `Estado` del plan en `origin/main`.

Cada plan ejecutado se registra aquí en un PR `docs:` aparte, después de que su PR de implementación se mergea.
Así varias PRs de implementación no chocan en esta tabla. El reclamo de un plan en curso vive en su rama y en su
PR en borrador (ver [`README.md` §3](README.md#3-cómo-se-implementa-un-plan)).

La tabla lista **todos** los planes, no solo los cerrados: los que aún no se han ejecutado figuran con su
`Estado en main` real (`propuesto`) y `—` en rama y PR, para que se vea de un vistazo qué falta por implementar.

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
| F3 | [`back-inventory-stock`](../plans/back-inventory-stock.md) | T6 | `cerrado` | `back-inventory-stock` | [#81](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/81) |
| F2 | [`front-public-web-landing`](../plans/front-public-web-landing.md) | T6 | `cerrado` | `front-public-web-landing` | [#83](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/83) |
| F3 | [`back-reception-operacion`](../plans/back-reception-operacion.md) | T7 | `cerrado` | `back-reception-operacion` | [#88](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/88) |
| F2 | [`front-admin-web-catalogo`](../plans/front-admin-web-catalogo.md) | T7 | `cerrado` | `front-admin-web-catalogo` | [#96](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/96) |
| F3 | [`back-hotel-operaciones`](../plans/back-hotel-operaciones.md) | T8 | `cerrado` | `back-hotel-operaciones` | [#98](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/98) |
| F3 | [`back-sales-ventas`](../plans/back-sales-ventas.md) | T8 | `cerrado` | `back-sales-ventas` | [#100](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/100) |
| F4 | [`back-reception-cierre`](../plans/back-reception-cierre.md) | T9 | `cerrado` | `back-reception-cierre` | [#102](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/102) |
| F4 | [`front-admin-web-area-cliente`](../plans/front-admin-web-area-cliente.md) | T9 | `cerrado` | `front-admin-web-area-cliente` | [#104](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/104) |
| F4 | [`back-reporting-proyecciones`](../plans/back-reporting-proyecciones.md) | T10 | `cerrado` | `back-reporting-proyecciones` | [#107](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/107) |
| F3 | [`front-admin-web-recepcion`](../plans/front-admin-web-recepcion.md) | T10 | `cerrado` | `front-admin-web-recepcion` | [#109](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/109) |
| F5 | [`qa-runbooks`](../plans/qa-runbooks.md) | T11 | `cerrado` | `qa-runbooks` | [#111](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/111) |
| F3 | [`front-admin-web-ventas`](../plans/front-admin-web-ventas.md) | T11 | `cerrado` | `front-admin-web-ventas` | [#119](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/119) |
| F4 | [`front-admin-web-cierre`](../plans/front-admin-web-cierre.md) | T12 | `cerrado` | `front-admin-web-cierre` | [#121](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/121) |
| F4 | [`front-admin-web-reportes`](../plans/front-admin-web-reportes.md) | T13 | `cerrado` | `front-admin-web-reportes` | [#123](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/123) |
| F5 | [`qa-matriz-aceptacion`](../plans/qa-matriz-aceptacion.md) | T14 | `cerrado` | `qa-matriz-aceptacion` | [#125](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/125) |
| F5 | [`back-hotel-presentacion-real`](../plans/back-hotel-presentacion-real.md) | T15 | `cerrado` | `back-hotel-presentacion-real` | [#133](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/133) |
| F5 | [`back-identity-presentacion-real`](../plans/back-identity-presentacion-real.md) | T15 | `cerrado` | `back-identity-presentacion-real` | [#135](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/135) |
| F5 | [`back-notification-presentacion-real`](../plans/back-notification-presentacion-real.md) | T15 | `cerrado` | `back-notification-presentacion-real` | [#139](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/139) |
| F5 | [`back-inventory-imagenes-producto`](../plans/back-inventory-imagenes-producto.md) | T15 | `cerrado` | `back-inventory-imagenes-producto` | [#137](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/137) |
| F5 | [`front-public-web-presentacion-real`](../plans/front-public-web-presentacion-real.md) | T16 | `propuesto` | — | — |
| F5 | [`front-admin-web-imagenes-panel`](../plans/front-admin-web-imagenes-panel.md) | T16 | `propuesto` | — | — |
| F5 | [`qa-matriz-lp01-imagenes`](../plans/qa-matriz-lp01-imagenes.md) | T17 | `propuesto` | — | — |
| F2 | [`back-arreglo-hotel-arranque`](../plans/back-arreglo-hotel-arranque.md) | — | `cerrado` | `back-arreglo-hotel-arranque` | [#76](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/76) |
| F1 | [`back-arreglo-audiencia-maquina`](../plans/back-arreglo-audiencia-maquina.md) | — | `cerrado` | `back-arreglo-audiencia-maquina` | [#79](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/79) |
| F2 | [`back-arreglo-hotel-catalogo-admin`](../plans/back-arreglo-hotel-catalogo-admin.md) | — | `cerrado` | `back-arreglo-hotel-catalogo-admin` | [#92](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/92) |
| F2 | [`back-arreglo-identity-personas-catalogo`](../plans/back-arreglo-identity-personas-catalogo.md) | — | `cerrado` | `back-arreglo-identity-personas-catalogo` | [#94](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/94) |
| F3 | [`back-arreglo-inventory-baja-y-ajuste`](../plans/back-arreglo-inventory-baja-y-ajuste.md) | — | `cerrado` | `back-arreglo-inventory-baja-y-ajuste` | [#115](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/115) |
| F3 | [`back-arreglo-sales-pago-202`](../plans/back-arreglo-sales-pago-202.md) | — | `cerrado` | `back-arreglo-sales-pago-202` | [#117](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/117) |
| F2 | [`back-arreglo-ruta-interna-clientes`](../plans/back-arreglo-ruta-interna-clientes.md) | — | `cerrado` | `back-arreglo-ruta-interna-clientes` | [#86](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/86) |

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

- `back-inventory-stock` (T6): **bloqueado dos veces y ya `cerrado`.** El primer bloqueo no era suyo: `hotel-service` no arrancaba desde `main` (plan cerrado `back-hotel-catalogo`, PR #66), lo que dejó su PR [#72] cerrado sin mergear. El segundo fue la audiencia del token máquina. Los dos se resolvieron con planes de arreglo —[`back-arreglo-hotel-arranque`](../plans/back-arreglo-hotel-arranque.md) (PR #76) y [`back-arreglo-audiencia-maquina`](../plans/back-arreglo-audiencia-maquina.md) (PR #79)— y después se retomó el plan y se mergeó en [#81](https://github.com/DanielHugo96/Aplicacion-Web-Hotel-2/pull/81). Bitácora: [`decisiones/IA-back-inventory-stock-02.md`](decisiones/IA-back-inventory-stock-02.md).
