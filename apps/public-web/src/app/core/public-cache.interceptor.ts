import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { of, tap } from 'rxjs';
import { GATEWAY_BASE_URL } from './config';

const PUBLIC_CACHE_TTL_MS = 60_000;

interface CachedResponse {
  expiresAt: number;
  response: HttpResponse<unknown>;
}

/**
 * Caché de cliente para contenido público: solo GET bajo `/api/public/` y
 * nunca más de 60 s, que es el máximo que declara el contrato. Las respuestas
 * autenticadas o fuera de esa ruta no se guardan.
 */
const cache = new Map<string, CachedResponse>();

export const publicCacheInterceptor: HttpInterceptorFn = (req, next) => {
  const gatewayBase = inject(GATEWAY_BASE_URL);
  const isPublicGet =
    req.method === 'GET' &&
    req.url.startsWith(`${gatewayBase}/api/public/`);

  if (!isPublicGet) {
    return next(req);
  }

  const key = req.urlWithParams;
  const hit = cache.get(key);
  if (hit && hit.expiresAt > Date.now()) {
    return of(hit.response.clone());
  }

  return next(req).pipe(
    tap((event) => {
      if (event instanceof HttpResponse) {
        cache.set(key, {
          expiresAt: Date.now() + PUBLIC_CACHE_TTL_MS,
          response: event.clone(),
        });
      }
    }),
  );
};

/** Solo para pruebas: deja la caché en un estado conocido. */
export function clearPublicCache(): void {
  cache.clear();
}
