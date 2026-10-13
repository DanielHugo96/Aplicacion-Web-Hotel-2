# Título corto del plan

| Campo | Valor |
|---|---|
| Plan | `ámbito-tema` |
| Ámbito | `back` |
| Componente | `identity-service` |
| Fase | `F2` |
| Modo | `directo` |
| Estado | `propuesto` |
| Depende de | `—` |
| Spec | `especificacion/microservicios/identity/api.md`, sección REST |
| Casos | `—` |
| Change | `—` |

## 1. Objetivo

Resultado verificable en una o dos líneas.

## 2. Alcance

**Entra:** endpoints, pantallas, eventos o comandos que este plan implementa.

**Fuera de alcance:** lo que el plan no hace, aunque esté cerca.

## 3. Contexto

Lo que la spec ya fija, citado por sección sin copiar el texto, y las decisiones que este plan toma. Si el plan no decide nada, decirlo.

## 4. Pasos

- [ ] 1. Acción. Verificación: comando o criterio observable.
- [ ] 2. Acción. Verificación: comando o criterio observable.

## 5. Contratos afectados

- OpenAPI: rutas de `contracts/openapi/<servicio>.yaml` que se añaden o cambian.
- Eventos y comandos: nombres de `05-mensajeria.md` y schemas en `contracts/events/`.
- Persistencia: tablas y migraciones Flyway (`V<n>__descripcion.sql`).
- Errores: códigos de `errors.md` que aparecen en el plan.

## 6. Pruebas

- Caso `<ID>`: comando o endpoint; resultado esperado; dónde queda la evidencia.
- Comandos: los de la sección «Verificación» del `README.md` raíz.

## 7. Preguntas abiertas

Ninguna.

## 8. Criterio de terminado

- [ ] Todos los pasos marcados con su verificación.
- [ ] Casos de la sección 6 con evidencia.
- [ ] Sin preguntas abiertas.
