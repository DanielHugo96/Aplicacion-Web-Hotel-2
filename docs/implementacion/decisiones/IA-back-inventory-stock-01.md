# IA-back-inventory-stock-01 — La dependencia `back-hotel-catalogo` no arranca desde `main`

- **Plan:** `back-inventory-stock`
- **Paso:** 6 y 14 de la sección 4. Pasos (alta de producto y `POST /api/producto/{id}/sedes`)
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-19

## Qué se encontró

El plan depende de `back-hotel-catalogo` (estado `cerrado`, mergeado en `main` con el PR #66) porque
`ProductoService.validarSede` llama por Feign a `GET /internal/hoteles/{id}` antes de crear una
asignación de sede. Al ejecutar los casos de aceptación se comprobó que **`hotel-service` no puede
arrancar desde `main`**, por dos defectos independientes y ajenos a este plan:

1. **Migración V1–V4 sobre base vacía:** `services/hotel-service/src/main/resources/db/migration/V3__hotel_tablas.sql`
   declara `habitacion.publicada` (línea 58) pero la función `fn_catalogo_publico` usa `hab.publica`
   y `h2.publica` (líneas 110 y 116). Flyway aborta con
   `ERROR: column hab.publica does not exist`. Reproducción: crear `hotel_db` vacía y arrancar el
   servicio (el log completo queda en la evidencia del paso 6).
2. **Arranque del contexto JPA:** aun con el esquema creado, Hibernate falla al construir el
   metamodelo con `org.hibernate.MappingException: No column with logical name 'id_hotel' in table 'piso'`.
   El origen está en `Habitacion`: mapea `@Column(name = "id_hotel")` y a la vez un `@JoinColumn(name =
   "id_hotel", referencedColumnName = "id_hotel", ...)` contra `Piso`, que solo expone `id_hotel`
   como columna de su asociación `hotel`, no como columna propia referenciable. El fallo ocurre
   también con `spring.jpa.hibernate.ddl-auto=none`, es decir, no es la validación sino el mapeo.

Ambos defectos están en un servicio ajeno y en un plan ya cerrado. `docs/plans/README.md` §6.4 limita
la zona de escritura de un plan `back` a `services/<servicio>-service/**` del propio servicio, y la
campaña prohíbe tocar servicios ajenos; corregirlos aquí exigiría tocar zona ajena (nivel 3).

## Qué se decidió

No se toca `hotel-service`. Se ejecutan y evidencian todas las comprobaciones de este plan que no
dependen de un hotel en marcha, y se documenta de forma explícita que las rutas que exigen la
validación de sede por Feign (`POST /api/producto/registrar` y `POST /api/producto/{id}/sedes`) **no
se pudieron ejecutar** en este entorno. El defecto queda registrado para el dueño de
`back-hotel-catalogo`.

## Por qué

Norma > plan > catálogo > registro ([`../README.md`](../README.md) §1). La norma fija la zona de
escritura y prohíbe tocar otros servicios; el plan `back-inventory-stock` no incluye corregir hotel.
La alternativa —fabricar el entorno (aplicar a mano una copia parcheada de V3 y desactivar
validaciones) — produciría evidencia que no corresponde a `main` y se descartó. Se aplicó el mismo
criterio que la norma reserva a los bloqueos de entorno: decirlo y seguir, sin arreglarlo en código
ajeno.

## Alternativas descartadas

- **Corregir `V3__hotel_tablas.sql` y el mapeo de `Habitacion`.** Es la solución real, pero es zona
  ajena y pertenece a un plan ya cerrado; hacerlo desde aquí oculta el defecto en vez de reportarlo.
- **Arrancar hotel con una copia parcheada de la migración en `target/classes` y
  `ddl-auto=none`.** Se intentó como diagnóstico: la migración pasa, pero el mapeo JPA sigue
  fallando, así que no habilita el flujo. Además, aunque hubiera funcionado, la evidencia no
  representaría `main`.
- **Sustituir el Feign por un stub.** Prohibido por la campaña: no se usan mocks ni stubs para
  salvar una verificación.

## A quién afecta

- `back-hotel-catalogo` (plan cerrado): sus dos defectos impiden levantar el servicio desde `main`;
  necesita un plan o PR de corrección propio.
- `back-sales-ventas` y cualquier plan que invoque `POST /api/producto/registrar` o
  `POST /api/producto/{id}/sedes` con un hotel real: no podrá hacerlo hasta que se corrija.
- Este plan: `CRUD01` queda evidenciado en edición, versión, baja lógica, `If-Match` 428/412 y sku
  duplicado 409; el alta y la asignación de sede quedan sin evidencia de ejecución.
