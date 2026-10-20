import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Propaga una correlación opcional en cada llamada. Si la petición no trae
 * `X-Correlation-Id`, se genera uno; el Gateway lo devuelve y el cuerpo de
 * error lo expone en `meta.correlationId`.
 */
export const correlationInterceptor: HttpInterceptorFn = (req, next) => {
  const correlationId =
    req.headers.get('X-Correlation-Id') ?? createCorrelationId();

  return next(
    req.clone({
      setHeaders: {
        Accept: 'application/json',
        'X-Correlation-Id': correlationId,
      },
    }),
  );
};

function createCorrelationId(): string {
  const cryptoApi = globalThis.crypto;
  if (cryptoApi && typeof cryptoApi.randomUUID === 'function') {
    return cryptoApi.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = Math.floor(Math.random() * 16);
    const value = char === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}
