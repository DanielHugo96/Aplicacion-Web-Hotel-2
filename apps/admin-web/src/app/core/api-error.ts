/**
 * Errores del catálogo único (`docs/contracts/errors.md`).
 * La UI nunca redacta un mensaje propio para un código conocido: usa el
 * `mensaje para UI` y muestra la acción esperada tal cual.
 * Un código desconocido muestra `message` del backend y el `correlationId`.
 */

export interface ApiUiError {
  code: string;
  message: string;
  action: string;
  fields: Record<string, string>;
  correlationId: string;
  retryable: boolean;
  reloadable: boolean;
  relogin: boolean;
}

interface CatalogEntry {
  message: string;
  action: string;
  retryable?: boolean;
  reloadable?: boolean;
  relogin?: boolean;
}

export const ERROR_CATALOG: Record<string, CatalogEntry> = {
  VALIDATION_ERROR: {
    message: 'Revisa los datos marcados.',
    action: 'Corregir los campos; si la intención cambia, nueva clave de idempotencia.',
  },
  UNAUTHENTICATED: {
    message: 'Tu sesión expiró. Vuelve a iniciar sesión.',
    action: 'Iniciar sesión de nuevo.',
    relogin: true,
  },
  FORBIDDEN: {
    message: 'No tienes permiso para esta acción.',
    action: 'No reintentar; ocultar la acción según el rol.',
  },
  RESOURCE_NOT_FOUND: {
    message: 'No encontramos lo que buscas.',
    action: 'Volver al listado; no asumir que existe.',
  },
  RATE_LIMITED: {
    message: 'Demasiados intentos. Espera un momento.',
    action: 'Esperar el tiempo indicado y reintentar.',
    retryable: true,
  },
  IDEMPOTENCY_CONFLICT: {
    message: 'La operación ya se envió con otros datos.',
    action: 'Reiniciar el formulario con una intención nueva.',
  },
  DUPLICATE_RESOURCE: {
    message: 'Ese valor ya está registrado.',
    action: 'Corregir el valor o editar el registro existente.',
  },
  LAST_ADMIN_PROTECTED: {
    message: 'Debe quedar al menos un administrador activo.',
    action: 'Asignar otro administrador antes del cambio.',
  },
  FIXED_ROLE_CATALOG: {
    message: 'Los roles del sistema no se crean ni se eliminan.',
    action: 'Editar solo la etiqueta del rol.',
  },
  FIXED_STATE_CATALOG: {
    message: 'Los estados físicos del sistema no se crean ni se eliminan.',
    action: 'Editar solo la etiqueta del estado.',
  },
  ACTIVE_DEPENDENCIES: {
    message: 'No se puede dar de baja mientras tenga elementos activos.',
    action: 'Dar de baja los elementos hijos primero.',
  },
  STAFF_CREDENTIAL_REQUIRED: {
    message: 'Para crear personal, la persona necesita credenciales de acceso.',
    action: 'Crear la cuenta con clave o registrar solo un cliente.',
  },
  PRECONDITION_FAILED: {
    message: 'Otra persona modificó este registro. Recarga para ver los cambios.',
    action: 'Recargar con el ETag nuevo; no sobrescribir.',
    reloadable: true,
  },
  PRECONDITION_REQUIRED: {
    message: 'Falta la versión del registro.',
    action: 'Recargar el recurso y reenviar con el ETag.',
    reloadable: true,
  },
  DEPENDENCY_UNAVAILABLE: {
    message: 'El servicio no está disponible. Reintenta en unos segundos.',
    action: 'Reintentar con la misma clave.',
    retryable: true,
  },
  ROOM_NOT_READY: {
    message: 'La habitación no admite ingreso todavía (limpieza o mantenimiento).',
    action: 'Elegir otra habitación o esperar la limpieza.',
  },
  ROOM_OCCUPIED: {
    message: 'La habitación ya está ocupada.',
    action: 'Elegir otra; refrescar el listado.',
  },
  ROOM_NOT_SYNCED: {
    message: 'La habitación aún no está sincronizada con recepción.',
    action: 'Reintentar; si persiste, revisar gates (runbook de outbox/proyección).',
    retryable: true,
  },
  ROOM_OPERATION_IN_PROGRESS: {
    message: 'La habitación tiene una operación en curso.',
    action: 'Esperar a que se aplique o se libere; no lanzar otra en paralelo.',
  },
  GATE_NOT_AVAILABLE: {
    message: 'No se puede bloquear la habitación: está ocupada o en limpieza.',
    action: 'No insistir con el mismo blockId; el rechazo es durable.',
  },
  OPERATIONS_IN_PROGRESS: {
    message: 'Hay consumos en proceso. Espera a que terminen.',
    action: 'Esperar el resultado y reintentar.',
  },
  ADVANCE_EXCEEDS_NEW_TOTAL: {
    message: 'La nueva fecha deja el alojamiento por debajo del adelanto registrado.',
    action: 'Mantener la fecha o cerrar la estadía; no hay reembolso en el MVP.',
  },
  // Área de cliente (C) — códigos de `contracts/errors.md`
  STAY_NOT_ACTIVE: {
    message: 'La estadía no está activa para esa operación.',
    action: 'Recargar el estado; usar el flujo correcto.',
  },
  PRODUCT_INACTIVE: {
    message: 'El producto ya no está disponible en esta sede.',
    action: 'Quitarlo del carrito; recargar catálogo.',
  },
  STOCK_INSUFFICIENT: {
    message: 'No hay stock suficiente de uno o más productos.',
    action: 'Ajustar cantidades y crear una venta nueva.',
  },
  SALE_ALREADY_PAID: {
    message: 'Una venta pagada no se anula; requiere devolución (fuera del MVP).',
    action: 'Consultar con administración; no borrar historial.',
  },
  LEGACY_MOVEMENT_UNVERIFIED: {
    message: 'Esta venta antigua no puede anularse automáticamente.',
    action: 'Solicitar conciliación manual (operador).',
  },
  PASSWORD_POLICY: {
    message: 'La contraseña debe tener entre 12 y 72 bytes (caracteres UTF-8).',
    action: 'Corregir la clave.',
  },
  CURRENT_PASSWORD_MISMATCH: {
    message: 'La contraseña actual no es correcta.',
    action: 'Volver a escribir la clave actual.',
  },
  INTERNAL_ERROR: {
    message: 'Ocurrió un error inesperado.',
    action: 'Contactar soporte con el correlationId.',
  },
};

/** Códigos que pertenecen al alcance de este plan (sección 5 del plan). */
export const CATALOGO_ERRORS = Object.keys(ERROR_CATALOG);

export function parseApiError(err: unknown): ApiUiError {
  const anyErr = err as any;
  const body = anyErr?.error ?? anyErr ?? {};
  const code: string =
    body?.error?.code ?? (anyErr?.status === 401 ? 'UNAUTHENTICATED' : 'INTERNAL_ERROR');
  const entry = ERROR_CATALOG[code];
  const correlationId: string =
    body?.meta?.correlationId ?? anyErr?.headers?.get?.('X-Correlation-Id') ?? '';
  const fields: Record<string, string> = body?.error?.fields ?? {};
  return {
    code,
    message: entry?.message ?? (body?.message || 'Ocurrió un error inesperado.'),
    action: entry?.action ?? 'Contactar soporte con el correlationId.',
    fields,
    correlationId,
    retryable: !!entry?.retryable,
    reloadable: !!entry?.reloadable,
    relogin: !!entry?.relogin,
  };
}
