# Planes de ejecución

## Qué es esta carpeta

Aquí viven los planes de ejecución: documentos cortos que explican, antes de escribir código, qué hay que hacer, en qué orden y cómo se sabe que terminó.

Un plan no es código ni documentación del sistema. Es la hoja de ruta de una tarea concreta: la escribe quien la va a hacer, la usa mientras trabaja y se archiva cuando la tarea se cierra.

> La especificación dice qué es el sistema. El plan dice cómo lo construimos paso a paso.
> Spec en ../especificacion, planes en esta carpeta.

## Convención de nombres

- Un plan = una tarea, en minúsculas con guiones: `identity-login.md`, `reception-checkin.md`.
- Si sigue el orden de `../especificacion/07-revision-y-arranque.md`, indica el incremento: `inc03-identity-hotel.md`.

## Contenido mínimo de cada plan

1. **Objetivo**: qué entrega, en 1-2 líneas.
2. **Alcance**: qué documentos de la spec cubre (enlaces a `../especificacion/...` y `../contracts/...`).
3. **Pasos**: lista ordenada y verificable.
4. **Contratos afectados**: OpenAPI, eventos, DDL, matriz de `06-migracion-y-pruebas.md` (con IDs de caso).
5. **Criterio de terminado**: cómo se comprueba (comandos, endpoints, evidencias).

## Reglas al escribir planes

- La spec manda: no inventar endpoints, eventos ni reglas. Si falta algo, se corrige la spec en el mismo PR (contratos, BD, flujo y prueba relacionada).
- La identidad nunca se propaga por headers (`X-User-Id`, `X-Role`, `X-Hotel-Id`): cada servicio la obtiene del JWT validado (algoritmo/issuer/audience/exp/scope), según [`03-contrato-comun.md`](../especificacion/03-contrato-comun.md). No usar ejemplos antiguos que propagaban esos headers.
- Usar el [catálogo de errores](../contracts/errors.md) como fuente única (código, HTTP, mensaje de UI y acción).
- Cada plan referencia los IDs de la matriz de aceptación que cubre; no se marca nada como ejecutado por estar escrito.

## Ciclo de vida

- Activo: está en esta carpeta y en trabajo.
- Cerrado: se mueve a `archivados/` o se elimina tras el merge del PR que lo implementó. Esta carpeta solo contiene trabajo pendiente o en curso.
