import { TestBed } from '@angular/core/testing';
import {
  HttpRequest,
  HttpHandlerFn,
  HttpResponse,
  HttpErrorResponse,
} from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { gatewayInterceptor } from './gateway.interceptor';
import { SessionService } from './services/session.service';
import { ETagCacheService } from './services/etag-cache.service';
import { Router } from '@angular/router';

const authData = {
  idPersona: 1,
  nombre: 'Juan',
  apellido: 'Pérez',
  correo: 'juan@hotel.test',
  tipoPersona: 'Cliente',
  token: 'jwt-de-prueba',
  expiresIn: 900,
  sedes: [],
};

const persona = {
  idPersona: 1,
  tipoDocumento: 'DNI',
  documento: '44555666',
  nombre: 'Juan',
  apellido: 'Pérez',
  correo: 'juan@hotel.test',
  fotoUrl: null,
  idTipoPersona: 3,
  tipoPersona: 'Cliente',
  sedes: [],
  estado: true,
  fechaCreacion: '2026-10-19T00:00:00Z',
  version: 1,
};

describe('gatewayInterceptor', () => {
  let session: SessionService;
  let router: Router;
  let nextCalls: HttpRequest<unknown>[] = [];

  beforeEach(() => {
    nextCalls = [];
    TestBed.configureTestingModule({
      providers: [
        SessionService,
        ETagCacheService,
        { provide: Router, useValue: { navigateByUrl: vi.fn() } },
      ],
    });
    session = TestBed.inject(SessionService);
    router = TestBed.inject(Router);
    session.setSession(authData, persona);
  });

  const next: HttpHandlerFn = (req: HttpRequest<unknown>) => {
    nextCalls.push(req);
    return of(new HttpResponse({ status: 200, body: { ok: true } }));
  };

  it('agrega Accept, Authorization, correlación e Idempotency-Key a peticiones mutantes', () => {
    TestBed.runInInjectionContext(() => {
      gatewayInterceptor(new HttpRequest('POST', '/api/auth/registro', { a: 1 }), next).subscribe();
    });
    const sent = nextCalls[0];
    expect(sent.method).toBe('POST');
    expect(sent.headers.get('Accept')).toBe('application/json');
    expect(sent.headers.get('Authorization')).toBe('Bearer jwt-de-prueba');
    expect(sent.headers.get('X-Correlation-Id')).toBeTruthy();
    const key1 = sent.headers.get('Idempotency-Key');
    expect(key1).toBeTruthy();
  });

  it('no agrega Idempotency-Key a login ni a peticiones de solo lectura', () => {
    TestBed.runInInjectionContext(() => {
      gatewayInterceptor(new HttpRequest('POST', '/api/auth/login', {}), next).subscribe();
      gatewayInterceptor(new HttpRequest('GET', '/api/auth/me'), next).subscribe();
    });
    expect(nextCalls[0].headers.has('Idempotency-Key')).toBeFalsy();
    expect(nextCalls[1].headers.has('Idempotency-Key')).toBeFalsy();
  });

  it('conserva la misma Idempotency-Key al reintentar y genera una nueva para intención nueva', () => {
    TestBed.runInInjectionContext(() => {
      gatewayInterceptor(new HttpRequest('POST', '/api/auth/registro', { a: 1 }), next).subscribe();
    });
    const key1 = nextCalls[0].headers.get('Idempotency-Key');
    expect(key1).toBeTruthy();

    TestBed.runInInjectionContext(() => {
      gatewayInterceptor(new HttpRequest('POST', '/api/auth/registro', { a: 1 }), next).subscribe();
    });
    expect(nextCalls[1].headers.get('Idempotency-Key')).toBe(key1);

    TestBed.runInInjectionContext(() => {
      gatewayInterceptor(new HttpRequest('PUT', '/api/auth/password', {}), next).subscribe();
    });
    expect(nextCalls[2].headers.get('Idempotency-Key')).not.toBe(key1);
  });

  it('adjunta If-Match desde el ETag cacheado en PUT', () => {
    TestBed.inject(ETagCacheService).set('/api/auth/me', '"1"');
    TestBed.runInInjectionContext(() => {
      gatewayInterceptor(new HttpRequest('PUT', '/api/auth/me', { nombre: 'Juan' }), next).subscribe();
    });
    const last = nextCalls[nextCalls.length - 1];
    expect(last.headers.get('If-Match')).toBe('"1"');
  });

  it('401 → borra la sesión en memoria y redirige a /login sin reintentar', () => {
    const spy = vi.spyOn(router, 'navigateByUrl');
    const next401: HttpHandlerFn = () =>
      throwError(() => ({ status: 401, statusText: 'Unauthorized' } as HttpErrorResponse));
    TestBed.runInInjectionContext(() => {
      gatewayInterceptor(new HttpRequest('GET', '/api/auth/me'), next401).subscribe({
        error: () => {},
      });
    });
    expect(session.getToken()).toBeNull();
    expect(spy).toHaveBeenCalledWith('/login', { replaceUrl: true });
  });
});
