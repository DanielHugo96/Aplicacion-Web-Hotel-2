# Registro de implementación

Cómo se ejecutan los planes de [`../plans/`](../plans) entre varias personas sin pisarse.
Aquí no hay autoridad propia: todo lo que dice se deriva de la norma y de los planes.

| Archivo | Qué es |
|---|---|
| [`orden.md`](orden.md) | Calendario de los 25 planes, qué puede ir en paralelo y qué debe ir en serie. |
| [`estado.md`](estado.md) | Resumen por plan. Puede estar atrasado: si hay duda, manda el plan en `main`. |

## 1. Fuentes de verdad, en este orden

1. **La norma:** [`../plans/README.md`](../plans/README.md) y [`../guia-git.md`](../guia-git.md). Gana siempre.
2. **El plan en `main`:** su `Estado` y su `Depende de` deciden si se puede empezar.
3. **El catálogo:** [`../plans/catalogo.md`](../plans/catalogo.md). Debe coincidir con el plan.
4. **Este registro:** [`orden.md`](orden.md) indica cuándo y con qué ir en paralelo. No autoriza nada por sí mismo.

## 2. Un plan está listo si cumple todo

- Su `Estado` en `origin/main` es `propuesto`.
- Todos sus `Depende de` tienen `Estado: cerrado` en `origin/main`.
- No existe rama remota ni PR abierto con su nombre (ver paso 1 de §3).
- Su zona está libre según las colas de [`orden.md` §3](orden.md#3-zonas-exclusivas).
- No tiene preguntas abiertas.

## 3. Cómo se implementa un plan

1. **Reclamar.** La rama con el nombre del plan es el candado.
   ```bash
   git switch main && git pull origin main
   git ls-remote --heads origin <plan>      # debe salir vacío
   git switch -c <plan>
   ```
   Cambia `Estado` a `en curso` en `docs/plans/<plan>.md`, haz commit `docs: marca en curso el plan <plan>`,
   sube con `git push -u origin <plan>` y abre un **PR en borrador** titulado `feat: implementa el plan <plan>`.
   Si el `push` falla porque la rama ya existe, alguien la tomó primero: elige otro plan.
2. **Ejecutar los pasos en orden** (norma §6.5). Por cada `- [ ]`: haz el cambio, corre la verificación
   canónica de la sección «Verificación» del `README.md` raíz y marca `- [x]` **en el mismo commit**.
   - Si un paso no se puede cumplir o la spec no decide, no lo marques: escribe la pregunta en
     *Preguntas abiertas*, haz commit y **para** (norma §6.6).
   - Si el plan es `openspec`, sigue `openspec/changes/<plan>/` (`tasks.md`) con la skill `openspec-apply-change`.
3. **Evidencia.** En *Pruebas*, cada caso asignado anota comando o endpoint, resultado y dónde está la salida
   (norma §6.8). Sin evidencia, el caso no se marca.
4. **Sincronizar.** `git pull origin main` (merge). **Nunca** `rebase` ni `push --force` sobre una rama ya
   publicada. Si `main` cambió en archivos que tocas, vuelve a correr la verificación.
5. **Cerrar** en el último commit del plan: `Estado: cerrado`, todos los pasos marcados, *Preguntas abiertas*
   vacías y, si es `openspec`, el change archivado con `openspec-archive-change`. Quita el borrador y pide revisión.
6. **Revisión y merge** los hace **otra persona**. Quien implementó no mergea su PR.
7. **Después del merge**, el plan cuenta como cerrado y sus dependientes ya pueden empezar. Actualizar
   `estado.md` es tarea de coordinación, en un PR `docs:` aparte.

## 4. Qué significa «cerrado»

Deben cumplirse todas estas condiciones en `main`:

- PR mergeado.
- `Estado: cerrado` en el plan.
- Todos los pasos `- [x]` y todos los casos de *Pruebas* con evidencia.
- *Preguntas abiertas* vacías.
- Si es `openspec`, change archivado.

Un plan cerrado solo en una rama **no** desbloquea a nadie.

## 5. Reglas de seguridad

1. Nada empieza sin dependencias cerradas en `main` (norma §6.2).
2. Un plan = una rama con su nombre = un PR = un dueño.
3. Solo se toca la zona del propio plan (norma §6.4). Si hace falta otra, es pregunta abierta, no un cambio.
4. Las colas de `admin-web` y de `qa` van en serie, y cada servicio tiene un solo plan abierto a la vez
   ([`orden.md` §3](orden.md#3-zonas-exclusivas)).
5. Los planes `back-plataforma-*` tocan los siete servicios: mientras uno esté abierto, no se abre ningún plan de servicio.
6. Ante ambigüedad, **parar**. No se inventan endpoints, eventos, códigos de error, reglas ni valores (norma §6.6).
7. Nunca commit ni push directo a `main`, nunca `push --force`, nunca mergear el propio PR.
8. Si el plan que te toca está bloqueado, no esperes: toma el siguiente plan **listo** de [`orden.md`](orden.md).

## 6. Decisiones abiertas del registro

- **Mocks en el front.** La norma es contradictoria: §7 dice que un front «puede avanzar contra
  `contracts/openapi/*.yaml` con mocks», y §6.2 dice que no empieza un plan hasta cerrar todas sus
  dependencias. Este registro aplica **§6.2 (estricto)**: ningún front arranca con mocks antes de que su
  back esté cerrado. Si se quiere permitir mocks, hace falta un PR a `docs/plans/README.md` aprobado por una persona.
- **`front-admin-web-catalogo` no tiene archivo en `main`.** No puede ejecutarse. Además bloquea
  `qa-matriz-aceptacion`, porque ese plan depende de todos los F1–F4. Hasta que su plan entre, el calendario
  queda incompleto en la última tanda.

## 7. Índice de planes (agrupado sin mover archivos)

Los planes siguen en `docs/plans/<ámbito>-<tema>.md`. La norma prohíbe subcarpetas (§8) y el validador
lee solo el nivel raíz, así que este índice es la vista agrupada.

**Por fase**

- **F0 · Fundación (1):** [`infra-monorepo`](../plans/infra-monorepo.md)
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
- **F4 · Cierre y asíncronos (6):** [`back-reception-cierre`](../plans/back-reception-cierre.md) ·
  [`back-notification-worker`](../plans/back-notification-worker.md) ·
  [`back-reporting-proyecciones`](../plans/back-reporting-proyecciones.md) ·
  [`front-admin-web-cierre`](../plans/front-admin-web-cierre.md) ·
  [`front-admin-web-reportes`](../plans/front-admin-web-reportes.md) ·
  [`front-admin-web-area-cliente`](../plans/front-admin-web-area-cliente.md)
- **F5 · Aceptación (2):** [`qa-runbooks`](../plans/qa-runbooks.md) ·
  [`qa-matriz-aceptacion`](../plans/qa-matriz-aceptacion.md)

**Por ámbito**

- **infra (3):** `infra-monorepo`, `infra-compose`, `infra-gateway-eureka`
- **back (12):** `back-plataforma-comunes`, `back-plataforma-mensajeria`, `back-identity-auth`,
  `back-identity-personas`, `back-hotel-catalogo`, `back-reception-operacion`, `back-hotel-operaciones`,
  `back-inventory-stock`, `back-sales-ventas`, `back-reception-cierre`, `back-notification-worker`,
  `back-reporting-proyecciones`
- **front (8):** `front-public-web-landing`, `front-admin-web-base`, `front-admin-web-catalogo`,
  `front-admin-web-recepcion`, `front-admin-web-ventas`, `front-admin-web-cierre`,
  `front-admin-web-reportes`, `front-admin-web-area-cliente`
- **qa (2):** `qa-runbooks`, `qa-matriz-aceptacion`

Si entra un plan nuevo, su enlace se añade aquí en el mismo PR que lo planifica.

## 8. Tablero de GitHub (opcional)

Si se quiere una vista en vivo sin archivos extra: labels `fase-f0`…`fase-f5`, `ambito-*`, `modo-openspec`,
`bloqueado`; un milestone por fase; un issue por plan; y un Project kanban `Pendiente / En curso / En revisión / Mergeado`.
