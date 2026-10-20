# Checklist DESIGN.md §11 — área de cliente

Revisión hecha en Chrome sobre `http://localhost:4300` con el rol CLIENTE y datos reales del
Gateway. Capturas en esta misma carpeta.

| # | Criterio | Mi estadía | Catálogo | Carrito | Mis ventas | Detalle de venta | Mi perfil |
|---|---|---|---|---|---|---|---|
| 1 | Rol correcto visible; nada administrativo | `Cliente`, sin menú A/E | ídem | ídem | ídem | ídem | ídem |
| 2 | Formatos es-PE | `19/10/2026`, `S/ 80.00` | `S/ 3.50`, `S/ 8.00` | `S/ 3.50` | `S/ 18.50` a la derecha | `20/10/2026 06:05`, `S/ 1,234.50` en prueba; `S/ 18.50` real | — |
| 3 | Vacío, cargando (skeleton) y error del catálogo + `correlationId` | vacío y skeleton; 404 con mensaje y `correlationId` | vacío “El catálogo está vacío”; skeleton; 503 con Reintentar | vacío “Tu carrito está vacío”; skeleton; errores por línea | vacío “Aún no tienes consumos”; skeleton; reintento | skeleton; 404 con mensaje y `correlationId` | skeleton; error con Reintentar |
| 4 | 202 como pendiente, nunca éxito optimista | — | — | banner “Operación en proceso. No repitas la acción.” + “Actualizado …” + polling | `Rechazada` como píldora, no éxito | — | — |
| 5 | Sin acciones destructivas de A/E | no hay check-in/cierre/pago/anulación | no hay stock administrativo | “Quitar” línea propia; sin anular venta | sin pagar/anular | sin pagar/anular | solo perfil y clave |
| 6 | Sin textos prohibidos | sin “reservar”, “disponible ahora” ni disponibilidad garantizada | “Existencias”, precio informativo | sin textos de reserva | sin textos de reserva | sin textos de reserva | sin textos de reserva |
| 7 | Accesibilidad | `main`/regiones, foco visible, etiquetas | `alt` en imágenes, botones con texto, foco | `aria-label` en stepper, `aria-invalid` en cantidad | `th scope`, enlaces con texto | `th scope`, foco visible | etiquetas + `aria-required`, contador de bytes |
| 8 | Números tabulares e importes a la derecha | `tabular-nums` | precio tabular | precio/cantidad tabulares | total a la derecha | cantidades/importes a la derecha | — |
| 9 | Copys en minúscula de oración y vocabulario | “Mi estadía”, “Saldo de alojamiento” | “Agregar”, “Existencias” | “Confirmar venta”, “Quitar” | “Pago pendiente”, “Confirmada” | “No registrado” | “Cambiar contraseña” |
| 10 | 390px y 1024px; `prefers-reduced-motion` | `01/02` | `03/04` | `05/11` | `06/07` | `08/12` | `09/10` |

## Evidencia por pantalla

- **Mi estadía**: `01-mi-estadia-1024.png`, `02-mi-estadia-390.png`. Muestra snapshot de cliente y
  habitación, fechas `dd/MM/yyyy`, estado `Activa`, importes del backend. Sin estadía ACTIVA:
  “No tienes una estadía activa; consulta en recepción”.
- **Catálogo**: `03-catalogo-1024.png`, `04-catalogo-390.png`. Precio y existencias del backend;
  botón Agregar; paginación con `Página 1 de 1 · 3 registros`.
- **Carrito**: `05-carrito-1024.png`, `11-carrito-390.png`. Stepper, quitar, confirmar; el 202
  muestra banner persistente y deshabilita acciones (prueba de componente).
- **Mis ventas**: `06-mis-ventas-1024.png`, `07-mis-ventas-390.png`. Dos píldoras separadas
  (`Pago` y `Operación`), total a la derecha, `Página 1 de 2 · 21 registros`.
- **Detalle**: `08-venta-detalle-1024.png`, `12-venta-detalle-390.png`. Líneas inmutables,
  `medioPago` nulo como `No registrado`, fechas `dd/MM/yyyy HH:mm`.
- **Mi perfil**: `09-mi-perfil-1024.png`, `10-mi-perfil-390.png`. Formulario de perfil y clave,
  contador `0 / 72 bytes UTF-8`, botón deshabilitado hasta que la clave es válida.

## Movimiento

- El CSS global declara `@media (prefers-reduced-motion: reduce)` y desactiva animaciones,
  transiciones y `scroll-behavior`; los skeletons y spinners también tienen su regla. La consulta
  en las hojas cargadas devolvió **3 reglas** `prefers-reduced-motion`.
- El tooling de captura no expone la emulación de `prefers-reduced-motion`; no se afirma una
  captura con esa preferencia activa. Las capturas son estáticas y no dependen de movimiento; las
  pruebas de componentes no esperan animaciones.

## Textos administrativos

`grep` sobre el shell y las pantallas del área de cliente: 0 resultados para “Registrar check-in”,
“Registrar salida”, “Anular check-in”, “Anular venta”, “Reportes”, “Productos”, “Limpieza”,
“Sedes”, “Habitaciones” y “Personas”. El shell solo declara Mi estadía, Catálogo, Carrito,
Mis ventas y Mi perfil.
