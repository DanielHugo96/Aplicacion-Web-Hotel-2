# Planes de ejecución

## Qué es esta carpeta

Aquí viven los **planes de ejecución**: documentos cortos que explican, antes de escribir código, **qué hay que hacer, en qué orden y cómo se sabe que terminó**.

Un plan no es código ni documentación del sistema. Es la hoja de ruta de una tarea concreta: la escribe quien la va a hacer, la usa mientras trabaja y se archiva cuando la tarea se cierra.

> La especificación **dice qué es** el sistema. El plan **dice cómo lo construimos paso a paso**.
> Spec en [`../especificacion`](../especificacion/README.md), planes en esta carpeta.

## Cuándo crear un plan

Crea un plan **antes** de tocar código cuando la tarea:

- toca más de un microservicio, o más de una base de datos;
- cambia un contrato compartido (headers, envelopes, topics, DTOs, endpoints);
- tiene varios pasos donde el orden importa (migración antes que código, seed antes que prueba);
- lleva más de medio día de trabajo;
- tiene riesgos o decisiones que conviene dejar escritas para el equipo.

No hace falta plan para: corregir un typo, un fix puntual, un ajuste de estilo.

## Cómo se nombra

Un archivo por plan, en minúsculas, con guiones y sufijo de fecha:

```
NN-slug-de-la-tarea-AAAA-MM-DD.md
```

Ejemplos:

```
01-identity-service-jwt-2026-10-04.md
02-migracion-bd-hotel-2026-10-10.md
```

- `NN-` es el número de orden en que se planearon (01, 02, 03...).
- El slug describe la tarea, corto y entendible por todos.
- La fecha es la de creación del plan.

## Cómo se escribe un plan

Seis secciones, en este orden. Si una no aplica, se deja vacía con un guion `-`.

```markdown
# Plan: <nombre de la tarea>

## Objetivo
Una o dos frases: qué debe quedar funcionando al terminar.

## Alcance
- Incluido:
- Excluido:

## Tareas
1. Paso concreto y verificable.
2. Otro paso.
3. ...

## Dependencias
- Servicio, topic o documento del que depende.

## Riesgos
- Qué puede salir mal y cómo se mitiga.

## Criterios de aceptación
- [ ] Condición observable 1
- [ ] Condición observable 2
```

Reglas de estilo:

- Tarea = algo que se puede marcar como hecho. Nada de "trabajar en el login".
- Los criterios de aceptación son observables: se pueden probar desde fuera del código.
- Si el plan toca contratos, enlaza el documento de [`../especificacion`](../especificacion/README.md) que cambia.

## Ciclo de vida de un plan

```
borrador  ->  en curso  ->  hecho  ->  (->  archivado si la decisión se obsoleta)
```

- **borrador:** se está escribiendo o revisando, todavía no se envía a ejecución.
- **en curso:** alguien lo está ejecutando; los commits se refieren al plan.
- **hecho:** la tarea se completó y sus criterios de aceptación se cumplieron. El archivo se queda, no se borra.

Al terminar, actualiza el estado en el encabezado del plan.

## Relación con Git

Cada plan se trabaja **en su propia rama**, siguiendo la guía del equipo:

```bash
git switch main
git pull
git switch -c plan-identity-service-jwt
```

Y al cerrarlo:

```bash
git add .
git commit -m "docs: agrega plan de identity-service"
git push -u origin plan-identity-service-jwt
```

Reglas que no se rompen:

- Nunca se trabaja directo en `main`.
- El plan se sube **en la misma rama** que la tarea que ejecuta; así el código y su plan viajan juntos al PR.
- El nombre de la rama sigue la tarea, no el documento: `git switch -c login-jwt`, no `git switch -c plan`.

## Ejemplo

[`ejemplo-plan.md`](ejemplo-plan.md) muestra un plan completo y sirve de plantilla para los nuevos.