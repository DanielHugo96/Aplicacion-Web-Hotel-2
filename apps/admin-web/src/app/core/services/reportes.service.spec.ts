import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../api.config';
import { gatewayInterceptor } from '../gateway.interceptor';
import { parseApiError } from '../api-error';
import { SessionService } from './session.service';
import { ReportesService, parseRetryAfter } from './reportes.service';
import {
  apiErrorReporte,
  dashboardReporte,
  envelopeReporte,
  metaReporte,
  reporteBajoStock,
  reporteCobros,
  reporteOcupacion,
  reporteVentas,
} from '../../features/reportes/testing/reportes.fixtures';
import { authData, PERSONA_ADMIN } from '../../features/ventas/testing/ventas.fixtures';

const INICIO = '2026-09-20T00:00:00-05:00';
const FIN = '2026-10-21T00:00:00-05:00';

function sinHeadersDeIdentidad(req: { headers: { has(name: string): boolean } }): void {
  expect(req.headers.has('X-User-Id')).toBe(false);
  expect(req.headers.has('X-Role')).toBe(false);
  expect(req.headers.has('X-Hotel-Id')).toBe(false);
}

function sinHeadersDeMutacion(req: {
  headers: { has(name: string): boolean };
}): void {
  // Solo GET: sin clave de idempotencia ni precondición.
  expect(req.headers.has('Idempotency-Key')).toBe(false);
  expect(req.headers.has('If-Match')).toBe(false);
}

describe('ReportesService (Gateway, solo GET)', () => {
  let service: ReportesService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        ReportesService,
        SessionService,
        provideHttpClient(withInterceptors([gatewayInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    TestBed.inject(SessionService).setSession(
      authData('Administrador'),
      PERSONA_ADMIN
    );
    service = TestBed.inject(ReportesService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('el dashboard sin idHotel pide el consolidado y conserva la meta EVENTUAL', async () => {
    const promesa = service.dashboard(null);
    const req = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/reportes/dashboard`
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.params.has('idHotel')).toBe(false);
    sinHeadersDeIdentidad(req.request);
    sinHeadersDeMutacion(req.request);
    req.flush(envelopeReporte(dashboardReporte({ ingresosHoy: 3642.5 }), { degraded: true }));
    const res = await promesa;
    expect(res.data?.ingresosHoy).toBe(3642.5);
    expect(res.data?.moneda).toBe('PEN');
    expect(res.meta.consistency).toBe('EVENTUAL');
    expect(res.meta.degraded).toBe(true);
    expect(res.meta.bootstrapComplete).toBe(true);
    expect(res.meta.updatedAt).toBe('2026-10-04T15:00:00Z');
  });

  it('el dashboard con idHotel lo envía como query', async () => {
    const promesa = service.dashboard(1);
    const req = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/reportes/dashboard` &&
        r.params.get('idHotel') === '1'
    );
    sinHeadersDeMutacion(req.request);
    req.flush(envelopeReporte(dashboardReporte()));
    await promesa;
  });

  it('ventas envía idHotel, inicio, fin, page y size con offset', async () => {
    const promesa = service.ventas(1, INICIO, FIN, 2, 2);
    const req = http.expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('idHotel')).toBe('1');
    expect(req.request.params.get('inicio')).toBe(INICIO);
    expect(req.request.params.get('fin')).toBe(FIN);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('2');
    sinHeadersDeIdentidad(req.request);
    sinHeadersDeMutacion(req.request);
    req.flush(
      envelopeReporte([reporteVentas()], {
        page: 2,
        size: 2,
        totalElements: 8,
      })
    );
    const res = await promesa;
    expect(res.data[0].nombreProducto).toBe('Agua 500ml');
    expect(res.meta.page).toBe(2);
    expect(res.meta.size).toBe(2);
    expect(res.meta.totalElements).toBe(8);
  });

  it('ocupación, cobros y bajo stock usan sus rutas y queries', async () => {
    const ocupacion = service.ocupacion(1, INICIO, FIN, 0, 20);
    const reqOcupacion = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/reportes/ocupacion`
    );
    expect(reqOcupacion.request.params.get('inicio')).toBe(INICIO);
    reqOcupacion.flush(envelopeReporte([reporteOcupacion()]));
    expect((await ocupacion).data[0].vecesAlquilada).toBe(22);

    const cobros = service.cobros(1, INICIO, FIN, 0, 20);
    const reqCobros = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/reportes/cobros`
    );
    expect(reqCobros.request.params.get('fin')).toBe(FIN);
    reqCobros.flush(envelopeReporte([reporteCobros()]));
    expect((await cobros).data[0].medioPagoCierre).toBe('TARJETA');

    const bajoStock = service.bajoStock(1, 5, 0, 20);
    const reqBajoStock = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/reportes/productos-bajo-stock`
    );
    expect(reqBajoStock.request.params.get('idHotel')).toBe('1');
    expect(reqBajoStock.request.params.get('limite')).toBe('5');
    expect(reqBajoStock.request.params.get('page')).toBe('0');
    expect(reqBajoStock.request.params.get('size')).toBe('20');
    reqBajoStock.flush(envelopeReporte([reporteBajoStock()]));
    expect((await bajoStock).data[0].cantidad).toBe(3);
  });

  it('un 503 REPORTING_NOT_READY llega con su mensaje para UI y Retry-After', async () => {
    const promesa = service.dashboard(null);
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/dashboard`)
      .flush(apiErrorReporte('REPORTING_NOT_READY'), {
        status: 503,
        statusText: 'Service Unavailable',
        headers: { 'Retry-After': '7' },
      });
    const err = await promesa.then(
      () => null,
      (e) => parseApiError(e)
    );
    expect(err?.code).toBe('REPORTING_NOT_READY');
    expect(err?.message).toBe('Los reportes aún se están preparando.');
    expect(err?.retryAfterMs).toBe(7000);
    expect(err?.correlationId).toBe('cid-reporte-error');
  });

  it('un 503 REPORTING_UNAVAILABLE no se reintenta solo: conserva su acción', async () => {
    const promesa = service.ventas(1, INICIO, FIN);
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`)
      .flush(apiErrorReporte('REPORTING_UNAVAILABLE'), {
        status: 503,
        statusText: 'Service Unavailable',
      });
    const err = await promesa.then(
      () => null,
      (e) => parseApiError(e)
    );
    expect(err?.message).toBe('Los reportes no están disponibles en este momento.');
    expect(err?.retryable).toBe(false);
    expect(err?.retryAfterMs).toBeNull();
  });

  it('una lista vacía devuelve data: [] y meta coherente, nunca 404', async () => {
    const promesa = service.ventas(1, INICIO, FIN, 0, 20);
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`)
      .flush(envelopeReporte([], { totalElements: 0 }));
    const res = await promesa;
    expect(res.data).toEqual([]);
    expect(res.meta.totalElements).toBe(0);
  });

  it('parseRetryAfter acepta segundos y fecha HTTP', () => {
    expect(parseRetryAfter('5')).toBe(5000);
    expect(parseRetryAfter(null)).toBeNull();
    const futuro = new Date(Date.now() + 4000).toUTCString();
    const ms = parseRetryAfter(futuro);
    expect(ms).not.toBeNull();
    expect(ms!).toBeGreaterThan(0);
    expect(ms!).toBeLessThanOrEqual(4000);
  });
});

