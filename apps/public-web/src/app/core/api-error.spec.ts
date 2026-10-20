import { HttpErrorResponse } from '@angular/common/http';
import { mapHttpError } from './api-error';

describe('mapHttpError', () => {
  it('usa el mensaje para UI del catálogo y conserva la correlación', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: {
        success: false,
        message: 'Detalle técnico',
        data: null,
        error: { code: 'VALIDATION_ERROR', fields: { capacidad: '1..10' } },
        meta: { correlationId: 'corr-1' },
      },
    });

    expect(mapHttpError(error)).toMatchObject({
      code: 'VALIDATION_ERROR',
      message: 'Revisa los datos marcados.',
      correlationId: 'corr-1',
      fields: { capacidad: '1..10' },
    });
  });

  it('traduce red caída a un mensaje con reintento', () => {
    const error = new HttpErrorResponse({ status: 0 });
    expect(mapHttpError(error)).toMatchObject({
      code: 'NETWORK_ERROR',
      retryable: true,
    });
  });

  it('traduce 503 del Gateway sin cuerpo a DEPENDENCY_UNAVAILABLE', () => {
    const error = new HttpErrorResponse({ status: 503 });
    expect(mapHttpError(error).message).toBe(
      'El servicio no está disponible. Reintenta en unos segundos.',
    );
  });

  it('para un código desconocido usa el message y la correlación, sin traducir', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: {
        success: false,
        message: 'Conflicto no catalogado',
        data: null,
        error: { code: 'NUEVO_CODIGO', fields: {} },
        meta: { correlationId: 'corr-9' },
      },
    });

    expect(mapHttpError(error)).toMatchObject({
      code: 'NUEVO_CODIGO',
      message: 'Conflicto no catalogado',
      correlationId: 'corr-9',
    });
  });
});
