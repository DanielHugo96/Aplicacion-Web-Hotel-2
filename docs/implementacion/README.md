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
| 4 | Campaña automática por IA | Los planes pendientes se ejecutan sin intervención humana, con un agente orquestador y ejecutoras por ámbito. Rompe dos reglas de por sí: **nadie pregunta** (§6 de la norma) y **el orquestador mergea su propio PR** (`guia-git.md`). Se sustituye la pregunta por el protocolo de decisión de tres niveles y la revisión humana por la validación de la propia ejecutora. Toda decisión queda escrita en [`decisiones/`](decisiones/). Andamiaje y procedimiento: [§9](#9-campaña-automática-por-ia). | Con cada tanda. Si un plan se repite en `BLOQUEADO`, o si la campaña amends un plan `cerrado`, se revisa aquí y se detiene. |

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

### Los agentes

| Agente | Ámbito | Modelo | Qué hace |
|---|---|---|---|
| `orquestador` | uno por plan | `opencode-go/deepseek-v4.1-flash` | Conduce, delega, da por validado y hace el merge. Si `main` está roto, encarga un plan de arreglo. No elige plan, no escribe código y no puede preguntar. |
| `planificador-arreglos` | cuando `main` se rompe | `opencode-go/deepseek-v4.1-flash` | Reproduce el fallo, redacta un plan `<ámbito>-arreglo-<tema>` con evidencia, lo añade al catálogo y lo publica en un PR `docs:`. No implementa y no mergea. |
| `plan-back` | `back`, `infra` | `opencode-go/longcat-2.5-preview-free` | Implementa los pasos de un plan, verifica y deja evidencia. |
| `plan-front` | `front` | `opencode-go/longcat-2.5-preview-free` | Igual, sobre `apps/<app>/`. |
| `plan-qa` | `qa` | `opencode-go/longcat-2.5-preview-free` | Igual, sobre `tests/**` y `scripts/**`. |
| `ejecutor` | respaldo: cualquier ámbito | `opencode-go/minimax-m3.1-flash-free#high` | Sustituye a las ejecutoras de ámbito cuando DeepSeek se queda sin cuota o sin créditos. Implementa el plan entero y deja la misma evidencia. |

El orquestador corre con la herramienta de preguntar deshabilitada por
configuración. "No preguntar" no es una instrucción del prompt: es una prohibición.

**Ya no hay agente revisor.** El orquestador mergea en cuanto la ejecutora termina sin dejar el
plan `BLOQUEADO`: la ejecutora es quien valida su propio trabajo, y no hay una segunda lectura
que lo apruebe. Es la excepción acotada de [`guia-git.md`](../guia-git.md) y de la regla
6.11.3 de la norma. La separación que la regla 9 protegía se asume aquí como riesgo
explícito, no se sustituye por otra revisión.

### El relevo: cuándo DeepSeek se queda sin cuota

DeepSeek V4.1 Flash se paga con créditos, y la campaña se los gasta. Cuando el modelo deja de
responder —cuota agotada, sin créditos o colgado—, la campaña **no se para**, porque hay un
ejecutor de respaldo.

| Qué | Qué hace |
|---|---|
| `ejecutor` | Implementa un plan de **cualquier** ámbito (`back`, `infra`, `front`, `qa`) sobre **MiniMax M3.1 Flash Free** (`opencode-go/minimax-m3.1-flash-free#high`). |

El relevo tiene dos formas:

- **En la sesión**, el orquestador delega en `ejecutor` con el modelo explícito de MiniMax (punto
  5.1 de su prompt). Si la sesión entera es la que no responde, quien está delante cambia el
  modelo de la sesión a `opencode-go/minimax-m3.1-flash-free#high` y relanza `/campana`: el plan a
  medias se retoma solo, porque `proximo-plan.py` devuelve primero los planes `en curso`.
- **En el runner desatendido**, [`scripts/orquestar.py`](../../scripts/orquestar.py) detecta el
  error de cuota, relanza el plan con el modelo de respaldo y sigue con el siguiente. Como los
  subagentes heredan el modelo de la sesión, el relevo pone a MiniMax a todo el equipo.

La variante de esfuerzo es `high` a propósito: con `low` el modelo se pierde en planes largos, y
con `xhigh` o `max` el servicio gratuito se satura. `high` es donde todavía razona lo suficiente
para seguir pasos en orden y verificar un build.

Lo que el relevo **no** cambia: la zona que se puede tocar, el protocolo de decisión de tres
niveles, la evidencia por caso, y el hecho de que quien implementa nunca mergea.

### Cómo se conduce: desde OpenCode

La campaña se maneja **desde una sesión de OpenCode**, no desde un script. El guion es este:

1. Abre OpenCode en el repositorio.
2. Selecciona el agente **`orquestador`** (con Tab, o escribiéndolo en el selector).
3. Elige el modelo **DeepSeek V4.1 Flash** (`opencode-go/deepseek-v4.1-flash`).
4. Escribe `/campana`, o simplemente dile `ejecuta la campaña`.

A partir de ahí el orquestador hace todo lo demás sin preguntar nada:

```
python scripts/preflight.py        # precondiciones; si falla, para y dice cuál
git switch main && git pull        # se sitúa
                                    # y repite:
                                    #   proximo-plan.py  → qué toca
                                    #   switch -c <plan> → reclamar
                                    #   Task → plan-back | plan-front | plan-qa
                                    #   gh pr create + gh pr merge
                                    #   PR docs: de estado
```

**El orden de los planes no lo decide el orquestador**: lo calcula `scripts/proximo-plan.py`
leyendo el `Estado` y el `Depende de`. El orquestador no elige, no salta y no improvisa.

**Cuando `main` se rompe, no se bloquea la campaña.** Si un plan ya `cerrado` deja `main` roto
(CI en `failure`, un servicio que no arranca, una verificación que no pasa) o hace falta
refactorizar antes de seguir, el orquestador llama a `planificador-arreglos`. Ese agente reproduce
el fallo, escribe `docs/plans/<ámbito>-arreglo-<tema>.md` con la evidencia, añade su fila al
catálogo y publica un PR `docs:`. El orquestador mergea ese PR y después ejecuta el arreglo como
cualquier plan; `proximo-plan.py` lo devuelve antes que los normales. La norma es la regla 6.11.5
de [`../plans/README.md`](../plans/README.md#611-ejecución-por-ia-campaña-automática).

Puedes acortar la campaña cuando quieras: `/campana back-identity-auth` la limita a un plan,
`/campana 3` a tres. Y si lo interrumpes, al volver a escribir `/campana` retoma por donde iba,
porque el script devuelve primero cualquier plan que quedó `en curso`.

### Ver lo que está pasando

Todo lo que hacen las ejecutoras ocurre en **sesiones hijas**, que desde la TUI se siguen con
<kbd>Leader</kbd>+<kbd>↓</kbd> (la primera) y <kbd>→</kbd>/<kbd>←</kbd> para ciclo. Ahí se ve el
razonamiento paso a paso de cada una, que es lo que el padre no recibe.

### El runner sin supervisión

[`scripts/orquestar.py`](../../scripts/orquestar.py) hace lo mismo en un bucle por proceso, para
dejar la campaña corriendo desatendida: arranca un servidor privado por plan, fija el modelo por
flag y comprueba al final que el plan quedó cerrado. Es opcional; la vía normal es el agente.

```bash
python scripts/orquestar.py                  # un plan
python scripts/orquestar.py --todos          # hasta que no quede ninguno
python scripts/orquestar.py --todos --seguir # enseñando lo que piensa el orquestador
```

Si el modelo principal se queda sin cuota, el runner relanza el plan con el de respaldo y sigue
por ahí:

```bash
python scripts/orquestar.py --todos --modelo-respaldo opencode-go/minimax-m3.1-flash-free#high
python scripts/orquestar.py --todos --sin-respaldo   # sin relevo: para si el modelo falla
```

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
- `docs/plans/catalogo.md`, **solo** `planificador-arreglos` y **solo** para añadir la fila de su
  plan de arreglo. Nunca para cambiar ni quitar filas existentes.
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
fracasar la campaña por la vía equivocada. Lo están por el preflight, que corta si el toolchain no
cumple lo que el repo declara. Antes sumaba una segunda defensa —el agente revisor rechazaba
cualquier diff que las tocase—; con el revisor eliminado, el preflight es la única, así que quien
revise los PRs tiene que mirar esto a mano.

### Por qué existe el preflight

El proyecto fija Java 25. Si el PATH de la sesión apunta a otro major, `mvnw verify` falla por un
motivo que no es del código, y lo más típico en un agente es leerlo como "el proyecto está mal" y
bajar `<release>25</release>` para que pase. Eso destruye el stack fijado sin que nada avise.

Hay dos defensas, y las dos hacen falta:

- `scripts/preflight.py` comprueba la máquina contra lo que declara el propio repositorio y avisa
  o corta antes de que un agente toque nada.
- [`scripts/mvn.sh`](../../scripts/mvn.sh) localiza el JDK correcto entre los instalados y lo
  exporta en cada invocación de Maven. **Las ejecutoras lo usan siempre en vez del `mvnw` a
  pelo**, así que da igual desde dónde se lance la campaña. No toca la configuración de la
  máquina.

Ninguna de las dos repara el entorno: eso lo hace una persona.

### Lo que la campaña no puede hacer

- No puede tocar `main` directamente, ni hacer `push --force`, ni editar los documentos de §6.4.
- No puede elegir el orden de los planes.
- No puede inventar un endpoint, un evento ni un código de error. Eso sigue prohibido en los
  tres niveles del protocolo de decisión.
- No puede corregir directamente un plan ya `cerrado` en `main`: para eso encarga un plan de
  arreglo a `planificador-arreglos`, que lo planifica y lo ejecuta una ejecutora.

Cuando un plan no se puede cerrar por una ambigüedad de negocio, la campaña lo registra en
[`decisiones/`](decisiones/), deja el PR sin mergear y pasa al siguiente. Cuando el problema es
código ya mergeado, no pasa de largo: encarga el arreglo, lo cierra y retoma lo que estaba
esperando. Se detiene sola, pero no se rompe sola.
