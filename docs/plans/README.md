# Planes de ejecución

Aquí viven los planes de ejecución: documentos que dicen, antes de escribir código, qué se construye, en qué orden y cómo se sabe que terminó. Un plan no es código ni especificación. Quien planifica (humano o IA) lo escribe; quien ejecuta (humano o IA) lo sigue y lo cierra.

> La especificación dice qué es el sistema. El plan dice cómo lo construimos paso a paso.

Este documento es la norma de ambos. Un plan que no pasa `scripts/validar-planes.py` o incumple las secciones 5 a 7 no se acepta en PR.

| Archivo | Qué es | Quién lo cambia |
|---|---|---|
| `README.md` | Esta norma | Solo por PR revisado |
| `catalogo.md` | Planes previstos, dependencias, dueños de mensajes, endpoints y casos | Quien planifica, en el mismo PR que añade o cambia un plan |
| `_plantilla.md` | Esqueleto de un plan nuevo | Solo por PR revisado |
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
| `front` | [`02`](../especificacion/02-landing-y-panel.md) y [`08`](../especificacion/08-admin-web.md); la sección de su módulo en [`contracts/README.md`](../contracts/README.md); las rutas de `admin-web.yaml` o `public-web.yaml` que usa | [`06`](../especificacion/06-migracion-y-pruebas.md) §4 (cambios de frontend) |
| `infra` | [`01`](../especificacion/01-arquitectura.md); [`gateway-eureka-docker.md`](../especificacion/infraestructura/gateway-eureka-docker.md); [`configuracion.md`](../especificacion/infraestructura/configuracion.md) | [`06`](../especificacion/06-migracion-y-pruebas.md) §2 (orden de implementación) |
| `qa` | [`06`](../especificacion/06-migracion-y-pruebas.md); [`runbooks.md`](../especificacion/infraestructura/runbooks.md) | Contratos de los flujos que prueba |
| Modo `openspec` | La sección de `04-flujos.md` o `05-mensajeria.md` que describe la máquina de estados o la entrega | Los JSON Schema del flujo en [`contracts/events/`](../contracts/events/) |

## 5. Estructura de un plan

La cabecera es una tabla con estos campos. El validador comprueba que existan, que tengan valores válidos y que coincidan con el catálogo.

| Campo | Regla |
|---|---|
| Plan | Igual al nombre del archivo sin `.md` |
| Ámbito | `back`, `front`, `infra` o `qa`; igual al prefijo del nombre |
| Componente | Servicio o app exactos: `identity-service`, `admin-web`, `infra` |
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
   - Siempre: su propio `docs/plans/<plan>.md`.
   - Nunca: `docs/especificacion/**`, `docs/contracts/errors.md`, `docs/plans/README.md` ni `docs/plans/catalogo.md`. Si el plan exige cambiar uno de ellos, se registra como pregunta abierta.
5. **Progreso en el propio plan.** Marca `- [x]` en el mismo commit que completa el paso. No reordena pasos. Si un paso no se puede cumplir, lo deja sin marcar, escribe el motivo en *Preguntas abiertas* y se detiene.
6. **Ante ambigüedad, parar.** No inventa endpoints, eventos, códigos de error, reglas ni valores. Si la spec no decide, es una pregunta abierta.
7. **Verifica con los comandos canónicos.** La sección «Verificación» del `README.md` raíz, creada en `infra-monorepo`, lista el build, las pruebas, Compose y el validador. Los planes citan esos comandos; no definen otros.
8. **Evidencia por caso.** En *Pruebas*, cada caso de [06](../especificacion/06-migracion-y-pruebas.md) anota el comando o endpoint, el resultado y dónde está la salida. Sin evidencia, el caso no se marca.
9. **Cierre.** Cambia el estado a `cerrado` en el último PR del plan. Una persona revisa y hace el merge; la ejecutora no mergea su propio PR.
10. **Sin secretos.** Nunca escribe credenciales reales; solo nombres de variables, como `DEMO_PASSWORD`.

## 7. Reglas

- **La spec manda.** Un plan no corrige la spec. Si falta algo, lo registra como pregunta abierta. Los cambios de spec van en un PR propio que aprueba una persona.
- **La identidad nunca se propaga por headers** (`X-User-Id`, `X-Role`, `X-Hotel-Id`). Cada servicio la obtiene del JWT validado (algoritmo, issuer, audience, exp y scope), según [`03-contrato-comun.md`](../especificacion/03-contrato-comun.md). No usar ejemplos antiguos que propagaban esos headers.
- **Formatos, mensajes y validaciones** salen de [`02`](../especificacion/02-landing-y-panel.md), [`08`](../especificacion/08-admin-web.md) y [`errors.md`](../contracts/errors.md). El plan no los redefine.
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
