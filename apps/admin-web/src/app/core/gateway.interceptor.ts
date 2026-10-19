import { inject } from '@angular/core';
import {
  HttpInterceptorFn,
  HttpRequest,
  HttpHandlerFn,
  HttpEvent,
  HttpResponse,
  HttpErrorResponse,
} from '@angular/common/http';
import { catchError, delay, switchMap, takeWhile, of, Observable } from 'rxjs';
import { SessionService } from './services/session.service';
import { ETagCacheService } from './services/etag-cache.service';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';

const POLL_HEADER = 'X-Admin-Web-Poll';

function uuidv4(): string {
  if (typeof crypto !== 'undefined' && typeof (crypto as any).randomUUID === 'function') {
    return (crypto as any).randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function requestKey(req: HttpRequest<unknown>): string {
  return `${req.method} ${req.urlWithParams ?? req.url}`;
}

/**
 * Una clave de idempotencia por intención (método + URL + query).
 * Se conserva al reintentar; ante una intención nueva se genera otra clave.
 */
const intentionKeys = new Map<string, string>();

function parseRetryAfter(value: string | null): number {
  if (!value) return 2000;
  const segundos = Number(value);
  if (Number.isFinite(segundos)) return segundos * 1000;
  const fecha = Date.parse(value);
  return Number.isFinite(fecha) ? Math.max(fecha - Date.now(), 1000) : 2000;
}

/**
 * Polling recursivo a Location tras un 202, respetando Retry-After.
 * Devuelve la primera respuesta que no sea 202 (resultado terminal).
 */
function poll202(
  http: HttpClient,
  location: string,
  baseReq: HttpRequest<unknown>,
  delayMs: number
): import('rxjs').Observable<HttpResponse<unknown>> {
  const pollHeaders: { [name: string]: string } = {};
  baseReq.headers.keys().forEach((name) => {
    const lower = name.toLowerCase();
    if (lower !== 'idempotency-key' && lower !== 'content-type') {
      pollHeaders[name] = baseReq.headers.get(name)!;
    }
  });
  pollHeaders[POLL_HEADER] = 'true';

  const step = (): import('rxjs').Observable<HttpResponse<unknown>> =>
    of(null).pipe(
      delay(delayMs),
      switchMap(() =>
        http.request('GET', location, {
          headers: pollHeaders,
          observe: 'response',
          responseType: 'json',
        })
      ),
      switchMap((r) =>
        r.status === 202 ? step() : of(r)
      )
    );

  return step();
}

/**
 * Interceptor común del panel. Todo el HTTP sale por el Gateway.
 *
 * - Accept: application/json y Content-Type: application/json en cuerpos JSON.
 * - Authorization: Bearer JWT (del almacén en memoria).
 * - X-Correlation-Id opcional (el Gateway genera y devuelve si falta).
 * - Idempotency-Key una vez por intención (salvo login); se conserva al reintentar.
 * - If-Match desde el ETag vigente del recurso en PUT/DELETE.
 * - 401 → relogin (borra sesión, redirige a /login, no reintenta con el mismo token).
 * - 202 → modo pendiente: polling a Location con Retry-After hasta resultado terminal.
 */
export const gatewayInterceptor: HttpInterceptorFn = (req, next) => {
  const session = inject(SessionService);
  const etags = inject(ETagCacheService);
  const http = inject(HttpClient);
  const router = inject(Router);

  const token = session.getToken();
  let headers = req.headers
    .set('Accept', 'application/json')
    .set('X-Correlation-Id', req.headers.get('X-Correlation-Id') || uuidv4());

  const isMutating = req.method === 'POST' || req.method === 'PUT' || req.method === 'DELETE';
  const isLogin = (req.urlWithParams ?? req.url).includes('/api/auth/login');
  const isPoll = req.headers.has(POLL_HEADER);

  if (isMutating && !isLogin && !isPoll) {
    const key = intentionKeys.get(requestKey(req)) ?? uuidv4();
    intentionKeys.set(requestKey(req), key);
    headers = headers.set('Idempotency-Key', key);
  }

  if ((req.method === 'PUT' || req.method === 'DELETE') && !headers.has('If-Match')) {
    const etag = etags.get(req.urlWithParams ?? req.url);
    if (etag) headers = headers.set('If-Match', etag);
  }

  if (token) headers = headers.set('Authorization', `Bearer ${token}`);
  if (isMutating && req.body) headers = headers.set('Content-Type', 'application/json');

  const nextReq = req.clone({ headers });

  return next(nextReq).pipe(
    switchMap((event: HttpEvent<unknown>) => {
      if (event instanceof HttpResponse && event.status === 202 && !isPoll) {
        const location = event.headers.get('Location');
        if (!location) return of(event);
        const delayMs = parseRetryAfter(event.headers.get('Retry-After'));
        return poll202(http, location, nextReq, delayMs);
      }
      return of(event);
    }),
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401) {
        session.logout();
        router.navigateByUrl('/login', { replaceUrl: true });
      }
      throw err;
    })
  ) as Observable<HttpEvent<unknown>>;
};
