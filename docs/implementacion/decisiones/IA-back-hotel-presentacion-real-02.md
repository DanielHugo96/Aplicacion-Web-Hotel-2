# IA-back-hotel-presentacion-real-02 — dónde se valida el par `imagenUrl`/`imagenAlt`

- **Plan:** `back-hotel-presentacion-real`
- **Paso:** 7 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-21

## Qué se encontró

El paso 7 pide que `HotelInput` gane `imagenUrl` (≤ 500) e `imagenAlt` (≤ 150) «con la regla de URL
de imagen» y que una URL sin `alt` o un `alt` sin URL se rechacen con 400 `VALIDATION_ERROR` sobre el
campo. La spec fija el qué ([`03` §Límites de campos comunes](../../especificacion/03-contrato-comun.md#límites-de-campos-comunes))
pero no dónde se comprueba, y el contrato no expone ninguna restricción declarativa para el par:
`HotelInput` de `hotel.yaml` es un `additionalProperties: false` con `maxLength` y descripciones, sin
`oneOf` ni `dependentRequired`.

La tentación es poner `@Valid` en `HotelController.registrar` y `actualizar` y apoyarse en los
`@Size`/`@NotBlank` que `HotelInput` ya declara. Pero hoy esos controladores **no** llevan `@Valid`,
así que ninguna de esas anotaciones se evalúa: las validaciones de los campos que ya existían
(`slug`, `nombre`, `correoContacto`…) las hace la base y el propio `HotelService`. Añadir `@Valid`
pondría a validar de golpe todos los campos de un CRUD que `back-hotel-catalogo` cerró, no solo los
dos nuevos.

## Qué se decidió

La regla del par se comprueba en `HotelService` (`validarImagen`), que es el punto único que usan
`registrar` y `actualizar`, y el error sale como `ApiException(VALIDATION_ERROR, {campo: mensaje})`,
que es exactamente la forma que exige `error.fields`. Los `@Size(max = 500)` / `@Size(max = 150)` se
declaran igualmente en el record, como documentación de los límites del contrato y por coherencia con
los demás campos del mismo fichero, pero la comprobación efectiva —incluidos los dos máximos— es la
del servicio.

## Por qué

Es la opción menos invasiva: cumple el paso 7 sin ampliar la validación de los campos de un plan ya
cerrado. `03` dice que los strings se validan «en backend» y que exceder un límite devuelve 400 con el
campo en `error.fields`; no dice que tenga que ser Bean Validation. `HotelService` ya es donde viven
las demás reglas de negocio del recurso (slug no modificable tras publicar, unicidad de slug), así
que la comprobación queda en el sitio donde el resto de invariantes se cumplen y no depende de que un
controlador futuro recuerde poner `@Valid`.

## Alternativas descartadas

- **`@Valid` en `HotelController`.** Activa de golpe `@NotBlank`/`@Pattern`/`@Email` sobre todos los
  campos de `HotelInput`, incluidos los que `back-hotel-catalogo` cerró sin esa validación. Un alta
  que hoy pasa podría empezar a dar 400, y el cambio no es de este plan.
- **`@Valid` solo en un DTO nuevo.** Duplicar `HotelInput` en dos records para validar unos campos y
  otros no rompe el contrato (`HotelInput` es un schema único) y obliga a elegir el DTO en el
  controlador, que es justo la lógica que se quiere evitar.
- **Validador de clase (`@ScriptAssert` o `@AssertTrue`).** Con `@Valid` fuera de juego no se
  ejecuta, y añadirlo vuelve al primer problema.
- **Dejar solo el `CHECK` de la base.** Rechazaría la URL sin `alt`, pero no la `alt` sin URL y, sobre
  todo, devolvería un 500 en vez del 400 `VALIDATION_ERROR` con `error.fields` que pide el contrato.

## A quién afecta

- `back-hotel-presentacion-real`: es su validación.
- `front-admin-web-imagenes-panel`: recibe 400 `VALIDATION_ERROR` con `error.fields.imagenUrl` o
  `error.fields.imagenAlt` y puede marcar el campo; no hay código de error nuevo.
- Contratos: ninguno. `hotel.yaml` ya describe la regla; no se ha tocado.
