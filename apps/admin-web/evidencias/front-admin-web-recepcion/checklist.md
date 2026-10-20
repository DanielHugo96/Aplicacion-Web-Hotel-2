# Checklist DESIGN.md §11 — recepción (admin-web)

Revisión hecha en Chrome sobre `http://localhost:4300` con datos reales del Gateway
(`http://localhost:8080`) y capturas a 1024 px en esta misma carpeta. Roles probados: ADMIN,
EMPLEADO y CLIENTE.

| # | Criterio | Estadías | Detalle / editar salida | Check-in | Limpieza | Habitaciones (operaciones) |
|---|---|---|---|---|---|---|
| 1 | Rol correcto visible; A/E ven Recepción y Limpieza, C no | Menú A con Recepción y Limpieza (`01`); E ve las mismas y solo lectura en habitaciones (`12`); C queda en `/mi-estadia` sin menú administrativo | A/E según guard; sin acciones de cierre (F4) | A/E; sin acciones de cierre ni anulación | A/E sede | Solo A ve Iniciar mantenimiento, Dar de baja, Completar mantenimiento y Reactivar; E ve «Solo lectura» (`12`) |
| 2 | Formatos es-PE `S/ 1,234.50`, `dd/MM/yyyy`, `dd/MM/yyyy HH:mm`, tabulares a la derecha | `S/ 70.00`, `S/ 140.00`, `20/10/2026`; importes y fechas tabulares (`01`) | `S/ 70.00`, `S/ 20.00`, `22/10/2026`; confirmación `dd/MM/yyyy HH:mm` con el pipe (`02`, `03`) | `S/ 70.00`/`S/ 100.00` del selector; fecha ISO del control con locale es-PE (`04`, `06`) | UUID en mono; versión tabular (`07`, `08`) | `S/ 70.00` y `S/ 100.00` a la derecha (`09`) |
| 3 | Vacío, cargando (skeleton) y error del catálogo + `correlationId` + acción | Vacío «No hay estadías en esta sede», skeleton de tabla, 503 con mensaje, `correlationId`, Copiar y Reintentar | Skeleton de tarjeta; 404 «No encontramos lo que buscas.» con `correlationId`; 412 «Otra persona modificó este registro…» | Skeleton de habitaciones, buscador sin resultados «Cliente no encontrado: créalo primero», 409 inline y 503 con Reintentar (`05`) | Vacío exacto «No hay tareas pendientes» (`07`), skeleton de tarjetas, 503/412 | Skeleton/errores del catálogo y banner de operación con mensaje del catálogo y `correlationId` |
| 4 | 202 como pendiente, nunca éxito optimista | — | — | — | — | Banner «Operación en proceso. No repitas la acción.» con spinner, `Actualizado …` y polling hasta APLICADA; acciones bloqueadas mientras hay operación (prueba de componente; `http-evidence.md`) |
| 5 | Destructivas con motivo y confirmación | — | Editar salida no es destructiva y no cierra | — | Completar limpieza no es destructiva | «Dar de baja» abre confirmación con consecuencia y dice «Dar de baja», no «Eliminar» (`11`); «Iniciar mantenimiento» exige motivo 1..300 (`10`) |
| 6 | Sin textos prohibidos | Sin «reservar», «reserva» ni «disponible ahora» | Ídem | «Sin datos» para `DESCONOCIDA`, nunca «Libre» ni «Disponible» | Ídem | «Ocupación: Sin datos»; jamás se traduce a disponible |
| 7 | Accesibilidad: teclado, foco visible, etiquetas, contraste, `aria-live` | `th scope`, enlaces con texto, foco visible, banner `role=alert` | Etiquetas y `aria-describedby` en fecha y observación; foco visible | Etiquetas visibles, errores con `aria-invalid`/`aria-describedby`, radios con `aria-label` | Botón con texto y spinner, `aria-live` en el banner de pendiente | Diálogo modal con foco atrapado; `aria-live` en el banner de pendiente; 0 campos sin etiqueta en la revisión de la pantalla |
| 8 | Números tabulares e importes a la derecha | `.num` + `font-variant-numeric: tabular-nums` | Ídem | Precio del selector a la derecha | Versión tabular | Precio a la derecha |
| 9 | Copys en minúscula de oración y vocabulario | «Estadías», «Registrar check-in», «Estadía vencida sin cierre» | «Editar salida», «Guardar salida», «Saldo de alojamiento» | «Registrar check-in», «Buscar cliente» | «Completar limpieza» | «Iniciar mantenimiento», «Dar de baja», «Reactivar» |
| 10 | 1024 px y `prefers-reduced-motion` | `01` | `02`, `03` | `04`, `05`, `06` | `07`, `08` | `09`, `10`, `11`, `12` |

## Evidencia por pantalla

- **Estadías** (`01-estadias-1024.png`): listado real de la sede 1, paginación
  `Página 1 de 1 · 4 registros`, píldoras de estado y fechas `dd/MM/yyyy`. El filtro por
  `estadoEstadia` y la etiqueta «Estadía vencida sin cierre» se cubren en
  `estadias-list.component.spec.ts` (ACTIVA vencida sí; CERRADA y salida de hoy no).
- **Detalle** (`02-detalle-estadia-1024.png`): snapshots de cliente y habitación, fechas,
  `precioInicial`, `adelanto`, `medioPagoAdelanto`, `precioRestante`, `totalPagado`, penalidad y
  observación. La edición (`03-editar-salida-1024.png`) muestra fecha y observación con contador
  `32/500 caracteres`; el 200 real refrescó el detalle y el toast «Cambios guardados».
- **Check-in** (`04-check-in-1024.png`): buscador exacto, cliente activo seleccionado, habitaciones
  activas con precio, estado físico y ocupación. `DESCONOCIDA` se pinta «Sin datos» y sigue
  seleccionable porque la admisión la decide el backend; `05-check-in-409-1024.png` muestra el 409
  `ROOM_NOT_SYNCED` junto al selector y en el banner, y `06-check-in-201-detalle-1024.png` el 201
  con navegación al detalle y el toast «Check-in registrado».
- **Limpieza** (`07-limpieza-vacia-1024.png`): vacío exacto «No hay tareas pendientes».
  (`08-limpieza-completadas-1024.png`): tarea COMPLETADA real de la habitación 303 con su versión.
  El 200 de completar, el 412 y el filtro por estado se cubren en `limpieza.component.spec.ts`.
- **Habitaciones y operaciones** (`09-habitaciones-operaciones-1024.png`): acciones de A sobre el
  catálogo existente. `10-dialogo-mantenimiento-1024.png`: motivo obligatorio y consecuencia.
  `11-confirmar-baja-1024.png`: confirmación destructiva con «Dar de baja». `12-empleado-habitaciones-1024.png`:
  el mismo catálogo con «Solo lectura», sin mantenimiento, baja ni reactivación.

## Movimiento

- El CSS global declara `@media (prefers-reduced-motion: reduce)` y desactiva animaciones,
  transiciones y `scroll-behavior`; skeletons y spinners tienen su regla. La consulta a las hojas
  cargadas en la pantalla de habitaciones devolvió **3 reglas** `prefers-reduced-motion`.
- El tooling de captura no expone emulación de `prefers-reduced-motion`; no se afirma una captura
  con esa preferencia activa. Las capturas son estáticas y las pruebas no esperan animaciones.

## Textos y roles

- `grep` sobre `features/recepcion`, `features/catalogo/habitaciones` y `ui/pending-banner`: 0
  resultados para «reservar»/«reserva». «Disponible» solo aparece en el mensaje del catálogo
  «El servicio no está disponible. Reintenta en unos segundos.», no como disponibilidad de
  habitación.
- Menú con ADMIN y EMPLEADO: Inicio, Personas, Roles, Sedes, Pisos, Categorías, Habitaciones,
  Limpieza, Recepción, Mi perfil. Cliente: solo el shell de área de cliente, sin menú
  administrativo.
- La navegación del cliente a una ruta `/app/**` la bloquea `rolGuard`/`authGuard`; la prueba de
  rutas y el spec de guards del base están en verde, y el shell de C no enlaza ninguna pantalla
  administrativa.
