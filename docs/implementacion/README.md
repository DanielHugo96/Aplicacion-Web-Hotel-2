# Registro de implementación

Cómo se ejecutan los planes de [`../plans/`](../plans) entre varias personas sin pisarse.
Aquí no hay autoridad propia: todo lo que dice se deriva de la norma y de los planes.

| Archivo | Qué es |
|---|---|
| [`orden.md`](orden.md) | Calendario de los 25 planes, qué puede ir en paralelo y qué debe ir en serie. |
| [`estado.md`](estado.md) | Registro de cada plan ejecutado: estado, rama y PR. Si hay duda, manda el plan en `main`. |
| [`decisiones/`](decisiones/) | Bitácora de las decisiones que tuvo que tomar la IA al ejecutar un plan. |

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
7. **Después del merge**, el plan cuenta como cerrado y sus dependientes ya pueden empezar. Quien ejecutó el plan lo registra en
   [`estado.md`](estado.md) con su rama y su PR, en un PR `docs:` aparte.

## 4. Qué significa «cerrado»

Deben cumplirse todas estas condiciones en `main`:

- PR mergeado.
- `Estado: cerrado` en el plan.
- Todos los pasos `- [x]` y todos los casos de *Pruebas* con evidencia.
- *Preguntas abiertas* vacías.
- Si es `openspec`, change archivado.
- Registrado en [`estado.md`](estado.md) con su rama y su PR.

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

## 6. Decisiones del registro

Tomadas el 14/10/2026. Cada una tiene un momento de revisión.

| # | Tema | Decisión | Cuándo se revisa |
|---|---|---|---|
| 1 | Mocks en el front | Se aplica **§6.2 (estricto)**: ningún front arranca antes de tener cerradas sus dependencias, y no se usan mocks. §7 de la norma dice lo contrario; la contradicción se resolvería con un PR aprobado a `docs/plans/README.md`. | En T5. Si la cola de `admin-web` va atrasada, se propone ese PR con datos reales. |
| 2 | Modo de ejecución | **Seguro:** una cola de `admin-web` (siete planes) y una de `qa` (dos planes). Calendario de 15 tandas. | Pasar a modo rápido solo si hay una fecha de entrega que aprieta o si la cola de front se atrasa respecto al calendario. |
| 3 | `front-admin-web-catalogo` | **Resuelto el 15/10/2026.** El plan entró en `main` por el PR #40 (`aedeea3`) y su `Estado` es `propuesto`. Ya no está pendiente de merge ni bloquea a `qa-matriz-aceptacion` por esa razón. Lo que sí lo bloquea son sus dependencias: `front-admin-web-base`, `back-identity-personas` y `back-hotel-catalogo`, todas de T5 o T6. | Resuelta. |
| 4 | Campaña automática por IA | Los 20 planes pendientes se ejecutan sin intervención humana, con un agente orquestador, ejecutoras por ámbito y un agente revisor. Rompe dos reglas de por sí: **nadie pregunta** (§6 de la norma) y **el orquestador mergea su propio PR** (`guia-git.md`). Se sustituye la pregunta por el protocolo de decisión de tres niveles y la revisión humana por la del agente revisor. Toda decisión queda escrita en [`decisiones/`](decisiones/). Andamiaje y procedimiento: [§9](#9-campaña-automática-por-ia). | Con cada tanda. Si un plan se repite en `BLOQUEADO`, o si la campaña amends un plan `cerrado`, se revisa aquí y se detiene. |

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
  `front-admin-web-catalogo` (en T7: depende de `front-admin-web-base`, `back-identity-personas`
  y `back-hotel-catalogo`)
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

## 9. Campaña automática por IA

Los 20 planes pendientes se ejecutan sin intervención humana. Los agentes viven en
[`.opencode/agents/`](../../.opencode/agents) y el andamiaje que los conduce está en
[`scripts/`](../../scripts).

### Los tres agentes

| Agente | Ámbito | Modelo | Qué hace |
|---|---|---|---|
| `orquestador` | uno por plan | `space-bunny-free#xhigh` | Conduce, delega, pide revisión y hace el merge. No elige plan, no escribe código y no puede preguntar. |
| `plan-back` | `back`, `infra` | `space-bunny-free#max` | Implementa los pasos de un plan, verifica y deja evidencia. |
| `plan-front` | `front` | `space-bunny-free#high` | Igual, sobre `apps/<app>/`. |
| `plan-qa` | `qa` | `space-bunny-free#high` | Igual, sobre `tests/**` y `scripts/**`. |
| `revisor` | uno por plan | `space-bunny-free#xhigh` | Audita el resultado antes del merge. Sin permiso de escritura. Veredicto binario: `APROBADO` o `RECHAZADO`. |

El orquestador y el revisor corren con la herramienta de preguntar deshabilitada por
configuración. "No preguntar" no es una instrucción del prompt: es una prohibición.

### Cómo se conduce

```bash
python scripts/preflight.py        # comprueba la máquina. Si falla, no se arranca.
python scripts/proximo-plan.py     # dice cuál es el siguiente plan. No lo elige un modelo.
python scripts/orquestar.py        # ejecuta ese plan y para.
python scripts/orquestar.py --todos --max 5   # encadena hasta cinco planes.
```

Un `opencode run` por plan, no una sesión para veinte. Si la campaña se corta por cuota o por un
fallo, el siguiente `python scripts/orquestar.py` retoma exactamente donde se quedó, porque
`proximo-plan.py` devuelve primero cualquier plan que quedó `en curso`.

### Qué queda bloqueado, y a quién

La división es deliberada: **una regla global que estorba a las personas es una regla mala**, y por
eso las reglas de la campaña no son globales.

| Dónde | Qué bloquea | A quién |
|---|---|---|
| [`opencode.jsonc`](../../opencode.jsonc) (`policies`) | Empujar a `main`, empujar con `--force`, borrar ramas | A todo el mundo, agentes incluidos |
| [`agents/*.md`](../../.opencode/agents) (`permissions`) | Editar `docs/**` salvo el propio plan, el registro y los contratos; leer o escribir `.env`; `git rebase`, `reset --hard`, `clean`, `branch -D` | Solo a los agentes de la campaña |

Las de `policies` son literalmente la lista de prohibitions de [`guia-git.md`](../guia-git.md), y
aplican a personas y a agentes por igual. Las de la campaña viven en el frontmatter de cada agente,
así que **tú puedes seguir enmendando la norma desde tu sesión sin tocar nada**.

Qué puede escribir un agente de la campaña dentro de `docs/`:

- `docs/plans/<plan>.md`, el suyo.
- `docs/implementacion/**`: el estado, el calendario y la bitácora de decisiones.
- `docs/contracts/**`: solo de forma aditiva; quitar algo ya publicado es un fallo de
  `scripts/validar-contratos.py`.

Lo que no puede es `docs/especificacion/**`, `docs/plans/README.md`, `docs/plans/catalogo.md`,
`_plantilla.md` ni `docs/guia-git.md`. Eso no está en `opencode.jsonc`, sino en cada agente: una
persona puede enmendar la norma, un agente de la campaña no.

Comprobado con un agente real: escribir su propio plan, la bitácora y un contrato, permitido;
escribir `docs/plans/catalogo.md` y leer `.env`, bloqueados.

Las versiones fijadas (`services/pom.xml`, `apps/*/package.json`) **no** están bloqueadas por
configuración, porque un plan legítimo puede necesitar una dependencia nueva y bloquear eso haría
fracasar la campaña por la vía equivocada. Lo están por otras dos vías: el preflight corta si el
toolchain no cumple lo que el repo declara, y el agente revisor rechaza cualquier diff que las
toque.

### Por qué existe el preflight

El proyecto fija Java 25 y tú tienes Java 17. Un build falla y una IA muy lista lo lee como "el
proyecto está mal", baja `<release>25</release>` a 17 y todo queda en verde con el stack
destruido. `preflight.py` comprueba la máquina contra lo que declara el propio repositorio y
corta antes de que un agente toque nada. **Ninguna de sus precondiciones se arregla desde un
agente.**

### Lo que la campaña no puede hacer

- No puede tocar `main` directamente, ni hacer `push --force`, ni editar los documentos de §6.4.
- No puede elegir el orden de los planes.
- No puede inventar un endpoint, un evento ni un código de error. Eso sigue prohibido en los
  tres niveles del protocolo de decisión.
- No puede corregir un plan ya `cerrado` en `main`.

Cuando un plan no se puede cerrar, la campaña lo registra en [`decisiones/`](decisiones/), deja
el PR sin mergear y pasa al siguiente. Se detiene sola, pero no se rompe sola.
