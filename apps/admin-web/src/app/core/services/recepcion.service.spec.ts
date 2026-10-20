import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../api.config';
import { gatewayInterceptor } from '../gateway.interceptor';
import { SessionService } from './session.service';
import { RecepcionService } from './recepcion.service';
import { ETagCacheService } from './etag-cache.service';
import {
  apiError,
  authData,
  envelopePage,
  envelopeSingle,
  habitacion,
  limpieza,
  recepcion,
  PERSONA_ADMIN,
} from '../../features/recepcion/testing/recepcion.fixtures';

function microtasks(): Promise<void> {
  return new Promise((r) => setTimeout(r, 0));
}

describe('RecepcionService', () => {
  let service: RecepcionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        SessionService,
        ETagCacheService,
        provideHttpClient(withInterceptors([gatewayInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigateByUrl: vi.fn() } },
      ],
    });
    const session = TestBed.inject(SessionService);
    session.setSession(authData('Administrador'), PERSONA_ADMIN);
    service = TestBed.inject(RecepcionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista estadías con idHotel, estadoEstadia, page y size, y lee meta', async () => {
    const promesa = service.listarEstadias(1, 1, 20, 'ACTIVA');
    const req = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/recepcion/listar` && r.method === 'GET'
    );
    expect(req.request.params.get('idHotel')).toBe('1');
    expect(req.request.params.get('estadoEstadia')).toBe('ACTIVA');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.params.get('size')).toBe('20');
    for (const prohibida of ['X-User-Id', 'X-Role', 'X-Hotel-Id']) {
      expect(req.request.headers.has(prohibida)).toBe(false);
    }
    req.flush(envelopePage([recepcion()], 1, 20, 21));
    const res = await promesa;
    expect(res.page).toBe(1);
    expect(res.size).toBe(20);
    expect(res.totalElements).toBe(21);
    expect(res.data[0].idRecepcion).toBe(1001);
  });

  it('buscarEstadia devuelve el ETag de partida para editar', async () => {
    const promesa = service.buscarEstadia(1001);
    const req = http.expectOne(`${API_BASE_URL}/api/recepcion/1001`);
    req.flush(envelopeSingle(recepcion()), { headers: { ETag: '"7"' } });
    const res = await promesa;
    expect(res.etag).toBe('"7"');
    expect(res.data.version).toBe(1);
  });

  it('actualizarSalida envía If-Match explícito e Idempotency-Key', async () => {
    const promesa = service.actualizarSalida(
      1001,
      { fechaSalida: '2026-10-22', observacion: 'Extiende una noche' },
      '"7"'
    );
    const req = http.expectOne(`${API_BASE_URL}/api/recepcion/actualizar/1001`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.headers.get('If-Match')).toBe('"7"');
    expect(req.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(req.request.body).toEqual({
      fechaSalida: '2026-10-22',
      observacion: 'Extiende una noche',
    });
    req.flush(envelopeSingle(recepcion({ fechaSalida: '2026-10-22' })), {
      headers: { ETag: '"8"' },
    });
    const res = await promesa;
    expect(res.etag).toBe('"8"');
    expect(res.data.fechaSalida).toBe('2026-10-22');
  });

  it('registrarCheckIn envía solo los campos del schema CheckIn', async () => {
    const promesa = service.registrarCheckIn({
      idHotel: 1,
      idCliente: 100,
      idHabitacion: 101,
      fechaSalida: '2026-10-21',
      adelanto: 20,
      medioPago: 'EFECTIVO',
      observacion: 'Ingreso presencial',
    });
    const req = http.expectOne(`${API_BASE_URL}/api/recepcion/registrar`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      idHotel: 1,
      idCliente: 100,
      idHabitacion: 101,
      fechaSalida: '2026-10-21',
      adelanto: 20,
      medioPago: 'EFECTIVO',
      observacion: 'Ingreso presencial',
    });
    req.flush(envelopeSingle(recepcion()), { status: 201, statusText: 'Created' });
    expect((await promesa).idRecepcion).toBe(1001);
  });

  it('completarLimpieza usa el id de habitación, el cleaningCycleId y la versión de la tarea', async () => {
    const tarea = limpieza({ version: 3 });
    const promesa = service.completarLimpieza(
      tarea.idHabitacion,
      tarea.cleaningCycleId,
      tarea.version
    );
    const req = http.expectOne(
      `${API_BASE_URL}/api/habitacion/101/limpieza-completada`
    );
    expect(req.request.method).toBe('PUT');
    expect(req.request.headers.get('If-Match')).toBe('"3"');
    expect(req.request.body).toEqual({ cleaningCycleId: tarea.cleaningCycleId });
    req.flush(envelopeSingle({ limpieza: { ...tarea, estado: 'COMPLETADA' }, habitacion: habitacion() }));
    expect((await promesa).limpieza.estado).toBe('COMPLETADA');
  });

  it('iniciarMantenimiento recibe el 202 en crudo y devuelve Location/Retry-After', async () => {
    const promesa = service.iniciarMantenimiento(101, 'Fuga de agua', 4);
    const req = http.expectOne(`${API_BASE_URL}/api/habitacion/101/mantenimiento`);
    expect(req.request.headers.get('If-Match')).toBe('"4"');
    expect(req.request.body).toEqual({ motivo: 'Fuga de agua' });
    req.flush(
      envelopeSingle({
        operationId: null,
        closureId: null,
        blockId: '33333333-3333-4333-8333-333333333333',
        estadoOperacion: 'PREPARANDO',
      }),
      {
        status: 202,
        statusText: 'Accepted',
        headers: {
          Location: '/api/habitacion/operaciones/33333333-3333-4333-8333-333333333333',
          'Retry-After': '2',
        },
      }
    );
    const res = await promesa;
    expect(res.kind).toBe('pendiente');
    if (res.kind === 'pendiente') {
      expect(res.location).toBe(
        `${API_BASE_URL}/api/habitacion/operaciones/33333333-3333-4333-8333-333333333333`
      );
      expect(res.retryAfterMs).toBe(2000);
      expect(res.operacion.blockId).toBe('33333333-3333-4333-8333-333333333333');
    }
  });

  it('darDeBaja propaga el 409 durable del gate sin reintentar la intención', async () => {
    const promesa = service.darDeBaja(101, 2);
    const req = http.expectOne(`${API_BASE_URL}/api/habitacion/eliminar/101`);
    expect(req.request.method).toBe('DELETE');
    req.flush(apiError('GATE_NOT_AVAILABLE', { idHabitacion: '101' }), {
      status: 409,
      statusText: 'Conflict',
    });
    await expect(promesa).rejects.toMatchObject({ status: 409 });
  });

  it('seguirOperacion sondea hasta APLICADA y notifica cada avance', async () => {
    const avances: string[] = [];
    const promesa = service.seguirOperacion(
      `${API_BASE_URL}/api/habitacion/operaciones/33333333-3333-4333-8333-333333333333`,
      1,
      (a) => avances.push(a.operacion.estado)
    );
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/habitacion/operaciones/33333333-3333-4333-8333-333333333333`)
      .flush(envelopeSingle({ blockId: 'b', idHabitacion: 101, tipo: 'MANTENIMIENTO', estado: 'PREPARANDO' }));
    await new Promise((r) => setTimeout(r, 10));
    http
      .expectOne(`${API_BASE_URL}/api/habitacion/operaciones/33333333-3333-4333-8333-333333333333`)
      .flush(envelopeSingle({ blockId: 'b', idHabitacion: 101, tipo: 'MANTENIMIENTO', estado: 'APLICADA' }));
    const res = await promesa;
    expect(avances).toEqual(['PREPARANDO', 'APLICADA']);
    expect(res?.operacion.estado).toBe('APLICADA');
  });
});
