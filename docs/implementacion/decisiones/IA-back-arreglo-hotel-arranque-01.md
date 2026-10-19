# IA-back-arreglo-hotel-arranque-01 — Migraciones en sitio, mapeo y proyección pública

- **Plan:** `back-arreglo-hotel-arranque`
- **Paso:** 2, 4, 6, 8 y 9 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-19

## Qué se encontró

El arreglo necesita tocar cuatro puntos que la spec no detalla y que chocan con la forma en que
están escritos los artefactos del plan reparado:

1. `V3__hotel_tablas.sql` nunca pudo aplicarse sobre una base vacía desde `main`, pero en algunas
   bases locales figura como aplicada con otro checksum. Flyway no permite corregir una migración
   aplicada sin cambiar su checksum.
2. El plan `back-hotel-catalogo` modeló `Habitacion.piso` como FK compuesta a `piso(id, id_hotel)`,
   pero `Piso` no expone `id_hotel` como columna básica. La spec fija la FK compuesta en el DDL,
   no obliga a modelarla igual en JPA.
3. `fn_catalogo_publico` devuelve `id_hotel`, `slug`, `nombre`, `id_categoria`, `descripcion`,
   `capacidad`, `precio_desde` e `imagenes`; no devuelve la descripción, dirección, ciudad,
   teléfono ni correo de la sede, ni el `detalle` de la categoría. La spec dice que el service
   añade el detalle de categoría y el contrato exige esos campos en `HotelPublico`.
4. `V4__seeds_hotel.sql` usa ids automáticos y `imagen_habitacion` selecciona `h.id` (la sede) en
   vez de `hab.id` (la habitación). La spec fija ids deterministas para pisos y habitaciones.

## Qué se decidió

1. **Corregir V3 y V4 en sitio.** Se editan las migraciones porque V3 no puede aplicarse desde
   cero y V4 no cumple los ids de la spec. Las bases de demo que ya las tengan aplicadas se
   recrean; no se añade una migración correctiva que no podría ejecutarse sobre una V3 rota en una
   base vacía, ni se desactiva `validate-on-migrate`.
2. **Modelar `Habitacion.piso` por `id_piso`.** Se usa un único
   `@JoinColumn(name = "id_piso", referencedColumnName = "id", insertable = false, updatable = false)`.
   La FK compuesta `(id_piso, id_hotel)` sigue en el DDL y la valida PostgreSQL; `idHotel` e
   `idPiso` siguen siendo columnas básicas de `Habitacion`.
3. **Completar la proyección pública en el service.** `CatalogoPublicoService` une el resultado de
   `fn_catalogo_publico` con `hotel` y `categoria` para obtener los campos que la función no
   devuelve, y selecciona `imagenes::text` para que el JSONB llegue como `String`. No se cambia la
   firma de la función ni el OpenAPI.
4. **Usar los ids deterministas de la spec y resetear secuencias.** Los pisos son 11, 21 y 31; las
   habitaciones 101, 102, 201, 202, 301 y 302. `imagen_habitacion` se inserta con `hab.id`. Al
   final, `setval` deja las secuencias por encima del máximo.

## Por qué

Se aplica la jerarquía de [`../README.md`](../README.md) §1: la spec manda y el arreglo debe
cumplirla sin cambiar contratos. De las opciones posibles, estas son las menos invasivas:

- Editar V3/V4 no cambia el esquema ni los contratos; solo corrige lo que ya estaba mal escrito.
  Una V5 no resuelve el arranque desde una base vacía porque V3 falla antes.
- El mapeo por `id_piso` conserva la integridad referencial en la base y evita añadir una columna
  básica `id_hotel` a `Piso` que duplicaría el mapeo de la asociación `hotel`.
- Completar la proyección en el service respeta la firma de `fn_catalogo_publico` que fija la spec
  y el contrato publicado.
- Los ids deterministas son los que usan `SEC03` (`habitacion` 201) y `OCC04` (`piso` 11); sin
  ellos esos casos no se pueden ejecutar como los describe la matriz.

## Alternativas descartadas

- **Añadir `V5__hotel_catalogo_funcion.sql` y dejar V3 rota.** Una base vacía seguiría fallando en
  V3; la V5 nunca llegaría a ejecutarse.
- **Añadir a `Piso` una columna básica `id_hotel` read-only.** Duplica el mapeo de la asociación
  `hotel` y es más invasivo que reducir la asociación de `Habitacion` a su clave real.
- **Cambiar la firma de `fn_catalogo_publico` para devolver los campos de sede.** La spec fija la
  firma de la función y el contrato ya los exige en `HotelPublico`; el service es quien debe
  completarlos.
- **Mantener los ids automáticos y adaptar `SEC03`/`OCC04` a los ids reales.** La matriz y los
  seeds deterministas de la spec fijan 201 y 11; adaptar los casos sería cambiar la spec.
- **Desactivar Flyway o `ddl-auto` para arrancar.** Oculta el defecto y deja el esquema sin
  validar; prohibido por el alcance del arreglo.

## A quién afecta

- `services/hotel-service/**` (V3, V4, `Habitacion`, `HabitacionRepository`,
  `CatalogoPublicoService`, `HotelPublicoController`).
- Los planes dependientes de `back-hotel-catalogo` se desbloquean cuando este arreglo se cierra;
  `back-inventory-stock` es el primero de la cola.
- No cambia ningún contrato de `docs/contracts/**` ni la especificación.
