# AGENTS.md

Instrucciones para agentes de IA que trabajan en este repositorio.

## Reglas de Git

- Todo cambio debe seguir las reglas de la guía [`docs/guia-git.md`](docs/guia-git.md).
- Nunca trabajar ni hacer commit directo en `main`; cada tarea va en su propia rama.
- Nombres de rama en minúsculas, con guiones, según la guía.
- Mensajes de commit con prefijo (`feat:`, `fix:`, `docs:`, `style:`).
- Nunca usar `git push --force` ni borrar ramas no mergeadas.
- No mergear PRs propios sin revisión.

## Documentación

- La especificación vive en `docs/`. Punto de entrada: [`docs/ESPECIFICACION.md`](docs/ESPECIFICACION.md).
- Los planes de ejecución siguen [`docs/plans/README.md`](docs/plans/README.md).
- El registro de implementación y su orden están en [`docs/implementacion/`](docs/implementacion/README.md).
- Los contratos de frontend están en `docs/contracts/`.

## Ejecución de planes

- Antes de ejecutar un plan, sigue el método y las reglas de [`docs/implementacion/README.md`](docs/implementacion/README.md) y el orden de [`docs/implementacion/orden.md`](docs/implementacion/orden.md).
- Cada vez que se ejecute un plan, regístralo en [`docs/implementacion/estado.md`](docs/implementacion/estado.md) con su estado, rama y PR.
- El registro va en un PR `docs:` aparte, después de que el PR de implementación se mergea. Nunca dentro de ese PR.

## Campaña automática por IA

Los agentes de IA de este repositorio viven en [`.opencode/agents/`](.opencode/agents) y las reglas duras que no pueden negociación en [`opencode.jsonc`](opencode.jsonc). Si eres un agente trabajando en este repositorio:

- **No decidas el orden de los planes.** Léelo de `python scripts/proximo-plan.py`.
- **No preguntes.** Tu configuración no te lo permite. Aplica el protocolo de decisión de tres niveles de [`docs/plans/README.md`](docs/plans/README.md) §6.11: decide y sigue (nivel 1), decide y registra en [`docs/implementacion/decisiones/`](docs/implementacion/decisiones/) (nivel 2), o marca bloqueado y pasa al plan siguiente (nivel 3).
- **No inventes** endpoints, eventos, códigos de error, reglas ni valores. Eso está prohibido en los tres niveles.
- **No toques** las versiones fijadas (`services/pom.xml`, `apps/*/package.json`, el `README.md` raíz). Si el build falla por el entorno, es un bloqueo, no un plan.
- **No merges tu propio trabajo.** Solo el orquestador lo hace, y solo tras el visto bueno del agente revisor.
