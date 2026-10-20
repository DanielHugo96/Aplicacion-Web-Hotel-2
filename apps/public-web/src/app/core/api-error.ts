import { HttpErrorResponse } from '@angular/common/http';
import { ApiErrorBody } from './catalog.models';

/**
 * Mensajes para UI y acciones esperadas del catálogo único (`contracts/errors.md`).
 * La landing nunca redacta mensajes propios para códigos conocidos.
 */
export interface UiError {
  code: string;
  message: string;
  action: string;
  correlationId: string;
  retryable: boolean;
  fields: Record<string, string>;
}

const CATALOG: Record<
  string,
  { message: string; action: string; retryable: boolean }
> = {
  VALIDATION_ERROR: {
    message: 'Revisa los datos marcados.',
    action: 'Corrige los campos marcados.',
    retryable: false,
  },
  UNAUTHENTICATED: {
    message: 'Tu sesión expiró. Vuelve a iniciar sesión.',
    action: 'Inicia sesión de nuevo.',
    retryable: false,
  },
  FORBIDDEN: {
    message: 'No tienes permiso para esta acción.',
    action: 'No reintentes; vuelve al inicio.',
    retryable: false,
  },
  RESOURCE_NOT_FOUND: {
    message: 'No encontramos lo que buscas.',
    action: 'Volver al listado.',
    retryable: false,
  },
  RATE_LIMITED: {
    message: 'Demasiados intentos. Espera un momento.',
    action: 'Espera y reintenta.',
    retryable: true,
  },
  DEPENDENCY_UNAVAILABLE: {
    message: 'El servicio no está disponible. Reintenta en unos segundos.',
    action: 'Reintentar.',
    retryable: true,
  },
  INTERNAL_ERROR: {
    message: 'Ocurrió un error inesperado.',
    action: 'Contacta con soporte e indica el correlationId.',
    retryable: true,
  },
};

const NETWORK_ERROR: UiError = {
  code: 'NETWORK_ERROR',
  message: 'No se pudo conectar con el servicio. Reintenta en unos segundos.',
  action: 'Reintentar.',
  correlationId: '',
  retryable: true,
  fields: {},
};

export function mapHttpError(error: unknown): UiError {
  if (!(error instanceof HttpErrorResponse)) {
    if (isApiErrorBody(error)) {
      return fromBody(error);
    }
    return NETWORK_ERROR;
  }

  if (error.status === 0) {
    return NETWORK_ERROR;
  }

  const body = error.error as ApiErrorBody | null;
  if (isApiErrorBody(body)) {
    return fromBody(body, error);
  }

  const byStatus: Record<number, string> = {
    400: 'VALIDATION_ERROR',
    401: 'UNAUTHENTICATED',
    403: 'FORBIDDEN',
    404: 'RESOURCE_NOT_FOUND',
    429: 'RATE_LIMITED',
    503: 'DEPENDENCY_UNAVAILABLE',
    500: 'INTERNAL_ERROR',
  };
  const code = byStatus[error.status] ?? 'INTERNAL_ERROR';
  const fallback = CATALOG[code] ?? CATALOG['INTERNAL_ERROR'];
  return {
    code,
    message: fallback.message,
    action: fallback.action,
    correlationId: error.headers.get('X-Correlation-Id') ?? '',
    retryable: fallback.retryable,
    fields: {},
  };
}

export function validationError(field: string, detail: string): UiError {
  return {
    code: 'VALIDATION_ERROR',
    message: CATALOG['VALIDATION_ERROR'].message,
    action: CATALOG['VALIDATION_ERROR'].action,
    correlationId: '',
    retryable: false,
    fields: { [field]: detail },
  };
}

function fromBody(body: ApiErrorBody, httpError?: HttpErrorResponse): UiError {
  const code = body.error?.code ?? 'INTERNAL_ERROR';
  const entry = CATALOG[code] ?? {
    message: body.message,
    action: 'Contacta con soporte e indica el correlationId.',
    retryable: false,
  };
  const correlationId =
    body.meta?.correlationId ??
    httpError?.headers.get('X-Correlation-Id') ??
    '';
  return {
    code,
    message: entry.message,
    action: entry.action,
    correlationId,
    retryable: entry.retryable,
    fields: body.error?.fields ?? {},
  };
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  return (
    typeof value === 'object' &&
    value !== null &&
    'error' in value &&
    typeof (value as ApiErrorBody).error === 'object'
  );
}
