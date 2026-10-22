# Prompts secuenciales para generar los planes

Esta es la secuencia completa de generación: **25 prompts, uno por plan, en orden de fases (F0 → F5)**, más un prompt final de verificación. Se ejecutan uno tras otro; al pasar el último, la planificación está completa y solo queda **implementar** los planes (empezando por F0).

Un plan por prompt: el asistente trabaja con contexto limpio y cada salida cabe en una rama y un PR revisables. Este documento no es norma: la norma es [`README.md`](README.md) y, por encima, la especificación.

## Cómo se ejecuta la secuencia

1. Prepara una vez: `git switch main && git pull`. Para los prompts marcados **openspec**, ten disponible el CLI (`openspec`, o `npx @fission-ai/openspec@1.13.1`).
2. Abre una **sesión nueva** del asistente por prompt, dentro del repo, y pega el bloque completo tal cual está.
3. Revisa lo que entregue y corre el validador (`python scripts/validar-planes.py`: 0 errores).
4. El propio prompt pide rama `<plan>`, commit `docs: agrega el plan <plan>` y push. El PR lo abre y lo revisa una persona; nadie mergea su propio PR.
5. Puedes encadenar el siguiente prompt sin esperar el merge (planificar no exige dependencias cerradas), pero nunca mezcles dos planes en una rama ni en un archivo.
6. Al terminar el prompt 25, ejecuta el **prompt final de verificación**. Si pasa, no queda ningún plan por escribir: solo queda implementar, en orden de fases.

El orden sigue [`catalogo.md`](catalogo.md): cada plan aparece después de sus dependencias (planificables o no, el orden ayuda a que la lectura sea coherente). El «Contenido» de cada bloque resume fielmente su fila del catálogo (puede omitir enlaces).

---

## 1. `infra-monorepo` (F0 · infra · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `infra-monorepo`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `infra-monorepo` · Ámbito: `infra` · Componente: `infra`
- Fase: `F0` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: —
- Casos: —
- Contenido: estructura del monorepo de 01-arquitectura.md §1, Maven agregador y wrappers, Angular 22.2.2 en las dos apps, versiones fijadas, .env.example, CI mínima y README raíz con la sección «Verificación» (comandos canónicos).

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma infra: docs/especificacion/01-arquitectura.md; docs/especificacion/infraestructura/gateway-eureka-docker.md; docs/especificacion/infraestructura/configuracion.md; y docs/especificacion/06-migracion-y-pruebas.md §2 (orden de implementación).
Lectura dirigida: de los api.md y OpenAPI, solo lo que este plan toca.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/infra-monorepo.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `infra-monorepo` desde `main` actualizado, commit `docs: agrega el plan infra-monorepo`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 2. `infra-compose` (F1 · infra · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `infra-compose`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `infra-compose` · Ámbito: `infra` · Componente: `infra`
- Fase: `F1` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `infra-monorepo`
- Casos: —
- Contenido: PostgreSQL 18.6 con 7 bases y roles (postgres/init), Kafka 4.3.1 en KRaft, RabbitMQ 4.3.6 con management, Mailpit, healthchecks y orden de arranque de la configuración.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma infra: docs/especificacion/01-arquitectura.md; docs/especificacion/infraestructura/configuracion.md (bases, brokers, healthchecks, orden de arranque); docs/especificacion/infraestructura/gateway-eureka-docker.md; y docs/especificacion/06-migracion-y-pruebas.md §2.
Lectura dirigida: de los api.md y OpenAPI, solo lo que este plan toca.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/infra-compose.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `infra-compose` desde `main` actualizado, commit `docs: agrega el plan infra-compose`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 3. `infra-gateway-eureka` (F1 · infra · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `infra-gateway-eureka`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `infra-gateway-eureka` · Ámbito: `infra` · Componente: `infra`
- Fase: `F1` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `infra-compose`
- Casos: `SEC08`
- Contenido: rutas del Gateway por prefijo, rate limit público de 60/min, timeouts, CORS, /actuator sin exponer, Eureka con credenciales propias.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma infra: docs/especificacion/infraestructura/gateway-eureka-docker.md (completo); docs/especificacion/01-arquitectura.md; docs/especificacion/infraestructura/configuracion.md; y docs/especificacion/06-migracion-y-pruebas.md §2.
Lectura dirigida: de los api.md y OpenAPI, solo lo que este plan toca.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/infra-gateway-eureka.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `infra-gateway-eureka` desde `main` actualizado, commit `docs: agrega el plan infra-gateway-eureka`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 4. `back-plataforma-comunes` (F1 · back · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `back-plataforma-comunes`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `back-plataforma-comunes` · Ámbito: `back` · Componente: los siete servicios (`services/*-service`)
- Fase: `F1` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `infra-compose`
- Casos: `SEC06`
- Contenido: convención aplicada en cada servicio: resource server JWT RS256 (iss, aud, exp, scope), envelope y códigos genéricos, correlationId, patrón de idempotencia, /actuator/health y logs estructurados.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md (completo: JWT, envelope, correlación, idempotencia, health y logs) y docs/contracts/errors.md.
5. Norma back: docs/especificacion/01-arquitectura.md §1 (estructura de services/); de los api.md de los servicios, solo lo relativo a /actuator/health y seguridad.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Este plan aplica una convención común sin lógica de negocio; no implementa funcionalidad de ningún servicio.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/back-plataforma-comunes.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `back-plataforma-comunes` desde `main` actualizado, commit `docs: agrega el plan back-plataforma-comunes`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 5. `back-plataforma-mensajeria` (F1 · back · openspec)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `back-plataforma-mensajeria`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `back-plataforma-mensajeria` · Ámbito: `back` · Componente: los siete servicios (`services/*-service`)
- Fase: `F1` · Modo: `openspec` · Estado: `propuesto` · Change: `openspec/changes/back-plataforma-mensajeria/`
- Depende de: `infra-compose`, `back-plataforma-comunes`
- Casos: `MSG01–MSG02`
- Contenido: outbox e inbox por servicio (DDL de 03-contrato-comun.md), poller de 5 s con lote de 50, backoff, alertas, orden por agregado, exchanges hotel.commands.v1 y hotel.dlx.v1, DLT y DLQ, JSON Schemas.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §3, §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md (el DDL de outbox/inbox) y docs/contracts/errors.md.
5. Norma back: docs/especificacion/05-mensajeria.md completo (entrega, reintentos, DLT/DLQ, orden por agregado); docs/contracts/events/envelope.v1.schema.json y command-envelope.v1.schema.json.
Lectura dirigida: de los api.md y OpenAPI, solo lo que este plan toca.

Método openspec (obligatorio, este plan es modo `openspec`):
- Crea la propuesta con el flujo de OpenSpec: skill `openspec-propose` (o `/opsx-propose`) si tu asistente lo tiene; si no, `npx @fission-ai/openspec@1.13.1 new change back-plataforma-mensajeria` y completa con `openspec instructions <artefacto> --change back-plataforma-mensajeria`.
- `openspec/changes/back-plataforma-mensajeria/` queda con proposal.md, specs delta, design.md si hay decisiones técnicas, y tasks.md, todo en español (lo exige openspec/config.yaml).
- Las tareas de tasks.md corresponden a los pasos del plan.
- Valida con `openspec validate back-plataforma-mensajeria --strict` sin errores.
- Plan y propuesta van juntos: misma rama, mismo PR.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/back-plataforma-mensajeria.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `back-plataforma-mensajeria` desde `main` actualizado, commit `docs: agrega el plan back-plataforma-mensajeria`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 6. `back-identity-auth` (F2 · back · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `back-identity-auth`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `back-identity-auth` · Ámbito: `back` · Componente: `identity-service`
- Fase: `F2` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `back-plataforma-mensajeria`
- Casos: `SEC02`
- Contenido: login, registro público (fuerza CLIENTE y emite notificacion.bienvenida por outbox), /api/auth/me, cambio de clave (Idempotency-Key + HMAC), social con 410, JWKS, token máquina POST /internal/auth/token, rate limits y BCrypt.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma back: docs/especificacion/microservicios/identity/api.md y base-de-datos.md; de docs/contracts/openapi/identity.yaml solo las rutas que toca; docs/especificacion/05-mensajeria.md por notificacion.bienvenida y su JSON Schema.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Un plan back toca un solo servicio: este plan solo toca Identity.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/back-identity-auth.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `back-identity-auth` desde `main` actualizado, commit `docs: agrega el plan back-identity-auth`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 7. `back-hotel-catalogo` (F2 · back · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `back-hotel-catalogo`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `back-hotel-catalogo` · Ámbito: `back` · Componente: `hotel-service`
- Fase: `F2` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `back-identity-auth`, `back-plataforma-mensajeria`
- Casos: `SEC03`, `LP01`, `CRUD01`, `OCC04`
- Contenido: sedes, pisos, categorías y habitaciones (altas, edición, imágenes), estados físicos fijos, catálogo público /api/public/**, rutas internas /internal/habitaciones y /internal/hoteles, hotel.sede.v1, HabitacionCreada, HabitacionSnapshot, consumidor hotel-ocupacion-v1.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma back: docs/especificacion/microservicios/hotel/api.md y base-de-datos.md; docs/especificacion/05-mensajeria.md por hotel.sede.v1, HabitacionCreada, HabitacionSnapshot y el consumidor hotel-ocupacion-v1; los JSON Schema de esos eventos en docs/contracts/events/; de docs/contracts/openapi/hotel.yaml solo las rutas que toca.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Un plan back toca un solo servicio: este plan solo toca Hotel.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/back-hotel-catalogo.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `back-hotel-catalogo` desde `main` actualizado, commit `docs: agrega el plan back-hotel-catalogo`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 8. `back-identity-personas` (F2 · back · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `back-identity-personas`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `back-identity-personas` · Ámbito: `back` · Componente: `identity-service`
- Fase: `F2` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `back-identity-auth`, `back-hotel-catalogo`
- Casos: `SEC01`, `SEC07`
- Contenido: personas y tipos de persona con mutex del último ADMIN, sedes asignadas validadas contra hotel, GET /internal/clientes/{id}, auditoría PERSONA_SEARCH y PERSONA_READ, anonimización manual, identity.persona.v1.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma back: docs/especificacion/microservicios/identity/api.md y base-de-datos.md; de docs/especificacion/microservicios/hotel/api.md solo la validación de sedes asignadas (rutas /internal/hoteles); docs/especificacion/05-mensajeria.md por identity.persona.v1 y su JSON Schema.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Un plan back toca un solo servicio: este plan solo toca Identity.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/back-identity-personas.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `back-identity-personas` desde `main` actualizado, commit `docs: agrega el plan back-identity-personas`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 9. `front-public-web-landing` (F2 · front · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `front-public-web-landing`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `front-public-web-landing` · Ámbito: `front` · Componente: `public-web`
- Fase: `F2` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `back-hotel-catalogo`
- Casos: `LP01`, `LP02`
- Contenido: landing: inicio, sedes, detalle de sede, tipos de habitación, contacto, privacidad, textos y assets demo, tarifa «Desde S/ 70.00», locale es-PE.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma front: docs/especificacion/02-landing-y-panel.md; docs/DESIGN-public-web.md (tokens y checklist por pantalla); la sección de public-web en docs/contracts/README.md; de docs/contracts/openapi/public-web.yaml solo las rutas que usa; docs/especificacion/06-migracion-y-pruebas.md §4.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Front: cita ../DESIGN-public-web.md; los pasos de UI usan sus tokens y la pantalla no se cierra sin pasar su checklist; no inventes colores, tipografías, componentes ni movimientos.
- Front consume el Gateway (localhost:8080); puede avanzar contra los OpenAPI con mocks, nunca contra puertos de servicios.
- La landing es informativa: sin reserva, sin pago y sin disponibilidad garantizada.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/front-public-web-landing.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `front-public-web-landing` desde `main` actualizado, commit `docs: agrega el plan front-public-web-landing`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 10. `front-admin-web-base` (F2 · front · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `front-admin-web-base`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `front-admin-web-base` · Ámbito: `front` · Componente: `admin-web`
- Fase: `F2` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `back-identity-auth`
- Casos: —
- Contenido: login y registro, guards por rol, layout y menú de 08-admin-web.md §5, interceptor (Idempotency-Key, ETag, 401, 202), mensajes desde errors.md, pipes de moneda y fecha, perfil y clave.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma front: docs/especificacion/08-admin-web.md (§5 layout y menú; roles y login) y 02-landing-y-panel.md; docs/DESIGN-admin-web.md (tokens y checklist por pantalla); la sección de admin-web en docs/contracts/README.md; de docs/contracts/openapi/admin-web.yaml solo las rutas que usa; docs/especificacion/06-migracion-y-pruebas.md §4.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Front: cita ../DESIGN-admin-web.md; los pasos de UI usan sus tokens y la pantalla no se cierra sin pasar su checklist; no inventes colores, tipografías, componentes ni movimientos.
- Front consume el Gateway (localhost:8080); puede avanzar contra los OpenAPI con mocks, nunca contra puertos de servicios.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/front-admin-web-base.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `front-admin-web-base` desde `main` actualizado, commit `docs: agrega el plan front-admin-web-base`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 11. `front-admin-web-catalogo` (F2 · front · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `front-admin-web-catalogo`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `front-admin-web-catalogo` · Ámbito: `front` · Componente: `admin-web`
- Fase: `F2` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `front-admin-web-base`, `back-identity-personas`, `back-hotel-catalogo`
- Casos: —
- Contenido: personas y roles; sedes, pisos, categorías y habitaciones con sus formularios, validaciones y estados de 08-admin-web.md.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma front: docs/especificacion/08-admin-web.md (módulos de personas y catálogo); docs/DESIGN-admin-web.md (tokens y checklist por pantalla); la sección de admin-web en docs/contracts/README.md; de docs/contracts/openapi/admin-web.yaml solo las rutas que usa; docs/especificacion/06-migracion-y-pruebas.md §4.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Front: cita ../DESIGN-admin-web.md; los pasos de UI usan sus tokens y la pantalla no se cierra sin pasar su checklist; no inventes colores, tipografías, componentes ni movimientos.
- Front consume el Gateway (localhost:8080); puede avanzar contra los OpenAPI con mocks, nunca contra puertos de servicios.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/front-admin-web-catalogo.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `front-admin-web-catalogo` desde `main` actualizado, commit `docs: agrega el plan front-admin-web-catalogo`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 12. `back-reception-operacion` (F3 · back · openspec)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `back-reception-operacion`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `back-reception-operacion` · Ámbito: `back` · Componente: `reception-service`
- Fase: `F3` · Modo: `openspec` · Estado: `propuesto` · Change: `openspec/changes/back-reception-operacion/`
- Depende de: `back-identity-personas`, `back-hotel-catalogo`, `back-plataforma-mensajeria`
- Casos: `OCC01–OCC03`, `CLN01`, `STK06`
- Contenido: room_gate y su sincronización desde hotel (grupo reception-gates-v1), check-in con adelanto y medio de pago, editar salida, lectura por estadía, campo vencida, bloqueos (POST /internal/habitaciones/{id}/bloqueos), operaciones de consumo y lápidas (recepcion.operacion-finalizar), notificacion.checkin.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §3, §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma back: docs/especificacion/microservicios/reception/api.md y base-de-datos.md; docs/especificacion/04-flujos.md (secciones de gates y check-in); docs/especificacion/05-mensajeria.md (reception-gates-v1, recepcion.operacion-finalizar, notificacion.checkin, reception.recepcion.v1 CheckIn) y sus JSON Schema en docs/contracts/events/.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Método openspec (obligatorio, este plan es modo `openspec`):
- Crea la propuesta con el flujo de OpenSpec: skill `openspec-propose` (o `/opsx-propose`) si tu asistente lo tiene; si no, `npx @fission-ai/openspec@1.13.1 new change back-reception-operacion` y completa con `openspec instructions <artefacto> --change back-reception-operacion`.
- `openspec/changes/back-reception-operacion/` queda con proposal.md, specs delta, design.md si hay decisiones técnicas, y tasks.md, todo en español (lo exige openspec/config.yaml).
- Las tareas de tasks.md corresponden a los pasos del plan.
- Valida con `openspec validate back-reception-operacion --strict` sin errores.
- Plan y propuesta van juntos: misma rama, mismo PR.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Un plan back toca un solo servicio: este plan solo toca Reception.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/back-reception-operacion.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `back-reception-operacion` desde `main` actualizado, commit `docs: agrega el plan back-reception-operacion`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 13. `back-hotel-operaciones` (F3 · back · openspec)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `back-hotel-operaciones`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `back-hotel-operaciones` · Ámbito: `back` · Componente: `hotel-service`
- Fase: `F3` · Modo: `openspec` · Estado: `propuesto` · Change: `openspec/changes/back-hotel-operaciones/`
- Depende de: `back-reception-operacion`
- Casos: `OCC02`, `OCC03`, `CLN01–CLN03`
- Contenido: mantenimiento y baja con blockId (200, 202, 409), reactivación, consulta de operaciones, ciclo de limpieza (limpiezas, limpieza-completada), limpieza.solicitada, HabitacionLista y HabitacionHabilitada.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §3, §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma back: docs/especificacion/microservicios/hotel/api.md (mantenimiento, reactivación, operaciones y limpiezas) y base-de-datos.md; docs/especificacion/04-flujos.md (mantenimiento y ciclo de limpieza); docs/especificacion/05-mensajeria.md (limpieza.solicitada, HabitacionLista, HabitacionHabilitada) y sus JSON Schema.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Método openspec (obligatorio, este plan es modo `openspec`):
- Crea la propuesta con el flujo de OpenSpec: skill `openspec-propose` (o `/opsx-propose`) si tu asistente lo tiene; si no, `npx @fission-ai/openspec@1.13.1 new change back-hotel-operaciones` y completa con `openspec instructions <artefacto> --change back-hotel-operaciones`.
- `openspec/changes/back-hotel-operaciones/` queda con proposal.md, specs delta, design.md si hay decisiones técnicas, y tasks.md, todo en español (lo exige openspec/config.yaml).
- Las tareas de tasks.md corresponden a los pasos del plan.
- Valida con `openspec validate back-hotel-operaciones --strict` sin errores.
- Plan y propuesta van juntos: misma rama, mismo PR.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Un plan back toca un solo servicio: este plan solo toca Hotel.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/back-hotel-operaciones.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `back-hotel-operaciones` desde `main` actualizado, commit `docs: agrega el plan back-hotel-operaciones`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 14. `back-inventory-stock` (F3 · back · openspec)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `back-inventory-stock`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `back-inventory-stock` · Ámbito: `back` · Componente: `inventory-service`
- Fase: `F3` · Modo: `openspec` · Estado: `propuesto` · Change: `openspec/changes/back-inventory-stock/`
- Depende de: `back-hotel-catalogo`, `back-plataforma-mensajeria`
- Casos: `SEC05`, `CRUD01`, `STK01–STK02`, `STK04`
- Contenido: productos y existencias por sede, ajustes y movimientos, descuento y restitución con locks e idempotencia (/internal/stock), catálogo interno, stock.restituir, inventory.stock.v1 e inventory.movimiento.v1.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §3, §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma back: docs/especificacion/microservicios/inventory/api.md y base-de-datos.md; docs/especificacion/04-flujos.md (descuento y restitución de stock); docs/especificacion/05-mensajeria.md (stock.restituir, inventory.stock.v1, inventory.movimiento.v1) y sus JSON Schema.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Método openspec (obligatorio, este plan es modo `openspec`):
- Crea la propuesta con el flujo de OpenSpec: skill `openspec-propose` (o `/opsx-propose`) si tu asistente lo tiene; si no, `npx @fission-ai/openspec@1.13.1 new change back-inventory-stock` y completa con `openspec instructions <artefacto> --change back-inventory-stock`.
- `openspec/changes/back-inventory-stock/` queda con proposal.md, specs delta, design.md si hay decisiones técnicas, y tasks.md, todo en español (lo exige openspec/config.yaml).
- Las tareas de tasks.md corresponden a los pasos del plan.
- Valida con `openspec validate back-inventory-stock --strict` sin errores.
- Plan y propuesta van juntos: misma rama, mismo PR.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Un plan back toca un solo servicio: este plan solo toca Inventory.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/back-inventory-stock.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `back-inventory-stock` desde `main` actualizado, commit `docs: agrega el plan back-inventory-stock`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 15. `back-sales-ventas` (F3 · back · openspec)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `back-sales-ventas`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `back-sales-ventas` · Ámbito: `back` · Componente: `sales-service`
- Fase: `F3` · Modo: `openspec` · Estado: `propuesto` · Change: `openspec/changes/back-sales-ventas/`
- Depende de: `back-reception-operacion`, `back-inventory-stock`, `back-plataforma-mensajeria`
- Casos: `SEC04`, `STK03–STK07`, `PAY01–PAY02`
- Contenido: carrito, catálogo privado, ventas (alta, pago, anulación impagada), operaciones y recuperación cada 30 s, compensación (grupo sales-compensacion-v1), productores de recepcion.operacion-finalizar y stock.restituir, sales.venta.v1, resumen y confirmación de cierre (/internal/cierres).

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §3, §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma back: docs/especificacion/microservicios/sales/api.md y base-de-datos.md; docs/especificacion/04-flujos.md (venta, pago y compensación); docs/especificacion/05-mensajeria.md (sales.venta.v1, sales-compensacion-v1, recepcion.operacion-finalizar, stock.restituir) y sus JSON Schema.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Método openspec (obligatorio, este plan es modo `openspec`):
- Crea la propuesta con el flujo de OpenSpec: skill `openspec-propose` (o `/opsx-propose`) si tu asistente lo tiene; si no, `npx @fission-ai/openspec@1.13.1 new change back-sales-ventas` y completa con `openspec instructions <artefacto> --change back-sales-ventas`.
- `openspec/changes/back-sales-ventas/` queda con proposal.md, specs delta, design.md si hay decisiones técnicas, y tasks.md, todo en español (lo exige openspec/config.yaml).
- Las tareas de tasks.md corresponden a los pasos del plan.
- Valida con `openspec validate back-sales-ventas --strict` sin errores.
- Plan y propuesta van juntos: misma rama, mismo PR.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Un plan back toca un solo servicio: este plan solo toca Sales.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/back-sales-ventas.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `back-sales-ventas` desde `main` actualizado, commit `docs: agrega el plan back-sales-ventas`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 16. `front-admin-web-recepcion` (F3 · front · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `front-admin-web-recepcion`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `front-admin-web-recepcion` · Ámbito: `front` · Componente: `admin-web`
- Fase: `F3` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `front-admin-web-base`, `back-reception-operacion`, `back-hotel-operaciones`
- Casos: —
- Contenido: listado y filtros de estadías, detalle, check-in (buscar cliente, crear estadía), editar salida, tablero de limpieza y acciones de mantenimiento con respuestas 200, 202 y 409.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma front: docs/especificacion/08-admin-web.md (módulo de recepción); docs/DESIGN-admin-web.md (tokens y checklist por pantalla); la sección de admin-web en docs/contracts/README.md; de docs/contracts/openapi/admin-web.yaml solo las rutas que usa; docs/especificacion/06-migracion-y-pruebas.md §4.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Front: cita ../DESIGN-admin-web.md; los pasos de UI usan sus tokens y la pantalla no se cierra sin pasar su checklist; no inventes colores, tipografías, componentes ni movimientos.
- Front consume el Gateway (localhost:8080); puede avanzar contra los OpenAPI con mocks, nunca contra puertos de servicios.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/front-admin-web-recepcion.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `front-admin-web-recepcion` desde `main` actualizado, commit `docs: agrega el plan front-admin-web-recepcion`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 17. `front-admin-web-ventas` (F3 · front · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `front-admin-web-ventas`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `front-admin-web-ventas` · Ámbito: `front` · Componente: `admin-web`
- Fase: `F3` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `front-admin-web-base`, `back-sales-ventas`, `back-inventory-stock`
- Casos: —
- Contenido: productos y movimientos; ventas (nueva, listado, detalle, pago, anulación impagada); polling de operaciones 202.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma front: docs/especificacion/08-admin-web.md (módulo de ventas); docs/DESIGN-admin-web.md (tokens y checklist por pantalla); la sección de admin-web en docs/contracts/README.md; de docs/contracts/openapi/admin-web.yaml solo las rutas que usa; docs/especificacion/06-migracion-y-pruebas.md §4.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Front: cita ../DESIGN-admin-web.md; los pasos de UI usan sus tokens y la pantalla no se cierra sin pasar su checklist; no inventes colores, tipografías, componentes ni movimientos.
- Front consume el Gateway (localhost:8080); puede avanzar contra los OpenAPI con mocks, nunca contra puertos de servicios.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/front-admin-web-ventas.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `front-admin-web-ventas` desde `main` actualizado, commit `docs: agrega el plan front-admin-web-ventas`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 18. `back-reception-cierre` (F4 · back · openspec)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `back-reception-cierre`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `back-reception-cierre` · Ámbito: `back` · Componente: `reception-service`
- Fase: `F4` · Modo: `openspec` · Estado: `propuesto` · Change: `openspec/changes/back-reception-cierre/`
- Depende de: `back-reception-operacion`, `back-sales-ventas`
- Casos: `OUT01–OUT06`
- Contenido: cotización, cierre VALIDANDO → CONFIRMANDO → COMPLETADO con cálculo congelado, penalidad y montoRecibido, recuperación cada 30 s, anulación de check-in, archivado, reception.recepcion.v1 (SalidaRegistrada y RecepcionActualizada de anulación), limpieza.solicitada y notificacion.checkout.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §3, §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma back: docs/especificacion/microservicios/reception/api.md (cotización, salida, anulación y eliminación) y base-de-datos.md; docs/especificacion/04-flujos.md (cierre de estadía); docs/especificacion/05-mensajeria.md (reception.recepcion.v1, limpieza.solicitada, notificacion.checkout) y sus JSON Schema.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Método openspec (obligatorio, este plan es modo `openspec`):
- Crea la propuesta con el flujo de OpenSpec: skill `openspec-propose` (o `/opsx-propose`) si tu asistente lo tiene; si no, `npx @fission-ai/openspec@1.13.1 new change back-reception-cierre` y completa con `openspec instructions <artefacto> --change back-reception-cierre`.
- `openspec/changes/back-reception-cierre/` queda con proposal.md, specs delta, design.md si hay decisiones técnicas, y tasks.md, todo en español (lo exige openspec/config.yaml).
- Las tareas de tasks.md corresponden a los pasos del plan.
- Valida con `openspec validate back-reception-cierre --strict` sin errores.
- Plan y propuesta van juntos: misma rama, mismo PR.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Un plan back toca un solo servicio: este plan solo toca Reception.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/back-reception-cierre.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `back-reception-cierre` desde `main` actualizado, commit `docs: agrega el plan back-reception-cierre`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 19. `back-notification-worker` (F4 · back · openspec)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `back-notification-worker`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `back-notification-worker` · Ámbito: `back` · Componente: `notification-service`
- Fase: `F4` · Modo: `openspec` · Estado: `propuesto` · Change: `openspec/changes/back-notification-worker/`
- Depende de: `back-plataforma-mensajeria`
- Casos: `NOT01–NOT03`
- Contenido: consumo de las tres colas notificacion.*, inbox, jobs con lease, SMTP hacia Mailpit, reintentos, CLI --admin auditada, purga por retención y readiness con Rabbit.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §3, §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma back: docs/especificacion/microservicios/notification/api.md y base-de-datos.md; docs/especificacion/05-mensajeria.md (colas notificacion.*); los JSON Schema de docs/contracts/events/ de bienvenida, checkin y checkout.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Método openspec (obligatorio, este plan es modo `openspec`):
- Crea la propuesta con el flujo de OpenSpec: skill `openspec-propose` (o `/opsx-propose`) si tu asistente lo tiene; si no, `npx @fission-ai/openspec@1.13.1 new change back-notification-worker` y completa con `openspec instructions <artefacto> --change back-notification-worker`.
- `openspec/changes/back-notification-worker/` queda con proposal.md, specs delta, design.md si hay decisiones técnicas, y tasks.md, todo en español (lo exige openspec/config.yaml).
- Las tareas de tasks.md corresponden a los pasos del plan.
- Valida con `openspec validate back-notification-worker --strict` sin errores.
- Plan y propuesta van juntos: misma rama, mismo PR.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Un plan back toca un solo servicio: este plan solo toca Notification (worker).
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/back-notification-worker.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `back-notification-worker` desde `main` actualizado, commit `docs: agrega el plan back-notification-worker`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 20. `back-reporting-proyecciones` (F4 · back · openspec)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `back-reporting-proyecciones`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `back-reporting-proyecciones` · Ámbito: `back` · Componente: `reporting-service`
- Fase: `F4` · Modo: `openspec` · Estado: `propuesto` · Change: `openspec/changes/back-reporting-proyecciones/`
- Depende de: `back-identity-personas`, `back-hotel-catalogo`, `back-hotel-operaciones`, `back-reception-cierre`, `back-inventory-stock`, `back-sales-ventas`, `back-plataforma-mensajeria`
- Casos: `SEC03`, `REP01–REP03`
- Contenido: consumidor reporting-proyeccion-v1 (6 topics), bootstrap con manifiesto y watermarks, /api/reportes/*, ingresosHoy (casos A, B y C), cobros por medio de pago, degradación y REPORTING_NOT_READY.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §3, §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma back: docs/especificacion/microservicios/reporting/api.md y base-de-datos.md; docs/especificacion/05-mensajeria.md (grupo reporting-proyeccion-v1 y los 6 topics que consume) y los JSON Schema correspondientes en docs/contracts/events/.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Método openspec (obligatorio, este plan es modo `openspec`):
- Crea la propuesta con el flujo de OpenSpec: skill `openspec-propose` (o `/opsx-propose`) si tu asistente lo tiene; si no, `npx @fission-ai/openspec@1.13.1 new change back-reporting-proyecciones` y completa con `openspec instructions <artefacto> --change back-reporting-proyecciones`.
- `openspec/changes/back-reporting-proyecciones/` queda con proposal.md, specs delta, design.md si hay decisiones técnicas, y tasks.md, todo en español (lo exige openspec/config.yaml).
- Las tareas de tasks.md corresponden a los pasos del plan.
- Valida con `openspec validate back-reporting-proyecciones --strict` sin errores.
- Plan y propuesta van juntos: misma rama, mismo PR.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Un plan back toca un solo servicio: este plan solo toca Reporting.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/back-reporting-proyecciones.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `back-reporting-proyecciones` desde `main` actualizado, commit `docs: agrega el plan back-reporting-proyecciones`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 21. `front-admin-web-cierre` (F4 · front · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `front-admin-web-cierre`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `front-admin-web-cierre` · Ámbito: `front` · Componente: `admin-web`
- Fase: `F4` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `front-admin-web-recepcion`, `back-reception-cierre`
- Casos: —
- Contenido: detalle de estadía, cotización, penalidad, monto recibido y medio de pago, anulación de check-in, archivado, estados 202 y 409, aviso «cierre pendiente, no volver a cobrar».

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma front: docs/especificacion/08-admin-web.md (módulo de cierre); docs/DESIGN-admin-web.md (tokens y checklist por pantalla); la sección de admin-web en docs/contracts/README.md; de docs/contracts/openapi/admin-web.yaml solo las rutas que usa; docs/especificacion/06-migracion-y-pruebas.md §4.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Front: cita ../DESIGN-admin-web.md; los pasos de UI usan sus tokens y la pantalla no se cierra sin pasar su checklist; no inventes colores, tipografías, componentes ni movimientos.
- Front consume el Gateway (localhost:8080); puede avanzar contra los OpenAPI con mocks, nunca contra puertos de servicios.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/front-admin-web-cierre.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `front-admin-web-cierre` desde `main` actualizado, commit `docs: agrega el plan front-admin-web-cierre`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 22. `front-admin-web-reportes` (F4 · front · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `front-admin-web-reportes`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `front-admin-web-reportes` · Ámbito: `front` · Componente: `admin-web`
- Fase: `F4` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `front-admin-web-base`, `back-reporting-proyecciones`
- Casos: —
- Contenido: dashboard y reportes (ventas, ocupación, cobros, bajo stock), exportación en el frontend, avisos de consistencia eventual y de 503.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma front: docs/especificacion/08-admin-web.md (módulo de reportes); docs/DESIGN-admin-web.md (tokens y checklist por pantalla); la sección de admin-web en docs/contracts/README.md; de docs/contracts/openapi/admin-web.yaml solo las rutas que usa; docs/especificacion/06-migracion-y-pruebas.md §4.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Front: cita ../DESIGN-admin-web.md; los pasos de UI usan sus tokens y la pantalla no se cierra sin pasar su checklist; no inventes colores, tipografías, componentes ni movimientos.
- Front consume el Gateway (localhost:8080); puede avanzar contra los OpenAPI con mocks, nunca contra puertos de servicios.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/front-admin-web-reportes.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `front-admin-web-reportes` desde `main` actualizado, commit `docs: agrega el plan front-admin-web-reportes`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 23. `front-admin-web-area-cliente` (F4 · front · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `front-admin-web-area-cliente`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `front-admin-web-area-cliente` · Ámbito: `front` · Componente: `admin-web`
- Fase: `F4` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `front-admin-web-base`, `back-reception-operacion`, `back-sales-ventas`
- Casos: —
- Contenido: mi estadía, catálogo y carrito, mis ventas con polling, mi perfil y clave, límites del rol C de 08-admin-web.md §2.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma front: docs/especificacion/08-admin-web.md §2 (límites del rol C); docs/DESIGN-admin-web.md (tokens y checklist por pantalla); la sección de admin-web en docs/contracts/README.md; de docs/contracts/openapi/admin-web.yaml solo las rutas que usa; docs/especificacion/06-migracion-y-pruebas.md §4.
Lectura dirigida: de los api.md y OpenAPI, solo las rutas y tablas que este plan toca.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Mensajería: solo los topics, comandos y grupos que el catálogo asigna a este plan.
- Front: cita ../DESIGN-admin-web.md; los pasos de UI usan sus tokens y la pantalla no se cierra sin pasar su checklist; no inventes colores, tipografías, componentes ni movimientos.
- Front consume el Gateway (localhost:8080); puede avanzar contra los OpenAPI con mocks, nunca contra puertos de servicios.
- El área de cliente no muestra nada administrativo (rol C); respeta la matriz de permisos de 08-admin-web.md.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/front-admin-web-area-cliente.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `front-admin-web-area-cliente` desde `main` actualizado, commit `docs: agrega el plan front-admin-web-area-cliente`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 24. `qa-matriz-aceptacion` (F5 · qa · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `qa-matriz-aceptacion`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `qa-matriz-aceptacion` · Ámbito: `qa` · Componente: `tests`, `scripts`
- Fase: `F5` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `infra-compose`, `infra-gateway-eureka`, `back-plataforma-comunes`, `back-plataforma-mensajeria`, `back-identity-auth`, `back-identity-personas`, `back-hotel-catalogo`, `front-public-web-landing`, `front-admin-web-base`, `front-admin-web-catalogo`, `back-reception-operacion`, `back-hotel-operaciones`, `back-inventory-stock`, `back-sales-ventas`, `front-admin-web-recepcion`, `front-admin-web-ventas`, `back-reception-cierre`, `back-notification-worker`, `back-reporting-proyecciones`, `front-admin-web-cierre`, `front-admin-web-reportes`, `front-admin-web-area-cliente`

Aviso: el catálogo fija «F1–F4» como dependencia; el validador la expande a esos 22 planes y falla si falta alguno en la cabecera. Cópialos todos tal cual.

- Casos: `Todos` (este plan ejecuta la matriz completa; no es dueño de ningún caso).
- Contenido: ejecutar la matriz completa con fixtures F1–F9 y evidencia por caso; las correcciones de spec que surjan se registran como preguntas abiertas, no como cambios directos.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §6, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1, la sección 4 (casos) y lo que te toca de §2–§3.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma qa: docs/especificacion/06-migracion-y-pruebas.md completo (fixtures y matriz de §5); docs/especificacion/infraestructura/runbooks.md como referencia de los escenarios; los contratos de los flujos que prueba.
Lectura dirigida: de los api.md y OpenAPI, solo lo que la matriz referencia.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Este plan no cambia la especificación: cada corrección que aparezca se registra como pregunta abierta.
- Este plan solo escribe en tests/ y scripts/.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/qa-matriz-aceptacion.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `qa-matriz-aceptacion` desde `main` actualizado, commit `docs: agrega el plan qa-matriz-aceptacion`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

## 25. `qa-runbooks` (F5 · qa · directo)

```text
Rol: eres el planificador del monorepo Aplicacion-Web-Hotel-2; la documentación vive en docs/ y este repo aún no tiene código. Tarea: redactar UN (1) plan de ejecución, solo uno: `qa-runbooks`. No implementes código; al terminar, detente.

Datos del plan (fila de docs/plans/catalogo.md; van tal cual en la cabecera):
- Plan: `qa-runbooks` · Ámbito: `qa` · Componente: `tests`, `scripts`
- Fase: `F5` · Modo: `directo` · Estado: `propuesto` · Change: `—`
- Depende de: `back-plataforma-mensajeria`, `back-reception-cierre`, `back-hotel-operaciones`, `back-notification-worker`, `back-reporting-proyecciones`
- Casos: —
- Contenido: simulacros de RB1 a RB4, alertas de la tabla de runbooks, backup y restauración.

Lecturas obligatorias, en este orden:
1. docs/plans/README.md completo (la norma; en especial §4, §5, §7 y §8).
2. docs/plans/_plantilla.md (estructura exacta de la salida).
3. docs/plans/catalogo.md: tu fila de §1 y lo que te toca de §2–§4.
4. docs/especificacion/03-contrato-comun.md y docs/contracts/errors.md.
5. Norma qa: docs/especificacion/infraestructura/runbooks.md completo (RB1–RB4, alertas, backup y restauración); docs/especificacion/06-migracion-y-pruebas.md; los contratos de los flujos que simula.
Lectura dirigida: de los api.md y OpenAPI, solo lo que los simulacros referencia.

Reglas no negociables:
- Español; la spec se enlaza por sección concreta, sin copiarla.
- Cabecera: tabla con los valores de arriba; completa `Spec` con enlaces a secciones concretas.
- Secciones, en orden: 1 Objetivo · 2 Alcance · 3 Contexto · 4 Pasos · 5 Contratos afectados · 6 Pruebas · 7 Preguntas abiertas · 8 Criterio de terminado.
- Pasos: 1 a 20 tareas `- [ ]` sin marcar, cada una con verificación observable (comando o criterio).
- Nada inventado: endpoints, eventos, códigos de error, formatos y mensajes salen de la spec; lo que la spec no decida va a «Preguntas abiertas».
- Identidad solo por JWT validado (03-contrato-comun.md); nunca cabeceras X-User-Id / X-Role / X-Hotel-Id.
- Versiones: solo las fijadas; alcance cerrado (sin reservas, pasarela de pago, facturación, fidelización, app móvil, Kubernetes, Redis, Debezium ni Config Server).
- Este plan solo escribe en tests/ y scripts/.
- No toques docs/especificacion/**, docs/contracts/errors.md, docs/plans/README.md ni docs/plans/_prompts.md; tu fila del catálogo, solo si está mal y lo justificas en el PR.
- Si docs/plans/qa-runbooks.md ya existe, detente y avísalo.

Cierre obligatorio:
1. Corre `python scripts/validar-planes.py` desde la raíz: 0 errores; cada aviso, resuelto o justificado.
2. Auto-revisión (norma §4.5): objetivo verificable, cada paso comprobable, sin reglas que la spec ya fija.
3. Entrega el archivo. Después: rama `qa-runbooks` desde `main` actualizado, commit `docs: agrega el plan qa-runbooks`, push y PR a `main`; lo revisa una persona y no lo mergees tú.
```

---

## Prompt final — verificación de cierre

Ejecutarlo cuando los 25 prompts anteriores estén hechos y sus PRs mergeados.

```text
Tarea: verificar que la planificación del proyecto está completa. No edites nada; reporta.

1. Corre `python scripts/validar-planes.py` desde la raíz: debe terminar con 0 error(es) y 0 aviso(s), y revisar 25 planes.
2. Confirma que existen los 25 archivos de docs/plans/ (los del catálogo §1) y que todos tienen Estado `propuesto`.
3. Para cada plan: contrasta su cabecera con su fila de catalogo.md (fase, ámbito, componente, dependencias, casos, modo, change).
4. Lista todas las «Preguntas abiertas» no vacías: cada una bloquea la ejecución de su plan hasta resolverse.
5. Si el CLI de OpenSpec está disponible: confirma que existen openspec/changes/<plan>/ para los 8 planes openspec y que `openspec validate --all --strict` pasa.
6. Reporta el resultado. Si todo está en verde, concluye: «Planificación completa: 0 planes por escribir; solo queda implementar, empezando por infra-monorepo (F0) y siguiendo las fases F0 → F5».
```

## Errores comunes (revisar a mano antes de cada PR)

- Códigos de error inventados que no están en [`../contracts/errors.md`](../contracts/errors.md).
- Ejemplos viejos con cabeceras `X-User-Id` / `X-Role` / `X-Hotel-Id`.
- Pasos que describen implementación en vez de orden verificable («hacer el CRUD»), o sin verificación observable.
- Un plan `back` que toca más de un servicio, o un plan de negocio declarado `back-plataforma-*`.
- Duplicar la spec en el plan en lugar de enlazarla por sección.
