# Planes de ejecución

## Qué es esta carpeta

Aquí viven los planes de ejecución: documentos cortos que explican, antes de escribir código, qué hay que hacer, en qué orden y cómo se sabe que terminó.

Un plan no es código ni documentación del sistema. Es la hoja de ruta de una tarea concreta: la escribe quien la va a hacer, la usa mientras trabaja y se archiva cuando la tarea se cierra.

> La especificación dice qué es el sistema. El plan dice cómo lo construimos paso a paso.
> Spec en ../especificacion, planes en esta carpeta.

## Reglas al escribir planes

- La identidad nunca se propaga por headers (`X-User-Id`, `X-Role`, `X-Hotel-Id`): cada servicio la obtiene del JWT validado (algoritmo/issuer/audience/exp/scope), según [`03-contrato-comun.md`](../especificacion/03-contrato-comun.md). No usar ejemplos antiguos que propagaban esos headers.
