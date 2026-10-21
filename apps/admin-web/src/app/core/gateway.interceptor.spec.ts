import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpRequest,
  HttpHandlerFn,
  HttpResponse,
  HttpErrorResponse,
  HttpHeaders,
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
        provideHttpClient(),
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

  it('conserva la Idempotency-Key al reintentar un 503 y la renueva tras cerrar la intención', () => {
    let intentos = 0;
    const inestable: HttpHandlerFn = (req) => {
      nextCalls.push(req);
      intentos++;
      if (intentos === 1) {
        return throwError(() => new HttpErrorResponse({ status: 503, statusText: 'Unavailable' }));
      }
      return of(new HttpResponse({ status: 201, body: { ok: true } }));
    };

    TestBed.runInInjectionContext(() => {
      gatewayInterceptor(new HttpRequest('POST', '/api/auth/registro', { a: 1 }), inestable).subscribe({
        error: () => undefined,
      });
    });
    const key1 = nextCalls[0].headers.get('Idempotency-Key');
    expect(key1).toBeTruthy();

    // Reintento del mismo 503: misma intención, misma clave.
    TestBed.runInInjectionContext(() => {
      gatewayInterceptor(new HttpRequest('POST', '/api/auth/registro', { a: 1 }), inestable).subscribe();
    });
    expect(nextCalls[1].headers.get('Idempotency-Key')).toBe(key1);

    // El 201 cierra la intención: la siguiente petición a la misma URL es nueva.
    TestBed.runInInjectionContext(() => {
      gatewayInterceptor(new HttpRequest('POST', '/api/auth/registro', { a: 1 }), next).subscribe();
    });
    expect(nextCalls[2].headers.get('Idempotency-Key')).not.toBe(key1);

    // Otra URL es otra intención.
    TestBed.runInInjectionContext(() => {
      gatewayInterceptor(new HttpRequest('PUT', '/api/auth/password', {}), next).subscribe();
    });
    expect(nextCalls[3].headers.get('Idempotency-Key')).not.toBe(key1);
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

  it('deja pasar el 202 de POST /api/venta para que el área C muestre el banner y sondee', () => {
    let status: number | null = null;
    let location: string | null = null;
    TestBed.runInInjectionContext(() => {
      gatewayInterceptor(new HttpRequest('POST', '/api/venta', { idRecepcion: 1001 }), () =>
        of(
          new HttpResponse({
            status: 202,
            statusText: 'Accepted',
            headers: new HttpHeaders({
              Location: '/api/venta/buscar/5001',
              'Retry-After': '2',
            }),
            body: { ok: true },
          })
        )
      ).subscribe((event) => {
        if (event instanceof HttpResponse) {
          status = event.status;
          location = event.headers.get('Location');
        }
      });
    });
    expect(status).toBe(202);
    expect(location).toBe('/api/venta/buscar/5001');
  });

  it('deja pasar el 202 de pago y anulación de venta para el banner del panel', () => {
    const respuestas: Array<{ status: number; location: string | null }> = [];
    TestBed.runInInjectionContext(() => {
      for (const [method, url] of [
        ['PUT', '/api/venta/5001'],
        ['DELETE', '/api/venta/5001?motivo=Error%20de%20cobro'],
      ] as const) {
        gatewayInterceptor(
          new HttpRequest(method, url, method === 'PUT' ? { estado: 'PAGADO' } : null),
          () =>
            of(
              new HttpResponse({
                status: 202,
                statusText: 'Accepted',
                headers: new HttpHeaders({
                  Location: '/api/venta/buscar/5001',
                  'Retry-After': '2',
                }),
                body: { ok: true },
              })
            )
        ).subscribe((event) => {
          if (event instanceof HttpResponse) {
            respuestas.push({ status: event.status, location: event.headers.get('Location') });
          }
        });
      }
    });
    expect(respuestas).toEqual([
      { status: 202, location: '/api/venta/buscar/5001' },
      { status: 202, location: '/api/venta/buscar/5001' },
    ]);
  });

  it('deja pasar el 202 de mantenimiento y baja de habitación para el banner del panel', () => {
    const respuestas: Array<{ status: number; location: string | null }> = [];
    TestBed.runInInjectionContext(() => {
      for (const [method, url] of [
        ['POST', '/api/habitacion/101/mantenimiento'],
        ['DELETE', '/api/habitacion/eliminar/101'],
      ] as const) {
        gatewayInterceptor(new HttpRequest(method, url, method === 'POST' ? { motivo: 'Fuga' } : null), () =>
          of(
            new HttpResponse({
              status: 202,
              statusText: 'Accepted',
              headers: new HttpHeaders({
                Location: '/api/habitacion/operaciones/33333333-3333-4333-8333-333333333333',
                'Retry-After': '2',
              }),
              body: { data: { blockId: '33333333-3333-4333-8333-333333333333' } },
            })
          )
        ).subscribe((event) => {
          if (event instanceof HttpResponse) {
            respuestas.push({ status: event.status, location: event.headers.get('Location') });
          }
        });
      }
    });
    expect(respuestas).toEqual([
      { status: 202, location: '/api/habitacion/operaciones/33333333-3333-4333-8333-333333333333' },
      { status: 202, location: '/api/habitacion/operaciones/33333333-3333-4333-8333-333333333333' },
    ]);
  });

  it('deja pasar el 202 de cierre de estadía para el banner «no volver a cobrar»', () => {
    let status: number | null = null;
    let location: string | null = null;
    TestBed.runInInjectionContext(() => {
      gatewayInterceptor(
        new HttpRequest('POST', '/api/recepcion/registrar-salida', { idRecepcion: 1001 }),
        () =>
          of(
            new HttpResponse({
              status: 202,
              statusText: 'Accepted',
              headers: new HttpHeaders({
                Location: '/api/recepcion/1001',
                'Retry-After': '2',
              }),
              body: { ok: true },
            })
          )
      ).subscribe((event) => {
        if (event instanceof HttpResponse) {
          status = event.status;
          location = event.headers.get('Location');
        }
      });
    });
    expect(status).toBe(202);
    expect(location).toBe('/api/recepcion/1001');
  });
});
