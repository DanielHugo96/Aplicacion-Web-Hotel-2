# IA-back-hotel-presentacion-real-01 — migración V6 y corrección de seeds ya aplicados

- **Plan:** `back-hotel-presentacion-real`
- **Paso:** 1 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-21

## Qué se encontró

La especificación v1.3 añade las columnas `hotel.imagen_url`/`hotel.imagen_alt`, el CHECK `ck_hotel_imagen_alt` y datos nuevos en los seeds ([`hotel/base-de-datos.md`](../../especificacion/microservicios/hotel/base-de-datos.md#seeds-deterministas)). `hotel-service` ya tiene aplicadas `V1`–`V5`, y `V4__seeds_hotel.sql` contiene los nombres, direcciones e imágenes de demostración. Flyway rechaza el cambio de checksum de una migración ya aplicada, así que no se puede corregir `V4` en sitio. La `V5` pertenece a `back-hotel-operaciones` y no toca las tablas de catálogo.

## Qué se decidió

La ampliación y la corrección de datos van en `V6__hotel_imagenes_presentacion.sql`: `ALTER TABLE hotel` con las dos columnas y el CHECK, y `UPDATE` de las tres sedes, las dos categorías y las seis habitaciones, más el completado de `imagen_habitacion`. Las filas semilla se reconocen por su URL `/assets/demo/…` o por su texto anterior, para no pisar una edición hecha desde el panel.

## Por qué

Es la opción menos invasiva que deja la base en el estado que fija la spec sin romper una migración ya aplicada. El caso es el mismo que resolvió `back-hotel-operaciones` al numerar su migración `V5` ([`IA-back-hotel-operaciones-01.md`](IA-back-hotel-operaciones-01.md)); aquí se repite el criterio: la migración nueva va al final del historial.

## Alternativas descartadas

- **Editar `V4__seeds_hotel.sql`.** Flyway falla por checksum en cualquier base ya migrada; es el defecto que prohíbe la restricción del encargo.
- **Crear una tabla `imagen_hotel`.** La spec guarda la foto de la sede en `hotel` porque es una sola imagen principal; una tabla aparte contradice [§Tablas](../../especificacion/microservicios/hotel/base-de-datos.md#tablas).
- **Dejar los seeds viejos y confiar en editarlos desde el panel.** La presentación arrancaría con «Hotel Demo» y sin galería; la spec exige el contenido inicial.

## A quién afecta

- `back-hotel-presentacion-real`: es su migración.
- `back-inventory-imagenes-producto` y `back-notification-presentacion-real`: aplican el mismo criterio en sus servicios (`V6` y `V901`) por la misma razón.
- Contratos: ninguno; `hotel.yaml` y el schema de `SedeSnapshot` ya están publicados en `0c5b0a2`.
