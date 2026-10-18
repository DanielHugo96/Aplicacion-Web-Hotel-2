# Planes de ejecución

Aquí viven los planes de ejecución: documentos que dicen, antes de escribir código, qué se construye, en qué orden y cómo se sabe que terminó. Un plan no es código ni especificación. Quien planifica (humano o IA) lo escribe; quien ejecuta (humano o IA) lo sigue y lo cierra.

> La especificación dice qué es el sistema. El plan dice cómo lo construimos paso a paso.

Este documento es la norma de ambos. Un plan que no pasa `scripts/validar-planes.py` o incumple las secciones 5 a 7 no se acepta en PR.

| Archivo | Qué es | Quién lo cambia |
|---|---|---|
| `README.md` | Esta norma | Solo por PR revisado |
| `catalogo.md` | Planes previstos, dependencias, dueños de mensajes, endpoints y casos | Quien planifica, en el mismo PR que añade o cambia un plan |
| `_plantilla.md` | Esqueleto de un plan nuevo | Solo por PR revisado |
| `_prompts.md` | Secuencia de prompts para generar los 25 planes uno tras otro | Solo por PR revisado |
| `<ámbito>-<tema>.md` | Un plan | Planificador (estado `propuesto`) y ejecutora (estado y progreso) |

Spec: [`../especificacion`](../especificacion) · Contratos: [`../contracts`](../contracts) · Validador: [`../../scripts/validar-planes.py`](../../scripts/validar-planes.py)

## 1. Ámbito

Cada plan cubre un solo ámbito, y su nombre lo declara.

| Ámbito | Archivo | Qué cubre | Dónde se escribe el código |
|---|---|---|---|
| `back` | `back-<servicio>-<tema>.md` | Un microservicio: `identity`, `hotel`, `reception`, `inventory`, `sales`, `reporting` o `notification` | `services/<servicio>-service/` |
| `front` | `front-<app>-<tema>.md` | Una app Angular: `public-web` o `admin-web` | `apps/<app>/` |
| `infra` | `infra-<tema>.md` | Compose, PostgreSQL, brokers, Gateway, Eureka, configuración | `infra/`, `services/api-gateway/`, `services/discovery-server/` |
| `qa` | `qa-<tema>.md` | Matriz de aceptación, fixtures, simulacros y runbooks | `tests/`, `scripts/` |

- Una funcionalidad que necesita back y front son dos planes enlazados por `Depende de`.
- Un plan `back` toca un solo servicio. La excepción es `back-plataforma-*`: aplica una convención común (seguridad, errores, outbox, health) en varios servicios, sin lógica de negocio. Cada plan de servicio depende de él.

## 2. Fases

Las fases son los seis pasos del [primer incremento](../especificacion/07-revision-y-arranque.md). No se abre una fase sin cerrar la anterior. Dentro de una fase, los planes pueden ejecutarse en paralelo si sus dependencias están cerradas.

| Fase | Nombre | Objetivo | Criterio de salida | Modo por defecto |
|---|---|---|---|---|
| F0 | Fundación | Monorepo, versiones fijadas, plantillas y CI | Compila vacío con versiones fijadas; los PRs pasan CI | `directo` |
| F1 | Plataforma | Compose, Gateway y Eureka; convención común de seguridad y errores; outbox e inbox | `compose up` con healthchecks verdes; el Gateway enruta y responde 401 sin token; rutas internas no expuestas | `directo`, `openspec` en mensajería |
| F2 | Dominio base | identity y hotel con seeds; landing pública; login, layout y CRUD del panel | Login, aislamiento por sede, último ADMIN, catálogo público y CRUD operativos; eventos de identity y hotel salen por outbox | `directo` |
| F3 | Operación | reception (gates y check-in), hotel (mantenimiento y limpieza), inventory y sales (consumo con débito) | Check-in y venta PAGADA con débito y compensación probados; mantenimiento 200/202 y limpieza visibles en el panel | `openspec` en flujos críticos |
| F4 | Cierre y asíncronos | Cierre de estadía con cobro, notification, reporting y área de cliente | Cierre completo con penalidad y cobro; correos de demo; reportes al día con desglose por medio de pago | `openspec` en flujos críticos |
| F5 | Aceptación | Matriz completa, runbooks y endurecimiento | Matriz de 41 casos verde con evidencia; simulacros de DLQ, cierre atascado y restauración | `directo` |

El proyecto se construye desde cero con seeds limpios. No hay migración del monolito (ver [`catalogo.md` §5](catalogo.md#5-fuera-de-alcance)).

## 3. Modo: directo u OpenSpec

- **`directo`**: la especificación fija el contrato completo (endpoints, pantallas, validaciones, configuración) y el plan es una lista de implementación. Es el modo por defecto.
- **`openspec`**: la tarea depende de estados persistidos, concurrencia, dinero o stock, o de varios servicios, y un error es caro. Antes de codificar se escribe una propuesta OpenSpec.

Se usa `openspec` cuando el plan cumple **dos o más** de estas condiciones:

1. Toca dos o más servicios (Feign, eventos o comandos).
2. Mantiene una máquina de estados persistida (CAS, rechazos durables, lápidas).
3. Mueve dinero o stock (idempotencia, compensación, comparación exacta).
4. Depende de fiabilidad de mensajería (outbox e inbox, reintentos, DLT y DLQ, orden por agregado).
5. Depende de concurrencia (locks, versiones, entrega duplicada).
6. Proyecta o migra datos (watermarks, bootstrap, consistencia eventual).

El catálogo marca el modo recomendado de cada plan. Un plan puede pasar a `openspec` durante la planificación si aparecen esos riesgos; se justifica en el PR.

**Quién escribe la propuesta.** La planificadora la crea con los comandos de OpenSpec en la misma sesión que el plan, y ambos llegan juntos a revisión. Si no hay capacidad con ese asistente, puede usarse otro que siga esta norma. Lo que se revisa son los artefactos, no el modelo que los produjo.

**Artefactos.** `openspec/changes/<plan>/` con `proposal.md`, specs delta, `design.md` cuando haya decisiones técnicas, y `tasks.md`. Las tareas de `tasks.md` corresponden a los pasos del plan. El plan no repite ese detalle: lo apunta en el campo `Change`.

## 4. Proceso del planificador

1. **Elegir** el plan en [`catalogo.md` §1](catalogo.md#1-planes). Planificar no exige que sus dependencias estén cerradas; ejecutar sí (sección 6).
2. **Leer** lo obligatorio para su ámbito (tabla siguiente). La lectura es dirigida: de un OpenAPI se leen solo las rutas que el plan toca. `hotel.yaml` tiene más de 3.000 líneas.
3. **Redactar** con [`_plantilla.md`](_plantilla.md) y la estructura de la sección 5.
4. **Validar** con `python scripts/validar-planes.py`. Debe terminar sin errores. Los avisos se resuelven o se justifican en el PR.
5. **Revisar a mano** lo que el validador no puede: que el objetivo sea verificable, que cada paso tenga cómo comprobarse, y que el plan no decida reglas que la spec no fija.
6. **Publicar** en `docs/plans/` con estado `propuesto`, en un PR `docs:` que revisa una persona.

| Ámbito | Siempre | Además |
|---|---|---|
| Todos | Esta norma; [`03-contrato-comun.md`](../especificacion/03-contrato-comun.md); [`errors.md`](../contracts/errors.md) | — |
| `back` | `microservicios/<servicio>/api.md` y `base-de-datos.md`; las rutas de `contracts/openapi/<servicio>.yaml` que toca; [`05-mensajeria.md`](../especificacion/05-mensajeria.md) si emite o consume | [`04-flujos.md`](../especificacion/04-flujos.md) si toca check-in, venta, stock, cierre, limpieza o mantenimiento |
| `front` | [`02`](../especificacion/02-landing-y-panel.md), [`08`](../especificacion/08-admin-web.md) y [`DESIGN.md`](../DESIGN.md) (paleta, tipografía, componentes, tokens y checklist); la sección de su módulo en [`contracts/README.md`](../contracts/README.md); las rutas de `admin-web.yaml` o `public-web.yaml` que usa | [`06`](../especificacion/06-migracion-y-pruebas.md) §4 (cambios de frontend) |
| `infra` | [`01`](../especificacion/01-arquitectura.md); [`gateway-eureka-docker.md`](../especificacion/infraestructura/gateway-eureka-docker.md); [`configuracion.md`](../especificacion/infraestructura/configuracion.md) | [`06`](../especificacion/06-migracion-y-pruebas.md) §2 (orden de implementación) |
| `qa` | [`06`](../especificacion/06-migracion-y-pruebas.md); [`runbooks.md`](../especificacion/infraestructura/runbooks.md) | Contratos de los flujos que prueba |
| Modo `openspec` | La sección de `04-flujos.md` o `05-mensajeria.md` que describe la máquina de estados o la entrega | Los JSON Schema del flujo en [`contracts/events/`](../contracts/events/) |

## 5. Estructura de un plan

La cabecera es una tabla con estos campos. El validador comprueba que existan, que tengan valores válidos y que coincidan con el catálogo.

| Campo | Regla |
|---|---|
| Plan | Igual al nombre del archivo sin `.md` |
| Ámbito | `back`, `front`, `infra` o `qa`; igual al prefijo del nombre |
| Componente | Servicio o app exactos: `identity-service`, `admin-web`, `infra`; en `back-plataforma-*`, `services/*-service`; en `qa-*`, `tests` y `scripts` |
| Fase | `F0` a `F5` |
| Modo | `directo` u `openspec` |
| Estado | `propuesto`, `en curso` o `cerrado` (sección 8) |
| Depende de | Planes del catálogo entre comillas invertidas, o `—`. Debe incluir todas las dependencias que fija el catálogo |
| Spec | Enlaces a las secciones concretas, no solo al documento |
| Casos | IDs de [06](../especificacion/06-migracion-y-pruebas.md), con rangos (`OUT01–OUT06`); `Todos` solo en `qa-matriz-aceptacion`; o `—` |
| Change | Solo en `openspec`: `openspec/changes/<plan>/` |

Secciones, en este orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.

- **Pasos**: de 1 a 20 tareas `- [ ]`, cada una con su forma de verificación. Un plan que necesita más de 20 se divide en dos planes con su dependencia.
- **Preguntas abiertas**: decisiones que la spec no toma. Vacía si no hay ninguna. Mientras tenga contenido, el plan no se ejecuta.
- **Front**: el campo Spec cita [`../DESIGN.md`](../DESIGN.md); los pasos de UI usan sus tokens (§10) y una pantalla no se cierra sin pasar su checklist (§11).

## 6. Contrato con la ejecutora

Quien ejecuta (humano o IA) sigue estas reglas. El validador no las comprueba: las comprueba quien revisa el PR.

1. **Ejecuta solo planes `propuesto` ya mergeados** en `main`.
2. **Respeta las dependencias.** No empieza un plan hasta que todos sus `Depende de` están `cerrado`.
3. **Una rama y un PR por plan**, con el tema del plan como nombre (`identity-auth`), según [`guia-git.md`](../guia-git.md). Commits con prefijo `feat:`, `fix:`, `docs:` o `style:`. Nunca `push --force` ni commit directo a `main`.
4. **Toca solo sus archivos:**
   - `back`: `services/<servicio>-service/**`, `contracts/openapi/<servicio>.yaml` y los schemas de `contracts/events/` que el plan nombra.
   - `front`: `apps/<app>/**`.
   - `infra`: `infra/**`, `services/api-gateway/**`, `services/discovery-server/**`, `.env.example` y la sección «Verificación» del `README.md` raíz.
   - `qa`: `tests/**` y `scripts/**`.
   - `back-plataforma-*`: `services/*-service/**`, solo para aplicar la convención.
   - Siempre: su propio `docs/plans/<plan>.md` y, en la ejecución por IA, `docs/implementacion/decisiones/`.
   - Nunca: `docs/especificacion/**`, `docs/plans/README.md` ni `docs/plans/catalogo.md`. Si el plan exige cambiar uno de ellos, se registra como pregunta abierta.
   - En la ejecución por IA, el catálogo de errores **sí** puede ampliarse, pero solo de forma aditiva: quitar un código es un fallo de `scripts/validar-contratos.py` ([§6.11](#611-ejecución-por-ia-campaña-automática)).
5. **Progreso en el propio plan.** Marca `- [x]` en el mismo commit que completa el paso. No reordena pasos. Si un paso no se puede cumplir, lo deja sin marcar, escribe el motivo en *Preguntas abiertas* y se detiene. La ejecución por IA se aparta de esta regla en [§6.11](#611-ejecución-por-ia-campaña-automática).
6. **Ante ambigüedad, parar.** No inventa endpoints, eventos, códigos de error, reglas ni valores. Si la spec no decide, es una pregunta abierta. La ejecución por IA sustituye la parada por el protocolo de decisión de [§6.11](#611-ejecución-por-ia-campaña-automática).
7. **Verifica con los comandos canónicos.** La sección «Verificación» del `README.md` raíz, creada en `infra-monorepo`, lista el build, las pruebas, Compose y el validador. Los planes citan esos comandos; no definen otros.
8. **Evidencia por caso.** En *Pruebas*, cada caso de [06](../especificacion/06-migracion-y-pruebas.md) anota el comando o endpoint, el resultado y dónde está la salida. Sin evidencia, el caso no se marca.
9. **Cierre.** Cambia el estado a `cerrado` en el último PR del plan. Una persona revisa y hace el merge; la ejecutora no mergea su propio PR. La ejecución por IA sustituye esa revisión por la validación de la propia ejecutora; ver [§6.11](#611-ejecución-por-ia-campaña-automática).
10. **Sin secretos.** Nunca escribe credenciales reales; solo nombres de variables, como `DEMO_PASSWORD`.

### 6.11 Ejecución por IA (campaña automática)

Las reglas 5, 6 y 9 están escritas para una ejecutora humana que puede parar a preguntarle a
alguien. La campaña automática cambia eso: sus agentes **no tienen herramienta de preguntar**,
así que "parar y preguntar" no es una opción, es un bloqueo. Esta subsección define qué se hace
en su lugar.

Esta es una **excepción autorizada**, no una decisión del autor del PR. No se aplica a ejecución
humana: quien ejecuta un plan a mano sigue las reglas 5, 6 y 9 tal cual.

**Quién es quién**

| Agente | Papel |
|---|---|
| Orquestador | Conduce **un** plan. No elige el orden: lo recibe ya calculado. Delega, da por validado y hace el merge. |
| Ejecutora | Implementa los pasos de **un** plan, se valida a sí misma y lo deja `cerrado`. No sabe qué plan va después, ni mergea, ni pregunta. |

**Regla 6.11.1 — Protocolo de decisión.** Ante cualquier bloqueo, la ejecutora aplica:

- **Nivel 1 — decide y sigue.** Resoluble leyendo la especificación, los contratos o el código.
  Nombres, estructura interna, datos de prueba. No se registra nada.
- **Nivel 2 — decide, registra y sigue.** Contradicción entre documentos, o la spec no fija un
  detalle menor. Aplica la jerarquía de fuentes de [`../implementacion/README.md`](../implementacion/README.md) §1
  (norma > plan > catálogo > registro), elige **la opción menos invasiva que no rompe ningún plan
  cerrado ni ningún contrato existente**, y escribe la decisión en
  [`../implementacion/decisiones/`](../implementacion/decisiones/) con el formato de ese
  directorio. En *Preguntas abiertas* escribe `Ninguna (resuelto por IA: <resumen>)`.
- **Nivel 3 — decide, aplica y marca.** La spec no fija un endpoint, un evento, un código de error,
  una regla de negocio o un valor. **Aquí también se decide, se aplica y se marca el paso.** La
  campaña no se atasca y ningún plan queda a medias.

  Una decisión de contrato no es una decisión local, porque la consume otro módulo. Por eso lleva
  reglas propias:

  1. **Ampliar sí, cambiar no.** No se elimina ni se renombra una ruta, un método, un código de
     respuesta, un `operationId`, un schema, una propiedad ni un código de error que ya estuviera.
     Si hace falta cambiar algo existente, se **añade** y el antiguo se queda donde está.
  2. **Copiar a un hermano, no al criterio propio.** Un elemento de contrato nuevo copia la forma
     del elemento equivalente que ya existe en el mismo fichero: mismo envoltorio, mismos headers,
     mismos nombres de campo, mismo estilo de código de error.
  3. **La decisión vive en el contrato.** Un elemento nuevo se escribe en
     `docs/contracts/openapi/<servicio>.yaml`, en `docs/contracts/events/` con un schema nuevo, o en
     el catálogo de errores. Ese fichero **es** la decisión; la bitácora solo la justifica.
  4. **Comprobado por script, no por confianza.** `scripts/validar-contratos.py` falla si desaparece
     algo ya publicado. Corre en CI y en `scripts/preflight.py`. No es una sugerencia: es un fallo
     de build, y vale igual que la prohibición de §6 para inventar.
  5. **Queda escrito.** Cada decisión de nivel 3 tiene su archivo en
     [`../implementacion/decisiones/`](../implementacion/decisiones/) diciendo qué faltaba, qué se
     añadió y qué elemento existente se usó como modelo. El cuerpo del PR la resume en una sección
     `## Decisiones de contrato`.

  **Lo único que sigue sin poder decidirse:** el comportamiento de negocio que la spec no dice. No
  hay contrato que ampare la decisión y el acierto es una lotería. Si dos reservas solapan y la
  spec no dice qué pasa, la IA no puede saberlo. Solo en ese caso el paso se deja sin marcar, el
  motivo va a la bitácora y la respuesta es `BLOQUEADO:`. Cualquier otro bloqueo se resuelve
  decidiendo.

Lo que cambia con la campaña es **qué se hace cuando la spec no decide**, no qué está prohibido.
Inventar comportamiento de negocio sigue prohibido en los tres niveles. Lo que el nivel 3 autoriza
es otra cosa, más estrecha: **ampliar el contrato siguiendo las convenciones que ya están escritas
en él**, de forma aditiva y comprobada por script. No es inventar en abierto: es seguir la
convención que el propio repositorio ya escribió, y dejar constancia de qué se añadió y por qué.

**Regla 6.11.2 — El orden no lo decide un modelo.** El siguiente plan se calcula con
`scripts/proximo-plan.py` a partir del `Estado` y el `Depende de` de los planes, siguiendo el
calendario de [`../implementacion/orden.md`](../implementacion/orden.md) §2. Si un plan queda
`en curso`, se reanuda ese. El orquestador no elige, no salta y no improvisa el orden.

**Regla 6.11.3 — El merge.** El orquestador hace el merge de su propio PR en cuanto la
ejecutora termina sin dejar el plan `BLOQUEADO`. No hay agente revisor: la ejecutora valida su
propio trabajo y no hay una segunda lectura que lo apruebe. La ejecutora **nunca** hace merge. La
separación que la regla 9 protegía —que quien implementa no sea quien aprueba— se asume aquí como
riesgo explícito: quien revise los PRs a mano es quien tiene que mirarlo.

Esta excepción **no** se extiende a nada más. Siguen siendo inviolables: `main` nunca se toca
directamente, `git push --force` nunca, los documentos de la lista de §6.4 nunca, y las
versiones fijadas del `README.md` raíz nunca.

**Regla 6.11.4 — Trazabilidad.** Toda decisión de nivel 2 o 3 queda escrita. Un plan cerrado por
IA sin bitácora de sus decisiones de nivel 2 está incompleto, igual que uno sin evidencia en
*Pruebas*.

## 7. Reglas

- **La spec manda.** Un plan no corrige la spec. Si falta algo, lo registra como pregunta abierta. Los cambios de spec van en un PR propio que aprueba una persona.
- **La identidad nunca se propaga por headers** (`X-User-Id`, `X-Role`, `X-Hotel-Id`). Cada servicio la obtiene del JWT validado (algoritmo, issuer, audience, exp y scope), según [`03-contrato-comun.md`](../especificacion/03-contrato-comun.md). No usar ejemplos antiguos que propagaban esos headers.
- **Formatos, mensajes y validaciones** salen de [`02`](../especificacion/02-landing-y-panel.md), [`08`](../especificacion/08-admin-web.md) y [`errors.md`](../contracts/errors.md). El plan no los redefine.
- **Diseño del front.** Los planes `front-*` planifican con [`../DESIGN.md`](../DESIGN.md): quien planifica cita las secciones que aplican y quien ejecuta no inventa colores, tipografías, componentes ni movimientos fuera de ese sistema. Ante conflicto, manda la spec.
- **Casos de aceptación.** Un plan cita los IDs de [06](../especificacion/06-migracion-y-pruebas.md) que cubre, y el catálogo asigna cada caso a un plan. Nada se marca como ejecutado por estar escrito.
- **Mensajería.** Cada topic, comando y grupo de consumidor tiene el dueño que fija el catálogo. Un plan no implementa un consumidor que el catálogo asigna a otro.
- **Front.** Consume el Gateway (`localhost:8080`) y se cierra con integración real. Puede avanzar contra `contracts/openapi/*.yaml` con mocks, nunca contra puertos de servicios.
- **Alcance cerrado.** No se proponen reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server. Si un plan los necesitara, es una pregunta abierta.
- **Versiones.** Solo las fijadas en [`01-arquitectura.md`](../especificacion/01-arquitectura.md): Java 25, Boot 4.1.1, Cloud 2025.1.3, Angular 22.2.2, PostgreSQL 18.6, Kafka 4.3.1 y RabbitMQ 4.3.6. Sin `latest`. Re-verificar la compatibilidad de Spring Cloud en cada patch de 2025.1.x.
- **No copiar la spec.** El plan aporta orden, alcance, dependencias y criterios. La spec aporta el contenido; el plan la enlaza por sección.

## 8. Ciclo de vida

Tres estados, sin carpetas adicionales:

- `propuesto`: el plan está en `main`. Lo escribió la planificación.
- `en curso`: la ejecutora abrió su rama.
- `cerrado`: el PR de implementación está mergeado.

Los planes cerrados se quedan en esta carpeta como registro; el historial de git es el archivo. Si al cerrar aparece trabajo nuevo, se escribe un plan nuevo. Los changes de OpenSpec se archivan con su comando de archivo al cerrar el plan.
