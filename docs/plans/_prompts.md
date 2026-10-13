# Prompts para generar planes con IA

Copiar y pegar, rellenando los `{campos}`. Este documento no es norma: la norma es [`README.md`](README.md) y, ante cualquier conflicto, manda la norma (y por encima, la especificación).

## 1. ¿Todos los planes en un solo prompt o por partes?

**Por partes: un plan por prompt y por sesión.**

- Cada plan termina en **una rama y un PR** que revisa una persona (norma, secciones 4 y 6). Un prompt con 25 planes produce un diff irrevisable.
- El contexto del asistente es finito: mezclar planes de varios servicios y fases degrada la precisión y empieza a inventar reglas.
- La norma limita a **20 pasos por plan**; decidir si un plan se divide es una decisión plan a plan.
- Los planes `openspec` necesitan además su propuesta (`openspec/changes/<plan>/`) en el mismo PR; eso no se administra en lote.

Para avanzar rápido: una sesión por plan, y varias personas en paralelo (los planes sin dependencia abierta pueden planificarse a la vez; ejecutar es otra historia). Un «lote por fase» solo es aceptable si cada plan sale en su **propia rama, archivo y PR**; nunca dos planes en un archivo ni varias ramas en un PR.

## 2. Cómo se usa

1. Elige el plan en [`catalogo.md`](catalogo.md) §1 y copia su fila.
2. Rellena el prompt maestro (§3) con los datos de esa fila; si el modo es `openspec`, añade el bloque de §4.
3. Pega todo en el asistente, dentro del repo.
4. Revisa la salida con el prompt de §5 o a mano (norma §4.5).
5. Valida con `python scripts/validar-planes.py` desde la raíz: 0 errores.
6. Rama `<plan>`, commit `docs: agrega el plan <plan>`, push y PR a `main`. Nadie mergea su propio PR.

## 3. Prompt maestro

Rellenar `{PLAN}`, `{FASE}`, `{MODO}`, `{AMBITO}`, `{DEPENDENCIAS}`, `{CONTENIDO}` y `{CASOS}` con la fila del catálogo. `{LECTURAS}` se rellena con la fila del ámbito en esta tabla:

| Ámbito | Lista para `{LECTURAS}` |
|---|---|
| `infra` | `docs/especificacion/01-arquitectura.md`; `docs/especificacion/infraestructura/gateway-eureka-docker.md` y `configuracion.md`; si toca el arranque, `docs/especificacion/06-migracion-y-pruebas.md` §2. |
| `back` | `docs/especificacion/microservicios/<servicio>/api.md` y `base-de-datos.md`; de `docs/contracts/openapi/<servicio>.yaml` solo las rutas que el plan toca; `docs/especificacion/05-mensajeria.md` si emite o consume; `docs/especificacion/04-flujos.md` si toca check-in, venta, stock, cierre, limpieza o mantenimiento. |
| `front` | `docs/especificacion/02-landing-y-panel.md`, `docs/especificacion/08-admin-web.md` y `docs/DESIGN.md` (§10 tokens y §11 checklist por pantalla); la sección del módulo en `docs/contracts/README.md`; de `docs/contracts/openapi/admin-web.yaml` o `public-web.yaml` solo las rutas que usa. |
| `qa` | `docs/especificacion/06-migracion-y-pruebas.md` y `docs/especificacion/infraestructura/runbooks.md`; los contratos de los flujos que prueba. |

Prompt:

```text
Actúas como planificador del monorepo Aplicacion-Web-Hotel-2 (la documentación vive en docs/; este repo aún no tiene código). Redacta UN (1) plan de ejecución, y solo uno: `{PLAN}`.

Datos del plan (fila de docs/plans/catalogo.md):
- Fase: {FASE} · Modo: {MODO} · Ámbito: {AMBITO}
- Depende de: {DEPENDENCIAS}
- Contenido: {CONTENIDO}
- Casos de aceptación asignados: {CASOS}

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (norma; en especial secciones 4, 5, 7 y 8).
2. docs/plans/_plantilla.md (estructura exacta de salida).
3. docs/plans/catalogo.md, secciones 1 a 4: tu fila, y los dueños de mensajería, rutas y casos que te tocan.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. {LECTURAS}

Lectura dirigida: de un OpenAPI o de un api.md lee solo las rutas que el plan toca; no cargues documentos enteros que no correspondan.

Reglas que no puedes romper:
- Escribe en español. No copies la spec: enlázala por sección concreta (nunca solo al documento).
- Cabecera = tabla con: Plan (igual al nombre del archivo), Ámbito (igual al prefijo), Componente, Fase, Modo, Estado (`propuesto`), Depende de (con backticks, tal cual el catálogo), Spec, Casos, Change (`—` salvo openspec). Debe coincidir con el catálogo.
- Secciones exactas, en este orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: entre 1 y 20 tareas `- [ ]`, todas sin marcar, cada una con su verificación observable (comando o criterio). Si no caben en 20, divide el plan y explica la división.
- No inventes endpoints, eventos, códigos de error, formatos, mensajes ni reglas. Lo que la spec no decida va en «Preguntas abiertas»; mientras haya una, el plan no se ejecuta.
- Identidad: nunca cabeceras X-User-Id / X-Role / X-Hotel-Id; cada servicio la deriva del JWT validado (docs/especificacion/03-contrato-comun.md).
- Versiones: solo las fijadas en la spec; nada de `latest`.
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Alcance cerrado: no propongas reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server.
- No toques docs/especificacion/**, docs/contracts/errors.md ni docs/plans/README.md. Si tu fila del catálogo está incompleta o mal, corrígela solo en lo que respecta a este plan y déjalo dicho en el PR; jamás toques filas ajenas.
- Si docs/plans/{PLAN}.md ya existe, detente y avísalo: no sobrescribas trabajo ajeno.
- Archivo de salida: docs/plans/{PLAN}.md.

Entrega:
1. El archivo completo.
2. `python scripts/validar-planes.py` desde la raíz del repo: 0 errores; cada aviso, resuelto o justificado.
3. Auto-revisión (norma §4.5): ¿el objetivo es verificable? ¿cada paso tiene cómo comprobarse? ¿el plan decide algo que la spec ya fija? Corrige antes de entregar.
4. Rama `<plan>` desde `main` actualizado, commit `docs: agrega el plan <plan>` y push; el PR lo abre y lo revisa una persona.
```

## 4. Bloque adicional si el plan es modo `openspec`

Añadir al final del prompt maestro, antes de «Entrega».

```text
Además, este plan es modo `openspec` (riesgo alto: estados persistidos, dinero, stock, varios servicios o mensajería):
- Crea la propuesta antes o junto con el plan, con el flujo de OpenSpec: skill `openspec-propose` (o `/opsx-propose`) si tu asistente lo tiene; si no, `npx @fission-ai/openspec@1.13.1 new change {PLAN}` y completa con `openspec instructions <artefacto> --change {PLAN}`.
- openspec/changes/{PLAN}/ debe quedar con: proposal.md, specs delta (specs/<capability>/spec.md), design.md si hay decisiones técnicas, y tasks.md. Todo en español (lo exige openspec/config.yaml).
- Las tareas de tasks.md corresponden a los pasos del plan; no se contradicen.
- Lee además la sección de docs/especificacion/04-flujos.md o 05-mensajeria.md que describe la máquina de estados o la entrega, y los JSON Schema de docs/contracts/events/ que el plan nombra.
- En la cabecera del plan: Change: `openspec/changes/{PLAN}/`.
- Valida con `openspec validate {PLAN} --strict` (o `npx @fission-ai/openspec@1.13.1 validate ...`) sin errores.
- Plan y propuesta van juntos: misma rama, mismo PR.
```

## 5. Prompt de revisión (antes del PR)

Sirve para que otra persona (o su asistente) revise el plan sin editarlo.

```text
Revisa el plan docs/plans/{PLAN}.md contra docs/plans/README.md (secciones 5 y 7) y su fila de docs/plans/catalogo.md. No edites nada; entrega hallazgos y acción propuesta para cada uno.
1. Corre `python scripts/validar-planes.py` y reporta errores y avisos.
2. Cabecera: completa y coincidente con el catálogo (fase, ámbito, dependencias, casos, modo).
3. Pasos: entre 1 y 20, sin marcar, cada uno con verificación observable.
4. El plan no decide reglas que la spec no fija; cada cita apunta a una sección real y concreta.
5. Front: cita docs/DESIGN.md y sus §10 y §11; sin colores, tipografías ni componentes inventados.
6. Openspec: openspec/changes/{PLAN}/ existe con sus artefactos y tasks.md coincide con los pasos; `openspec validate {PLAN} --strict` en verde.
7. Preguntas abiertas: vacío, o concretas y sin decisión tomada por el plan.
```

## 6. Ejemplo rellenado: `infra-monorepo` (F0)

```text
Actúas como planificador del monorepo Aplicacion-Web-Hotel-2 (la documentación vive en docs/; este repo aún no tiene código). Redacta UN (1) plan de ejecución, y solo uno: `infra-monorepo`.

Datos del plan (fila de docs/plans/catalogo.md):
- Fase: F0 · Modo: `directo` · Ámbito: infra
- Depende de: —
- Contenido: estructura del monorepo de 01-arquitectura.md §1, Maven agregador y wrappers, Angular 22.2.2 en las dos apps, versiones fijadas, `.env.example`, CI mínima y README raíz con la sección «Verificación» (comandos canónicos).
- Casos de aceptación asignados: —

Lecturas obligatorias: docs/especificacion/01-arquitectura.md; docs/especificacion/infraestructura/gateway-eureka-docker.md y configuracion.md.

Aplica las reglas y la entrega del prompt maestro (docs/plans/_prompts.md §3). No copies la spec: enlaza por sección.
```

## 7. Errores que ocurren siempre (revisar a mano)

- Inventar códigos de error que no están en [`../contracts/errors.md`](../contracts/errors.md).
- Volver a ejemplos viejos con cabeceras `X-User-Id` / `X-Role` / `X-Hotel-Id`.
- Pasos que describen implementación en vez de orden verificable («hacer el CRUD»).
- Pasos sin verificación observable.
- Un plan `back` que toca más de un servicio, o un plan de negocio que se declara `back-plataforma-*`.
- Duplicar el contenido de la spec en el plan en lugar de enlazarlo.
