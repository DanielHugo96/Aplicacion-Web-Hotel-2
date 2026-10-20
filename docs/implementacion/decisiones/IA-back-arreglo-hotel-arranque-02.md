# IA-back-arreglo-hotel-arranque-02 — Errores 409 y defectos que bloqueaban CRUD01

- **Plan:** `back-arreglo-hotel-arranque`
- **Paso:** 14 y 15 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-19

## Qué se encontró

Al ejecutar `CRUD01` y `OCC04` con respuestas reales aparecieron defectos del plan reparado que
no estaban en la lista inicial del arreglo, todos dentro de `services/hotel-service/**`:

1. `Hotel.createdAt` se mapeaba como columna básica obligatoria. Hibernate insertaba `null` y la
   base rechazaba el alta con `null value in column "created_at" ... violates not-null constraint`;
   el `DEFAULT now()` del DDL nunca se aplicaba.
2. `PisoService`, `CategoriaService`, `HotelService`, `HabitacionService` y
   `EstadoHabitacionService` lanzaban `ResponseStatusException(CONFLICT)`, pero
   `GlobalExceptionHandler.codeForStatus` no traducía `409` y caía en `INTERNAL_ERROR`. `OCC04`
   exige `409 ACTIVE_DEPENDENCIES` y el catálogo ya define ese código.
3. `ImagenHabitacionRepository.deleteByHabitacionId` no tenía `@Modifying`; al editar una
   habitación Spring Data ejecutaba el `DELETE` como consulta de lectura y respondía 500.
4. `OutboxService` recibía `ObjectMapper` con `@Autowired(required=false)` y el bean era `null`:
   el alta de sede fallaba al emitir `SedeSnapshot`. Además, el alta de habitación no resolvía la
   asociación `piso`, así que `pisoNombre` viajaba `null` en el evento y no cumplía el schema v1.

## Qué se decidió

1. `created_at` pasa a `insertable = false, updatable = false` para que lo rellene el `DEFAULT` de
   PostgreSQL; la vista `HotelView` no lo expone, así que no hay cambio de contrato.
2. Se añaden a `ErrorCode` los códigos que ya publica [`errors.md`](../../contracts/errors.md):
   `DUPLICATE_RESOURCE`, `ACTIVE_DEPENDENCIES` y `FIXED_STATE_CATALOG`, y las mutaciones lanzan
   `ApiException` con el código que corresponde en lugar de un `CONFLICT` genérico.
3. Se añade `@Modifying` al borrado de imágenes.
4. `OutboxService` usa un `ObjectMapper` propio, igual que `KafkaOutboxPublisher` y
   `MessageValidator`, y `HabitacionService` resuelve el `Piso` de la sede antes de guardar y
   emitir, para que `pisoNombre` sea el real y el evento valide contra su schema.

## Por qué

La jerarquía de [`../README.md`](../README.md) §1 da prioridad a la norma y a los contratos. Los
tres códigos de error ya están en `docs/contracts/errors.md`; no se inventa ninguno. El arreglo se
limita a `hotel-service`, que es la zona del plan que se repara, y no cambia rutas, schemas,
eventos ni versiones fijadas. Las correcciones son las mínimas para que los casos `CRUD01` y
`OCC04` se ejecuten de verdad.

## Alternativas descartadas

- **Mapear `CONFLICT` a `ACTIVE_DEPENDENCIES` en el handler global.** Habría etiquetado como
  dependencias activas también los duplicados y el catálogo fijo de estados.
- **Rellenar `created_at` desde el servicio.** Duplica una regla que ya vive en el DDL y deja el
  esquema dependiendo del código Java.
- **Validar la emisión del evento solo al publicar.** El schema exige `pisoNombre`; corregir el
  payload en origen evita filas de outbox inválidas y reintentos inútiles.
- **Añadir locks de padres en este arreglo.** La disciplina de locks es del plan de operaciones y
  `OCC04` con el piso 11 ya sembrado se cumple sin ella; añadirla aquí ampliaría el alcance.

## A quién afecta

`services/hotel-service/**` (`Hotel`, `ErrorCode`, `OutboxService`, `HabitacionService`,
`ImagenHabitacionRepository` y los servicios de mutación). No cambia ningún contrato de
`docs/contracts/**` ni la especificación, y no toca otros servicios ni planes.
