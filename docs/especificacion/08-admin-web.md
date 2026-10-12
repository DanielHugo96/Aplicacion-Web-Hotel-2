# admin-web — pantallas, formularios y estados

Mapa completo del panel (`admin-web`): rutas por rol, pantallas con acciones, formularios (campos, validaciones y mensajes por campo) y estados vacío/loading/error. Deriva de los contratos (`docs/contracts/`) y de [`02-landing-y-panel.md`](02-landing-y-panel.md); los mensajes de error de negocio salen del catálogo [`errors.md`](../contracts/errors.md).

## 1. Reglas transversales

- Todo HTTP va por el Gateway (`http://localhost:8080`); nunca puertos de servicios ni brokers.
- JWT en memoria; logout borra el token; 401 → relogin. Rol y sede se leen del JWT solo como UX: el backend revalida.
- `Idempotency-Key`: se genera **una vez por intención** y se conserva al reintentar; tras corregir un 400/409 de validación se crea una intención nueva con nueva clave.
- `If-Match` desde el `ETag` en cada edición; 412 → recargar y no sobrescribir.
- 202 → pantalla en modo “pendiente” con polling a `Location` (`Retry-After`); nunca mostrar éxito definitivo.
- Formatos: moneda `S/ 1,234.50`; fechas `dd/MM/yyyy`; timestamps `dd/MM/yyyy HH:mm`; locale es-PE.
- Errores: mostrar mensaje para UI del catálogo, resaltar `error.fields` y ofrecer la acción esperada; incluir `correlationId` en el estado de error.
- Área de cliente (C) separada del menú administrativo; C nunca ve acciones de A/E.

## 2. Rutas Angular y guards

| Ruta | Pantalla | Rol | Acciones principales |
|---|---|---|---|
| `/login` | Login | P | Iniciar sesión; enlace a registro y a la landing |
| `/registro` | Registro público | P | Crear cuenta Cliente; enlace a login |
| `/app/inicio` | Dashboard | A/E | KPIs de reportes de su alcance; accesos rápidos |
| `/app/personas` | Personas | A/E | Listar/buscar; alta (E solo Cliente); editar/baja solo A |
| `/app/personas/nuevo`, `/app/personas/:id/editar` | Formulario de persona | A/E | Alta/edición; sedes para personal |
| `/app/tipos-persona` | Roles | A/E lectura, A edita | Editar etiqueta; nunca crear/eliminar |
| `/app/sedes`, `/app/sedes/nuevo`, `/app/sedes/:id/editar` | Sedes | A/E lectura, A muta | CRUD de sedes; baja solo sin habitaciones activas |
| `/app/pisos`, `/app/pisos/nuevo`, `/app/pisos/:id/editar` | Pisos | A/E lectura, A muta | CRUD por sede |
| `/app/categorias`, `/app/categorias/nuevo`, `/app/categorias/:id/editar` | Categorías | A/E lectura, A muta | CRUD global |
| `/app/habitaciones`, `/app/habitaciones/nuevo`, `/app/habitaciones/:id/editar` | Habitaciones | A/E lectura, A muta | CRUD; imágenes; mantenimiento/baja/reactivar |
| `/app/limpieza` | Limpiezas | A/E sede | Listar PENDIENTE/COMPLETADA; completar ciclo |
| `/app/recepcion` | Estadías | A/E sede | Listar/filtrar; ver detalle; ir a check-in |
| `/app/recepcion/check-in` | Check-in | A/E sede | Buscar cliente; crear estadía |
| `/app/recepcion/:id` | Detalle de estadía | A/E sede (C propia en su área) | Editar salida; cotizar; cerrar; anular check-in; archivar |
| `/app/recepcion/:id/cierre` | Cierre | A/E sede | Cotización, penalidad, monto recibido y medio de pago |
| `/app/productos`, `/app/productos/nuevo`, `/app/productos/:id/editar` | Productos | A/E lectura sede, A muta | CRUD por sede; ajustes; movimientos |
| `/app/productos/:id/movimientos` | Movimientos | A/E sede | Consultar ventas/ajustes por producto |
| `/app/ventas`, `/app/ventas/:id` | Ventas | A/E sede | Listar; ver detalle; pagar; anular impagada |
| `/app/ventas/nueva` | Nueva venta | A/E sede (y C en su área) | Tomar carrito o catálogo; confirmar |
| `/app/reportes` | Reportes | A todas, E asignada | Dashboard, ventas, ocupación, cobros, bajo stock; exportar |
| `/app/perfil` | Mi perfil | A/E/C | Editar perfil; cambiar contraseña |
| `/mi-estadia` | Mi estadía | C | Ver estadía activa/snapshots; abrir catálogo |
| `/mi-estadia/catalogo` | Catálogo | C | Ver productos de la sede; agregar al carrito |
| `/mi-estadia/carrito` | Carrito | C | Listar/quitar; crear venta PENDIENTE |
| `/mis-ventas`, `/mis-ventas/:id` | Mis ventas | C | Estado de sus ventas; polling tras 202 |
| `/mi-perfil` | Mi perfil | C | Editar perfil; cambiar clave |

Guards: `authGuard` + `rolGuard(['ADMIN'|'EMPLEADO'|'CLIENTE'])`; al entrar, A/E van a `/app/inicio` y C a `/mi-estadia`. Ruta sin permiso → 403 del backend y redirect a la pantalla inicial del rol, sin mostrar datos.

Límites del área C: no hace check-in/check-out, no paga ventas (ni marca PAGADO), no anula, no ve reportes, stock administrativo ni datos de otras personas; solo opera su estadía ACTIVA y sus ventas.

## 3. Formularios (campos, validación y mensajes)

| Formulario | Campo | Validación | Mensaje |
|---|---|---|---|
| Login | correo | Requerido, formato correo (≤ 254) | “Ingresa tu correo” / “Correo inválido” |
| Login | clave | Requerida | “Ingresa tu contraseña” |
| Login (global) | — | 401 genérico; 429 tras 5/min | “Correo o contraseña incorrectos.” / “Demasiados intentos. Espera un momento.” |
| Registro | tipoDocumento | DNI, PASAPORTE o CE | “Selecciona el tipo de documento” |
| Registro | documento | DNI 8 dígitos; otros 6–20 alfanuméricos | “El DNI debe tener 8 dígitos” / “Documento inválido” |
| Registro | nombre, apellido | Requeridos, ≤ 100 | “Ingresa tu nombre” |
| Registro | correo | Requerido, formato, ≤ 254 | “Correo inválido” |
| Registro | clave | 12–72 bytes UTF-8, contador en vivo | “La contraseña debe tener entre 12 y 72 bytes (caracteres UTF-8)” |
| Persona (alta/edición) | tipoDocumento, documento, nombre, apellido, correo | Igual que Registro; documento no editable tras crear | “Documento inválido” / “El documento no se puede cambiar” |
| Persona | idTipoPersona | Requerido; E solo Cliente | “No puedes crear personal” |
| Persona | sedes | Empleado ≥ 1; Cliente 0; Admin 0 = global | “Asigna al menos una sede” |
| Persona | clave | Obligatoria para ADMIN/EMPLEADO nuevo | “Para crear personal, la clave es obligatoria” |
| Persona | estado | No desactivar al último ADMIN | “Debe quedar al menos un administrador activo.” |
| Hotel | slug | Requerido, `[a-z0-9-]`, único; estable tras publicar | “Slug inválido o ya usado” |
| Hotel | nombre, direccion, ciudad | Requeridos; ≤ 120 / 200 / 80 | “Campo obligatorio” |
| Hotel | descripcion | ≤ 300 | “Máximo 300 caracteres” |
| Hotel | telefono | ≤ 20 | “Teléfono demasiado largo” |
| Hotel | correoContacto | Correo ≤ 254 | “Correo inválido” |
| Hotel | publicado, estado | Booleanos; baja/inactivación sin habitaciones activas | “No se puede dar de baja mientras tenga elementos activos.” |
| Piso | idHotel | Requerido | “Selecciona la sede” |
| Piso | descripcion | Requerida, ≤ 80, única por sede | “Ya existe un piso con esa descripción” |
| Categoría | descripcion | Requerida, ≤ 80, única | “Ya existe esa categoría” |
| Categoría | detalle | Requerido, ≤ 500 | “Máximo 500 caracteres” |
| Categoría | capacidad | 1..10 | “Capacidad entre 1 y 10” |
| Habitación | idHotel, idPiso, idCategoria | Requeridos; piso de la sede | “Selecciona piso y categoría de la sede” |
| Habitación | numero | Requerido, ≤ 10; único por sede entre activas | “Ya existe una habitación activa con ese número” |
| Habitación | detalle | Requerido, ≤ 500 | “Máximo 500 caracteres” |
| Habitación | precio | > 0, 2 decimales | “El precio debe ser mayor que 0” |
| Habitación | urlsImagenes | ≤ 10; url https o `/assets/` (≤ 500); alt ≤ 150; orden único | “Máximo 10 imágenes” / “URL no permitida” |
| Mantenimiento | motivo | Requerido, 1..300 | “Indica el motivo” |
| Limpieza (completar) | cleaningCycleId | Fijo desde el listado; `If-Match` de la tarea | “La tarea cambió: recarga el listado” |
| Check-in | idCliente | Buscador exacto de Cliente activo | “Cliente no encontrado: créalo primero” |
| Check-in | idHabitacion | Requerida, del listado de la sede; LISTA y libre | “La habitación ya está ocupada.” / “La habitación no admite ingreso todavía.” |
| Check-in | fechaSalida | ≥ hoy (Lima); ≤ 365 noches | “La fecha de salida no puede ser anterior a hoy” |
| Check-in | adelanto | 0..alojamiento (noches × tarifa) | “El adelanto no puede superar el alojamiento” |
| Check-in | medioPago | Obligatorio si adelanto > 0: EFECTIVO/TARJETA/OTRO | “Selecciona el medio de pago del adelanto” |
| Check-in | observacion | ≤ 500 | “Máximo 500 caracteres” |
| Editar salida | fechaSalida | ≥ entrada; no dejar precioInicial < adelanto | “La nueva fecha deja el alojamiento por debajo del adelanto registrado.” |
| Cierre | costoPenalidad | 0..precioInicial | “La penalidad no puede superar el alojamiento” |
| Cierre | motivoPenalidad | Obligatorio si penalidad > 0, 1..300 | “Indica el motivo de la penalidad” |
| Cierre | montoRecibido | ≥ 0; etiqueta “Monto recibido ahora”; comparación exacta contra `cobroAhora` | “El monto no coincide con el calculado. Revisa la nueva cotización.” |
| Cierre | medioPago | Obligatorio: EFECTIVO/TARJETA/OTRO | “Selecciona el medio de pago” |
| Anular check-in | motivo | Requerido, 1..300; solo ACTIVA sin ventas admitidas ni cierre | “No se puede anular: hay consumos o un cierre en curso.” |
| Producto (alta) | sku | Requerido, ≤ 40, único global | “Ese SKU ya está registrado” |
| Producto | nombre, detalle, imagenUrl | ≤ 120 / 500 / 500 | “Máximo 500 caracteres” |
| Producto | idHotel, precio, cantidadInicial, umbralBajo | Requeridos; precio > 0; cantidades ≥ 0 | “El precio debe ser mayor que 0” |
| Ajuste de stock | delta | Entero ≠ 0 | “El delta no puede ser 0” |
| Ajuste de stock | motivo | Requerido, 1..300 | “Indica el motivo del ajuste” |
| Venta (nueva) | líneas | Desde carrito o catálogo; cantidad 1..9999; ≤ 50 productos | “Máximo 50 productos distintos” / “Cantidad inválida” |
| Venta (nueva) | estado | C solo PENDIENTE; A/E PAGADO exige medioPago | “Selecciona el medio de pago” |
| Pago de venta | medioPago | Requerido: EFECTIVO/TARJETA/OTRO | “Selecciona el medio de pago” |
| Reportes | inicio, fin | Requeridos; inicio < fin; rango ≤ 366 días; ocupación en medianoche Lima | “El rango no puede superar 366 días” |
| Mi perfil | nombre, apellido | Requeridos, ≤ 100 | “Ingresa tu nombre” |
| Mi perfil | fotoUrl | https allowlist o `/assets/`, ≤ 500 | “URL no permitida” |
| Cambiar clave | claveActual | Requerida | “Ingresa tu contraseña actual” |
| Cambiar clave | claveNueva | 12–72 bytes UTF-8; distinta de la actual | “La contraseña debe tener entre 12 y 72 bytes (caracteres UTF-8)” |

## 4. Estados por pantalla

| Pantalla | Vacío | Cargando | Error |
|---|---|---|---|
| Dashboard | “Sin datos para tu sede todavía” | Skeleton de KPIs | 503 `REPORTING_NOT_READY`: “Los reportes aún se están preparando.” + reintento |
| Personas | “No hay personas que coincidan” | Skeleton de tabla | 403/404 según catálogo; retry |
| Sedes/pisos/categorías/habitaciones | “Aún no hay registros” + botón Crear (A) | Skeleton de tabla/form | 409 de baja con dependencias explicado; 412 recargar |
| Limpieza | “No hay tareas pendientes” | Skeleton de tarjetas | 412 “recarga el listado”; retry |
| Estadías | “No hay estadías en esta sede” | Skeleton de tabla | 503 dependencia; retry con misma clave |
| Check-in | Formulario limpio; buscador sin resultados: “Cliente no encontrado: créalo primero” | Botón con spinner y bloqueo de doble envío | 409 `ROOM_OCCUPIED`/`ROOM_NOT_READY` con el mensaje del catálogo |
| Detalle/cierre | Cotización inicial | Fases “VALIDANDO/CONFIRMANDO” visibles | 409 `AMOUNT_MISMATCH` con nueva cotización; 202 “cierre pendiente, no volver a cobrar” + polling |
| Productos/movimientos | “Sin movimientos en el rango” | Skeleton de tabla | 503 sin fallback de precio; retry |
| Ventas | “No hay ventas” | Skeleton + polling de operaciones 202 | 409 por catálogo (stock, pagada); 412 recargar |
| Reportes | `data: []` con “Sin resultados”; nunca 404 | Skeleton por reporte | Degradado: aviso “datos eventualmente consistentes”; no utilizable: 503 |
| Mi estadía (C) | Sin estadía ACTIVA: “No tienes una estadía activa; consulta en recepción” | Skeleton de tarjeta | 404 sin datos de terceros; retry |
| Catálogo/carrito (C) | “El catálogo está vacío” / “Tu carrito está vacío” | Skeleton de lista | 409 `PRODUCT_INACTIVE`/`STOCK_INSUFFICIENT` al vender; quitar línea |
| Mis ventas (C) | “Aún no tienes consumos” | Skeleton + polling | 404 venta ajena; retry |
| Mi perfil | Formulario con datos actuales | Botón con spinner | 412 recargar; 400 por campo |

## 5. Menú por rol

| Sección | ADMIN | EMPLEADO | CLIENTE |
|---|---|---|---|
| Inicio/Dashboard | Sí | Sí (sede) | No |
| Personas y roles | Sí | Solo clientes | No |
| Catálogo físico (sedes/pisos/categorías/habitaciones) | CRUD | Lectura asignada | No |
| Limpieza | Sí | Sí asignada | No |
| Recepción (check-in/cierre/anular) | Sí | Sí asignada | No (solo ver su estadía) |
| Productos/stock | CRUD + ajustes | Lectura sede | No (catálogo vía su estadía) |
| Ventas (pagar/anular) | Sí | Sí asignada | No |
| Reportes | Todas las sedes | Sede asignada | No |
| Mi perfil / mi estadía / mis ventas / carrito | Sí | Sí | Solo lo propio |

Los guards ocultan secciones, pero el backend es la autoridad: un enlace oculto no sustituye un 403/404 real.
