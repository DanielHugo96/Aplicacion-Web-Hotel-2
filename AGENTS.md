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
