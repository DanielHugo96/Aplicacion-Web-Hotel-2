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

La campaña se maneja **desde una sesión de OpenCode**: se elige el agente `orquestador`, se elige
el modelo DeepSeek V4.1 Flash (`opencode-go/deepseek-v4.1-flash`) y se escribe `/campana`. Los agentes viven en [`.opencode/agents/`](.opencode/agents),
el comando en [`.opencode/commands/campana.md`](.opencode/commands/campana.md) y las reglas que no
se pueden negociar en [`opencode.jsonc`](opencode.jsonc).

Si un plan ya `cerrado` rompe `main` o hace falta refactorizar antes de seguir, el orquestador llama
al agente `planificador-arreglos` (también DeepSeek V4.1 Flash) para que genere un plan de arreglo
con evidencia. Ese plan se ejecuta antes de retomar los planes normales.

Si se agotan los créditos de DeepSeek V4.1 Flash, la campaña no se para: el agente `ejecutor`
(`.opencode/agents/ejecutor.md`), que corre sobre MiniMax M3.1 Flash Free
(`opencode-go/minimax-m3.1-flash-free#high`), implementa los planes de cualquier ámbito. En la
sesión, cambia el modelo a MiniMax; en el runner desatendido, `scripts/orquestar.py` hace el
relevo solo.

Si eres un agente trabajando en este repositorio:

- **No decidas el orden de los planes.** Léelo de `python scripts/proximo-plan.py`.
- **Usa `bash scripts/mvn.sh` para Maven, nunca `mvnw` a pelo.** El proyecto fija Java 25 y el PATH puede apuntar a otro.
- **No preguntes.** Tu configuración no te lo permite. Aplica el protocolo de decisión de tres niveles de [`docs/plans/README.md`](docs/plans/README.md) §6.11: decide y sigue (nivel 1), decide y registra en [`docs/implementacion/decisiones/`](docs/implementacion/decisiones/) (nivel 2), o marca bloqueado y pasa al plan siguiente (nivel 3).
- **No inventes** endpoints, eventos, códigos de error, reglas ni valores. Eso está prohibido en los tres niveles.
- **No toques** las versiones fijadas (`services/pom.xml`, `apps/*/package.json`, el `README.md` raíz). Si el build falla por el entorno, es un bloqueo, no un plan.
- **No merges tu propio trabajo.** Solo el orquestador lo hace, y solo cuando la ejecutora termina sin dejar el plan bloqueado.
