# Registro de implementación

Cómo llevamos entre varias personas la ejecución de los planes de [`../plans/`](../plans). Este
registro **no reemplaza** la norma de planes ([`../plans/README.md`](../plans/README.md)) ni el
catálogo ([`../plans/catalogo.md`](../plans/catalogo.md)): las dependencias, los dueños de mensajes
y los casos siguen viviendo ahí. Aquí solo hay **orden, paralelismo y estado**.

| Archivo | Qué es |
|---|---|
| [`orden.md`](orden.md) | La secuencia de implementación: tandas, camino crítico, qué se puede hacer en paralelo y cuellos de botella. |
| [`estado.md`](estado.md) | La tabla viva: una fila por plan con dueño, rama, PR y estado. |

## 1. Reglas de este registro

1. **Manda la norma de planes.** Esto no puede contradecir a [`../plans/README.md`](../plans/README.md)
   ni al [`catalogo.md`](../plans/catalogo.md). Si hay conflicto, gana la norma.
2. **No se trabaja en `main`.** Cada plan va en su rama, con el nombre del plan
   ([`../guia-git.md`](../guia-git.md)).
3. **Un plan = una rama = un PR.** Nunca dos planes en la misma rama ni en el mismo archivo.
4. **El estado oficial vive en el plan**, en su campo `Estado`
   ([`../plans/README.md` §8](../plans/README.md#8-ciclo-de-vida)). Este registro solo lo refleja.
5. **Quien ejecuta no toca** `catalogo.md` ni `docs/plans/README.md`
   ([`../plans/README.md` §6.4](../plans/README.md#6-contrato-con-la-ejecutora)). Los planes de
   implementación **no modifican el catálogo**: las filas ya están escritas por la planificadora.
6. **Un plan solo se ejecuta si está `propuesto` y mergeado en `main`**
   ([`../plans/README.md` §6.1](../plans/README.md#6-contrato-con-la-ejecutora)).
7. **`estado.md` se actualiza al mergear**, no en cada push. Así se evitan conflictos de merge en
   la tabla. La vista en vivo, si hace falta, es el tablero de GitHub (sección 4).

## 2. Ciclo de un plan

| Momento | Qué pasa | Dónde se ve |
|---|---|---|
| Tomo el plan | Marco `en curso` y creo la rama `<plan>` | Campo `Estado` del plan; fila en `estado.md` |
| Avanzo | Marco `- [x]` en el mismo commit que cierra cada paso (§6.5) | El propio plan |
| Termino | PR a `main`; lo revisa otra persona y hace el merge | GitHub |
| Mergeado | Marco `cerrado` (último PR del plan, §6.9) | Campo `Estado` del plan |
| Después | Actualizo su fila en `estado.md` | `estado.md` |

## 3. Índice de planes (agrupado, sin mover archivos) {#3-indice}

Los 25 planes viven en `docs/plans/<ámbito>-<tema>.md` y **ahí se quedan**: la norma prohíbe
carpetas adicionales ([§8](../plans/README.md#8-ciclo-de-vida)) y el validador
(`PLANES.glob("*.md")`) y los enlaces relativos de los planes dependen de esa ruta. Este índice
te da la vista agrupada sin tocar nada.

### Por fase

- **F0 · Fundación (1 plan):** [`infra-monorepo`](../plans/infra-monorepo.md)
- **F1 · Plataforma (4):** [`infra-compose`](../plans/infra-compose.md) ·
  [`infra-gateway-eureka`](../plans/infra-gateway-eureka.md) ·
  [`back-plataforma-comunes`](../plans/back-plataforma-comunes.md) ·
  [`back-plataforma-mensajeria`](../plans/back-plataforma-mensajeria.md)
- **F2 · Dominio base (6):** [`back-identity-auth`](../plans/back-identity-auth.md) ·
  [`back-hotel-catalogo`](../plans/back-hotel-catalogo.md) ·
  [`back-identity-personas`](../plans/back-identity-personas.md) ·
  [`front-public-web-landing`](../plans/front-public-web-landing.md) ·
  [`front-admin-web-base`](../plans/front-admin-web-base.md) ·
  `front-admin-web-catalogo` (sin archivo en `main` todavía)
- **F3 · Operación (6):** [`back-reception-operacion`](../plans/back-reception-operacion.md) ·
  [`back-hotel-operaciones`](../plans/back-hotel-operaciones.md) ·
  [`back-inventory-stock`](../plans/back-inventory-stock.md) ·
  [`back-sales-ventas`](../plans/back-sales-ventas.md) ·
  [`front-admin-web-recepcion`](../plans/front-admin-web-recepcion.md) ·
  [`front-admin-web-ventas`](../plans/front-admin-web-ventas.md)
- **F4 · Cierre y asíncronos (7):** [`back-reception-cierre`](../plans/back-reception-cierre.md) ·
  [`back-notification-worker`](../plans/back-notification-worker.md) ·
  [`back-reporting-proyecciones`](../plans/back-reporting-proyecciones.md) ·
  [`front-admin-web-cierre`](../plans/front-admin-web-cierre.md) ·
  [`front-admin-web-reportes`](../plans/front-admin-web-reportes.md) ·
  [`front-admin-web-area-cliente`](../plans/front-admin-web-area-cliente.md)
- **F5 · Aceptación (2):** [`qa-matriz-aceptacion`](../plans/qa-matriz-aceptacion.md) ·
  [`qa-runbooks`](../plans/qa-runbooks.md)

### Por ámbito

- **infra (3):** `infra-monorepo`, `infra-compose`, `infra-gateway-eureka`
- **back (12):** `back-plataforma-comunes`, `back-plataforma-mensajeria`, `back-identity-auth`,
  `back-hotel-catalogo`, `back-identity-personas`, `back-reception-operacion`,
  `back-hotel-operaciones`, `back-inventory-stock`, `back-sales-ventas`, `back-reception-cierre`,
  `back-notification-worker`, `back-reporting-proyecciones`
- **front (8):** `front-public-web-landing`, `front-admin-web-base`, `front-admin-web-catalogo`, `front-admin-web-recepcion`, `front-admin-web-ventas`, `front-admin-web-cierre`, `front-admin-web-reportes`, `front-admin-web-area-cliente`
- **qa (2):** `qa-matriz-aceptacion`, `qa-runbooks`

> Si entra un plan nuevo, añade su enlace en la fase que le toque en el mismo PR que lo planifica.

## 4. Tablero de GitHub (vista en vivo)

El repo no usa issues ni Projects todavía. Propuesta mínima para quien tenga permisos:

- **Labels:** `fase-f0`…`fase-f5`, `ambito-back`, `ambito-front`, `ambito-infra`, `ambito-qa`,
  `modo-openspec`, `bloqueado`.
- **Milestones:** uno por fase (`F0`…`F5`).
- **Un issue por plan**, con el título del plan y los pasos del plan como checklist. El issue se
  cierra al mergear el PR del plan.
- **Un Project (kanban)** con columnas `Pendiente / En curso / En revisión / Mergeado`, y los 25
  planes como cards. El trabajo de la IA y de las personas se ve ahí, sin tocar el repo.

Con `gh` instalado (opcional, no es requisito):

```bash
gh label create fase-f1 --color 0e8a16
gh issue create --title "back-identity-auth" --body-file docs/plans/back-identity-auth.md --label ambito-back
```

Sin `gh`: se hace a mano desde la pestaña *Issues* y *Projects* de GitHub.
