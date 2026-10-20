import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../../core/api.config';
import { gatewayInterceptor } from '../../core/gateway.interceptor';
import { SessionService } from '../../core/services/session.service';
import { RecepcionService } from '../../core/services/recepcion.service';
import { ETagCacheService } from '../../core/services/etag-cache.service';
import {
  authData,
  envelopePage,
  envelopeSingle,
  habitacion,
  persona,
  PERSONA_ADMIN,
  recepcion,
} from './testing/recepcion.fixtures';

/**
 * Matriz de contrato del flujo de recepción: búsqueda de cliente → habitación →
 * check-in 201 → listado → detalle con ETag → edición de salida 200. Las
 * pantallas y sus estados (vacío, error, 409, 412, 202) se cubren en los specs
 * de cada componente.
 */
describe('flujo de contrato de recepción', () => {
  let service: RecepcionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        SessionService,
        RecepcionService,
        ETagCacheService,
        provideHttpClient(withInterceptors([gatewayInterceptor])),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigateByUrl: vi.fn() } },
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), PERSONA_ADMIN);
    service = TestBed.inject(RecepcionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('cliente → habitación → check-in 201 → listado → detalle → edición 200', async () => {
    const cliente = service.buscarClientePorDocumento('70000001');
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/persona/listar` &&
          r.params.get('documento') === '70000001'
      )
      .flush(envelopePage([persona()]));
    const [encontrado] = await cliente;
    expect(encontrado.idPersona).toBe(100);

    const habitaciones = service.listarHabitacionesDeSede(1);
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/habitacion/listar` && r.params.get('idHotel') === '1'
      )
      .flush(envelopePage([habitacion(), habitacion({ idHabitacion: 999, estado: false })]));
    const disponibles = await habitaciones;
    expect(disponibles.map((h) => h.idHabitacion)).toEqual([101]);

    const checkIn = service.registrarCheckIn({
      idHotel: 1,
      idCliente: encontrado.idPersona,
      idHabitacion: disponibles[0].idHabitacion,
      fechaSalida: '2026-10-21',
      adelanto: 20,
      medioPago: 'EFECTIVO',
    });
    const reqCheckIn = http.expectOne(`${API_BASE_URL}/api/recepcion/registrar`);
    expect(reqCheckIn.request.headers.get('Idempotency-Key')).toBeTruthy();
    reqCheckIn.flush(envelopeSingle(recepcion()), { status: 201, statusText: 'Created' });
    const creada = await checkIn;
    expect(creada.idRecepcion).toBe(1001);
    expect(creada.estadoEstadia).toBe('ACTIVA');

    const listado = service.listarEstadias(1, 0, 20, 'ACTIVA');
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/recepcion/listar` &&
          r.params.get('estadoEstadia') === 'ACTIVA'
      )
      .flush(envelopePage([creada], 0, 20, 1));
    expect((await listado).totalElements).toBe(1);

    const detalle = service.buscarEstadia(creada.idRecepcion);
    http.expectOne(`${API_BASE_URL}/api/recepcion/1001`).flush(envelopeSingle(creada), {
      headers: { ETag: '"1"' },
    });
    const conEtag = await detalle;
    expect(conEtag.etag).toBe('"1"');

    const edicion = service.actualizarSalida(
      1001,
      { fechaSalida: '2026-10-22', observacion: 'Extiende una noche' },
      conEtag.etag!
    );
    const reqEdicion = http.expectOne(`${API_BASE_URL}/api/recepcion/actualizar/1001`);
    expect(reqEdicion.request.headers.get('If-Match')).toBe('"1"');
    expect(reqEdicion.request.body).toEqual({
      fechaSalida: '2026-10-22',
      observacion: 'Extiende una noche',
    });
    reqEdicion.flush(envelopeSingle(recepcion({ fechaSalida: '2026-10-22', version: 2 })), {
      headers: { ETag: '"2"' },
    });
    const editada = await edicion;
    expect(editada.etag).toBe('"2"');
    expect(editada.data.fechaSalida).toBe('2026-10-22');
  });
});
