# IA-back-identity-presentacion-real-01 — renombrar personas sembradas sin pisar ediciones

- **Plan:** `back-identity-presentacion-real`
- **Paso:** 2 de la sección 4. Pasos
- **Nivel:** 2 (decidir, registrar y seguir)
- **Fecha:** 2026-10-21

## Qué se encontró

[`identity/base-de-datos.md` §Seeds](../../especificacion/microservicios/identity/base-de-datos.md#seeds) fija los nombres «Sillar Admin», «Sillar Recepción Lima/Miraflores/Arequipa» y «Cliente Uno/Dos». El seeder `DemoDataSeeder` declara hoy `nombre = "Demo"` y los apellidos «Admin», «Empleado Lima», etc., y es idempotente solo por `INSERT ... ON CONFLICT DO NOTHING`. Cambiar las constantes no corrige una base ya sembrada, y esa base es la que alimenta la presentación.

## Qué se decidió

Cambiar las constantes y añadir al mismo `run()` un `UPDATE` por `id` que solo actúa cuando la fila conserva el nombre y el apellido semilla anteriores (`nombre = 'Demo'` y el apellido exacto). Un apellido editado desde el panel no coincide con el valor semilla y no se toca.

## Por qué

La spec pide la marca en los seeds y también dice que los seeds son contenido editable y que no se restablecen si ya existen. Corregir solo la fila que sigue siendo el valor semilla cumple las dos cosas: la presentación arranca con Casa Sillar y una edición humana no se pierde.

## Alternativas descartadas

- **Cambiar solo las constantes.** Las bases ya sembradas seguirían mostrando «Demo Admin» en el panel.
- **Borrar y volver a insertar las personas.** Destruye claves, sedes y ediciones, y además rompe las FK de `persona_sede`.
- **Hacerlo con una migración Flyway.** El `clave_hash` es BCrypt y se calcula en Java con `DEMO_PASSWORD`; la base no puede generarlo ([§Procedimientos](../../especificacion/microservicios/identity/base-de-datos.md#procedimientos-y-funciones)).

## A quién afecta

- `back-identity-presentacion-real`: es su seeder.
- `front-admin-web-imagenes-panel`: el nombre sembrado aparece en la barra del panel y en Mi perfil.
- Contratos: ninguno.
