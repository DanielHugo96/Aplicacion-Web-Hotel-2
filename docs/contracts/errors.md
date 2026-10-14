# Catálogo único de errores

Fuente única para backend, frontend y pruebas. Forma del envelope y status genéricos: [`especificacion/03-contrato-comun.md`](../especificacion/03-contrato-comun.md). El campo `error.code` de cada respuesta es el identificador estable; `message` es texto humano y la UI usa el **mensaje para UI** de esta tabla, nunca uno inventado.

Reglas:

- `error.fields` mapea campo → motivo cuando el error es de validación o de negocio asociado a un campo concreto.
- Los códigos no cambian de semántica entre servicios; dos códigos distintos no comparten HTTP y significado a la vez.
- Un 202 no es error: la operación sigue en curso; el cliente hace polling según `Location`/`Retry-After`.
- Notification no expone errores REST de negocio: sus fallos son jobs en `ERROR` o mensajes en DLQ y se operan con el runbook.

## Genéricos

| Código | HTTP | Servicio | Campo afectado | Cuándo ocurre | Mensaje para UI | Acción esperada |
|---|---|---|---|---|---|---|
| `VALIDATION_ERROR` | 400 | común | `error.fields[campo]` | Formato, límite de campo, JSON cerrado, campo calculado enviado por el cliente | "Revisa los datos marcados." | Corregir los campos; si la intención cambia, nueva `Idempotency-Key` |
| `UNAUTHENTICATED` | 401 | común | `Authorization` | Token ausente, expirado, firma/issuer/audience inválidos | "Tu sesión expiró. Vuelve a iniciar sesión." | Relogin; no reintentar con el mismo token |
| `FORBIDDEN` | 403 | común | — | Rol, scope o sede insuficientes; cliente intentando operación de personal | "No tienes permiso para esta acción." | No reintentar; ocultar la acción según rol |
| `RESOURCE_NOT_FOUND` | 404 | común | id de ruta | Inexistente o ajeno a sede/propietario (anti-enumeración) | "No encontramos lo que buscas." | Volver al listado; no asumir que existe |
| `ROUTE_RETIRED` | 410 | común | ruta | Ruta `RETIRADA` que se invoca fuera de su código específico | "Esta función ya no está disponible." | No usar; retirar el botón o enlace |
| `PRECONDITION_FAILED` | 412 | común | `If-Match` | Versión distinta al ETag vigente | "Otra persona modificó este registro. Recarga para ver los cambios." | Recargar con ETag nuevo; crear nueva intención |
| `PRECONDITION_REQUIRED` | 428 | común | `If-Match` | Falta `If-Match` en PUT/DELETE con `V` | "Falta la versión del registro." | Recargar el recurso y reenviar con ETag |
| `RATE_LIMITED` | 429 | gateway, identity | — | Límite por IP/cuenta (login 5/min, registro 3/min, público 60/min) | "Demasiados intentos. Espera un momento." | Esperar `Retry-After` y reintentar |
| `IDEMPOTENCY_CONFLICT` | 409 | común | `Idempotency-Key` | Misma clave con request distinto, o hash de débito distinto | "La operación ya se envió con otros datos." | Nueva intención con nueva clave; no mezclar cuerpos |
| `DUPLICATE_RESOURCE` | 409 | identity, hotel, inventory | correo, documento, slug, numero, sku | Valor único ya registrado | "Ese valor ya está registrado." | Corregir el valor o editar el existente |
| `PAYLOAD_TOO_LARGE` | 413 | gateway | cuerpo | Cuerpo mayor a 1MiB | "Los datos enviados son demasiado grandes." | Reducir el cuerpo; imágenes por URL autorizada |
| `DEPENDENCY_UNAVAILABLE` | 503 | común | — | Dependencia caída antes de crear una operación durable | "El servicio no está disponible. Reintenta en unos segundos." | Reintentar con la misma clave |
| `INTERNAL_ERROR` | 500 | común | — | Fallo no clasificado (sin stacktrace ni SQL en la respuesta) | "Ocurrió un error inesperado." | Contactar soporte con el `correlationId` |

## Identity

| Código | HTTP | Servicio | Campo afectado | Cuándo ocurre | Mensaje para UI | Acción esperada |
|---|---|---|---|---|---|---|
| `INVALID_CREDENTIALS` | 401 | identity | `correo`/`clave` | Login fallido; no revela si el correo existe | "Correo o contraseña incorrectos." | Verificar datos; esperar el límite tras 5 intentos/min |
| `PASSWORD_POLICY` | 400 | identity | `clave`/`claveNueva` | Clave < 12 o > 72 bytes UTF-8 | "La contraseña debe tener entre 12 y 72 bytes (caracteres UTF-8)." | Corregir la clave |
| `CURRENT_PASSWORD_MISMATCH` | 400 | identity | `claveActual` | Cambio de clave con la clave actual incorrecta | "La contraseña actual no es correcta." | Volver a escribir la clave actual |
| `SOCIAL_LOGIN_DISABLED` | 410 | identity | `POST /api/auth/social` | Ruta retirada del MVP | "El acceso con redes sociales ya no está disponible." | Usar correo y contraseña |
| `FIXED_ROLE_CATALOG` | 409 | identity | `/api/tipopersona` | Crear/eliminar roles del catálogo fijo ADMIN/EMPLEADO/CLIENTE | "Los roles del sistema no se crean ni eliminan." | Editar solo la etiqueta del rol |
| `STAFF_CREDENTIAL_REQUIRED` | 409 | identity | `idTipoPersona` | Promover a ADMIN/EMPLEADO a una persona sin clave | "Para crear personal, la persona necesita credenciales de acceso." | Crear la cuenta con clave o registrar solo cliente |
| `LAST_ADMIN_PROTECTED` | 409 | identity | `idTipoPersona`/`estado` | Degradar o inactivar al último ADMIN activo (PUT o DELETE) | "Debe quedar al menos un administrador activo." | Asignar otro ADMIN antes del cambio |

## Hotel

| Código | HTTP | Servicio | Campo afectado | Cuándo ocurre | Mensaje para UI | Acción esperada |
|---|---|---|---|---|---|---|
| `ROOM_NOT_READY` | 409 | hotel, reception | `idHabitacion` | Físico no `LISTA` o gate en `LIMPIEZA_PENDIENTE` | "La habitación no admite ingreso todavía (limpieza o mantenimiento)." | Elegir otra habitación o esperar la limpieza |
| `ROOM_OCCUPIED` | 409 | hotel, reception | `idHabitacion` | Gate `OCUPADA` u operación bloqueada por ocupación | "La habitación ya está ocupada." | Elegir otra; refrescar el listado |
| `ROOM_NOT_SYNCED` | 409 (503 transitorio) | reception | `idHabitacion` | Gate aún no creado: bootstrap Kafka no alcanzó esa habitación | "La habitación aún no está sincronizada con recepción." | Reintentar; si persiste, revisar gates (runbook de outbox/proyección) |
| `ROOM_OPERATION_IN_PROGRESS` | 409 | hotel | `idHabitacion` | Operación MANTENIMIENTO/BAJA en `PREPARANDO`/`APLICADA` sin liberar | "La habitación tiene una operación en curso." | Esperar a que se aplique o se libere; no lanzar otra en paralelo |
| `GATE_NOT_AVAILABLE` | 409 | reception (interno), hotel lo propaga | `blockId`/`idHabitacion` | El bloqueo solicitado no procede: gate `OCUPADA` o `LIMPIEZA_PENDIENTE`; rechazo durable por `blockId` | "No se puede bloquear la habitación: está ocupada o en limpieza." | No insistir con el mismo `blockId`; el rechazo es durable |
| `FIXED_STATE_CATALOG` | 409 | hotel | `/api/estadohabitacion` | Crear/eliminar estados físicos del catálogo fijo | "Los estados físicos del sistema no se crean ni eliminan." | Editar solo la etiqueta |
| `ACTIVE_DEPENDENCIES` | 409 | hotel | sede/piso/categoría | Baja lógica con habitaciones activas o elementos hijos activos | "No se puede dar de baja mientras tenga elementos activos." | Dar de baja los hijos primero |

## Reception

| Código | HTTP | Servicio | Campo afectado | Cuándo ocurre | Mensaje para UI | Acción esperada |
|---|---|---|---|---|---|---|
| `STAY_NOT_ACTIVE` | 409 | reception | `idRecepcion` | Operación sobre estadía `CERRANDO`/`CERRADA` (o admisión fuera de ACTIVA) | "La estadía no está activa para esa operación." | Recargar el estado; usar el flujo correcto |
| `OPERATIONS_IN_PROGRESS` | 409 | reception | `idRecepcion` | Operaciones de consumo `ABIERTAS` o admisión en vuelo al cerrar | "Hay consumos en proceso. Espera a que terminen." | Esperar el resultado y reintentar |
| `CLOSURE_IN_PROGRESS` | 409 | reception | `idRecepcion` | Otro cierre en `VALIDANDO`/`CONFIRMANDO` | "Ya hay un cierre en curso; no vuelvas a cobrar." | Consultar la estadía; reintentar solo con el mismo `closureId` |
| `AMOUNT_MISMATCH` | 409 | reception | `montoRecibido`/`costoPenalidad` | Importe declarado distinto al `cobroAhora` calculado (exacto) | "El monto no coincide con el calculado. Revisa la nueva cotización." | Mostrar la nueva cotización y confirmar con ella |
| `ADVANCE_EXCEEDS_NEW_TOTAL` | 409 | reception | `fechaSalida` | Acortar la estadía dejaría `precioInicial` por debajo del adelanto | "La nueva fecha deja el alojamiento por debajo del adelanto registrado." | Mantener la fecha o cerrar la estadía; no hay reembolso en el MVP |

## Inventory y Sales

| Código | HTTP | Servicio | Campo afectado | Cuándo ocurre | Mensaje para UI | Acción esperada |
|---|---|---|---|---|---|---|
| `STOCK_INSUFFICIENT` | 409 | inventory, sales | `cantidad` | Stock insuficiente para alguna línea; no hay descuento parcial | "No hay stock suficiente de uno o más productos." | Ajustar cantidades y crear una venta nueva |
| `PRODUCT_INACTIVE` | 409 | inventory, sales | `idProducto` | Producto o sede inactivos en la venta | "El producto ya no está disponible en esta sede." | Quitarlo del carrito; recargar catálogo |
| `DEBIT_CANCELLED` | 409 | inventory | `idVenta` | Lápida `RESTITUIDO` bloquea un débito tardío | "La venta fue anulada; el cobro no corresponde." | No reintentar; iniciar una venta nueva |
| `LEGACY_MOVEMENT_UNVERIFIED` | 409 | sales | `idVenta` | Venta `LEGACY` sin movimiento conciliado: no se emite restitución | "Esta venta antigua no puede anularse automáticamente." | Solicitar conciliación manual (operador) |
| `SALE_ALREADY_PAID` | 409 | sales | `idVenta` | Anular una venta `PAGADO` | "Una venta pagada no se anula; requiere devolución (fuera del MVP)." | Consultar con administración; no borrar historial |

## Reporting y Gateway

| Código | HTTP | Servicio | Campo afectado | Cuándo ocurre | Mensaje para UI | Acción esperada |
|---|---|---|---|---|---|---|
| `REPORTING_NOT_READY` | 503 | reporting | — | Bootstrap (`projection_bootstrap`) aún no está `LISTO` | "Los reportes aún se están preparando." | Esperar `bootstrapComplete`; reintentar |
| `REPORTING_UNAVAILABLE` | 503 | reporting | — | Proyección degradada no utilizable (p. ej. > 5min sin conexión activa) | "Los reportes no están disponibles en este momento." | Contactar soporte; revisar DLT con el runbook |
| `RATE_LIMITED` | 429 | gateway | — | 60 req/min por IP en rutas públicas; además login/registro de identity | "Demasiados intentos. Espera un momento." | Esperar `Retry-After` |
| `PAYLOAD_TOO_LARGE` | 413 | gateway | cuerpo | Cuerpo > 1MiB | "Los datos enviados son demasiado grandes." | Reducir el cuerpo |
| `DEPENDENCY_UNAVAILABLE` | 503 | gateway | — | Servicio de destino caído o circuit breaker abierto | "El servicio no está disponible. Reintenta en unos segundos." | Reintentar con la misma clave |

## Cómo lo usa el frontend

1. Por cada respuesta `success:false`, leer `error.code`.
2. Mostrar el **mensaje para UI** de esta tabla; si el código es desconocido, mostrar `message` y el `correlationId`.
3. Resaltar los campos de `error.fields`.
4. Aplicar la **acción esperada** (reintentar con la misma clave, recargar con ETag, esperar 202, contactar soporte) sin inventar flujos.
