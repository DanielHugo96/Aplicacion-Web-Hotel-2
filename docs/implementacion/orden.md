# Orden de implementación

Calendario de los 25 planes de [`../plans/`](../plans). Sale de los `Depende de` de cada plan y de las
zonas que toca. Cada plan arranca **en cuanto** sus dependencias estén cerradas en `main` y su zona esté
libre. La tanda es la referencia para saber qué puede ir a la vez, no una barrera que haya que esperar.

## 1. Punto de partida

- Los 25 planes están en `propuesto` en `main`. **Nada está implementado todavía.**
- Se empieza por **T0** (`infra-monorepo`), que no tiene dependencias.
- `front-admin-web-catalogo` no tiene archivo en `main`: no puede ejecutarse, y bloquea
  `qa-matriz-aceptacion` (ver [`README.md` §6](README.md#6-decisiones-abiertas-del-registro)).

## 2. Calendario

| Tanda | Planes que pueden correr a la vez | Requieren estar cerrados |
|---|---|---|
| **T0** | `infra-monorepo` | — |
| **T1** | `infra-compose` | `infra-monorepo` |
| **T2** | `back-plataforma-comunes` ‖ `infra-gateway-eureka` | `infra-compose` |
| **T3** | `back-plataforma-mensajeria` | `back-plataforma-comunes` |
| **T4** | `back-identity-auth` ‖ `back-notification-worker` | `back-plataforma-mensajeria` |
| **T5** | `back-hotel-catalogo` ‖ `front-admin-web-base` | `back-identity-auth` (y mensajería para hotel) |
| **T6** | `back-identity-personas` ‖ `back-inventory-stock` ‖ `front-public-web-landing` | identity-auth y hotel-catalogo, hotel-catalogo y mensajería, hotel-catalogo |
| **T7** | `back-reception-operacion` ‖ `front-admin-web-catalogo` ⚠ bloqueado | personas, hotel-catalogo y mensajería; base, personas y hotel-catalogo |
| **T8** | `back-hotel-operaciones` ‖ `back-sales-ventas` | reception-operacion; reception-operacion, inventory-stock y mensajería |
| **T9** | `back-reception-cierre` ‖ `front-admin-web-area-cliente` | reception-operacion y sales-ventas; base, reception-operacion y sales-ventas |
| **T10** | `back-reporting-proyecciones` ‖ `front-admin-web-recepcion` | los siete back de su `Depende de` (incluye reception-cierre); base, reception-operacion y hotel-operaciones |
| **T11** | `qa-runbooks` ‖ `front-admin-web-ventas` | mensajería, reception-cierre, hotel-operaciones, notification-worker, reporting; base, sales-ventas e inventory-stock |
| **T12** | `front-admin-web-cierre` | reception-cierre y front-admin-web-recepcion |
| **T13** | `front-admin-web-reportes` | reporting-proyecciones y front-admin-web-base |
| **T14** | `qa-matriz-aceptacion` | **todos** los F1–F4, incluido `front-admin-web-catalogo` |

Notas de lectura:

- Las dependencias de cada fila están en el encabezado `Depende de` del plan. Si una tabla y un plan
  no coinciden, manda el plan.
- `back-notification-worker` solo depende de mensajería, así que puede hacerse en **T4**, no en T11.
- `front-public-web-landing` solo depende de hotel-catalogo y no usa `admin-web`, así que puede ir en paralelo con la cola del panel.

## 3. Zonas exclusivas

Estas restricciones no salen de las dependencias. Son de **archivos compartidos**, y se aplican aunque
los planes no dependan entre sí.

| Zona | Planes que la tocan | Regla |
|---|---|---|
| `apps/admin-web/**` | `front-admin-web-base`, `-catalogo`, `-recepcion`, `-ventas`, `-area-cliente`, `-cierre`, `-reportes` | **Una a la vez.** Orden de la cola: base → catalogo → area-cliente → recepcion → ventas → cierre → reportes. Si el siguiente no está listo, toma el siguiente que sí lo esté. |
| `tests/**` y `scripts/**` | `qa-runbooks`, `qa-matriz-aceptacion` | **Una a la vez:** runbooks primero, matriz después. |
| `services/*-service/**` (plataforma) | `back-plataforma-comunes`, `back-plataforma-mensajeria` | Tocan los siete servicios: ningún otro plan de servicio abierto mientras estén abiertos. Las dependencias ya lo garantizan. |
| `services/<servicio>-service/**` | cada `back-*` de servicio | **Un plan abierto por servicio.** Ya se cumple en el calendario. |

## 4. Archivos compartidos entre planes que sí dependen uno del otro

Estos archivos los tocan dos planes, pero el orden de dependencia los pone en serie. Verificado contra los
planes:

| Archivo | Orden |
|---|---|
| `infra/compose.yaml` | `infra-monorepo` → `infra-compose` → `infra-gateway-eureka` |
| `contracts/openapi/hotel.yaml` | `back-hotel-catalogo` → `back-hotel-operaciones` |
| `contracts/openapi/reception.yaml` | `back-reception-operacion` → `back-reception-cierre` |
| `contracts/openapi/admin-web.yaml` | `front-admin-web-base` → `front-admin-web-reportes` |

Regla: el segundo plan solo empieza cuando el primero está **cerrado en `main`**, no solo en su rama.

## 5. Velocidad y cuello de botella

- Solo por dependencias, el mínimo serían **13 tandas** (T0 a T12). Con la cola segura de `admin-web`
  y la cola de `qa` son **15 tandas** (T0 a T14), con una duración similar por plan. Serializar cuesta
  dos tandas: es el precio de la seguridad.
- El cuello de botella es la **cola de `admin-web`**: siete planes en serie, desde T5 hasta T13.
- Para acelerar sin arriesgar: que una persona lleve la cola de front de principio a fin, y que las
  demás adelanten back en los carriles libres (ver la tabla de tandas).
- Las duraciones reales no son iguales. La cola de front manda: si un plan de front tarda más, retrasa T13 y T14.

## 6. Si sois una sola persona

Sigue el calendario de **T0 a T14**. Dentro de una tanda, el orden es libre siempre que se respeten las
dependencias y las colas de la sección 3.

## 7. Si tu plan está bloqueado

No esperes sin hacer nada. Toma el siguiente plan **listo** de la tanda actual o de la siguiente, según
[`README.md` §2](README.md#2-un-plan-está-listo-si-cumple-todo). Si nada está listo, avisa al equipo.
