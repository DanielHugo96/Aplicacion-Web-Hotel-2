import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../api.config';
import { ClienteService, resolverLocation } from './cliente.service';
import {
  carritoItem,
  envelopePage,
  envelopeSingle,
  operacion,
  producto,
  recepcion,
  venta,
} from '../../features/cliente/testing/cliente.fixtures';

describe('ClienteService', () => {
  let service: ClienteService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ClienteService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ClienteService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  it('lista las estadías propias con el idCliente recibido y page/size', async () => {
    const promesa = service.listarEstadiasPropias(100, 0, 100);
    const req = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/recepcion/cliente/100` &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '100'
    );
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.has('X-User-Id')).toBe(false);
    expect(req.request.headers.has('X-Role')).toBe(false);
    expect(req.request.headers.has('X-Hotel-Id')).toBe(false);
    req.flush(envelopePage([recepcion()], 0, 100, 1));

    const res = await promesa;
    expect(res.data).toHaveLength(1);
    expect(res.page).toBe(0);
    expect(res.size).toBe(100);
    expect(res.totalElements).toBe(1);
  });

  it('busca el detalle propio y devuelve el ETag sin usarlo para mutar', async () => {
    const promesa = service.buscarEstadia(1001);
    const req = http.expectOne(`${API_BASE_URL}/api/recepcion/1001`);
    req.flush(envelopeSingle(recepcion()), { headers: { ETag: '"3"' } });

    const res = await promesa;
    expect(res.data.idRecepcion).toBe(1001);
    expect(res.etag).toBe('"3"');
  });

  it('lista el catálogo de la estadía ACTIVA con idRecepcion, page y size', async () => {
    const promesa = service.listarCatalogo(1001, 0, 12);
    const req = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/venta/catalogo` &&
        r.params.get('idRecepcion') === '1001' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '12'
    );
    req.flush(envelopePage([producto()], 0, 12, 1));
    const res = await promesa;
    expect(res.data[0].precio).toBe(3.5);
  });

  it('lista el carrito (meta simple) y agrega/quita con query params', async () => {
    const listado = service.listarCarrito(1001);
    const reqListado = http.expectOne(`${API_BASE_URL}/api/carrito/listar/1001`);
    reqListado.flush(envelopeSingle([carritoItem()]));
    expect((await listado)[0].nombreProducto).toBe('Agua 500ml');

    const alta = service.agregarAlCarrito(1001, 1, 2);
    const reqAlta = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/carrito/agregar` &&
        r.params.get('idRecepcion') === '1001' &&
        r.params.get('idProducto') === '1' &&
        r.params.get('cantidad') === '2'
    );
    expect(reqAlta.request.method).toBe('POST');
    reqAlta.flush(envelopeSingle(carritoItem()));
    expect((await alta).cantidad).toBe(2);

    const quitar = service.quitarDelCarrito(1001, 1);
    const reqQuitar = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/carrito/eliminar` &&
        r.params.get('idRecepcion') === '1001' &&
        r.params.get('idProducto') === '1'
    );
    expect(reqQuitar.request.method).toBe('DELETE');
    reqQuitar.flush(null, { status: 204, statusText: 'No Content' });
    await quitar;
  });

  it('crea la venta PENDIENTE con un cuerpo cerrado (sin precios ni IDs derivados)', async () => {
    const body = {
      idRecepcion: 1001,
      estado: 'PENDIENTE' as const,
      detalles: [{ idProducto: 1, cantidad: 2 }],
    };
    const promesa = service.crearVenta(body);
    const req = http.expectOne(`${API_BASE_URL}/api/venta`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    expect(Object.keys(req.request.body).sort()).toEqual(
      ['detalles', 'estado', 'idRecepcion'].sort()
    );
    expect(Object.keys(req.request.body.detalles[0]).sort()).toEqual(
      ['cantidad', 'idProducto'].sort()
    );
    req.flush(envelopeSingle(venta()), { status: 201, statusText: 'Created' });

    const res = await promesa;
    expect(res.kind).toBe('confirmada');
  });

  it('devuelve el 202 como pendiente con Location resuelto y Retry-After', async () => {
    const promesa = service.crearVenta({
      idRecepcion: 1001,
      estado: 'PENDIENTE',
      detalles: [{ idProducto: 1, cantidad: 2 }],
    });
    const req = http.expectOne(`${API_BASE_URL}/api/venta`);
    req.flush(envelopeSingle(venta({ estadoOperacion: 'PENDIENTE' })), {
      status: 202,
      statusText: 'Accepted',
      headers: { Location: '/api/venta/buscar/5001', 'Retry-After': '2' },
    });

    const res = await promesa;
    expect(res.kind).toBe('pendiente');
    if (res.kind === 'pendiente') {
      expect(res.location).toBe(`${API_BASE_URL}/api/venta/buscar/5001`);
      expect(res.retryAfterMs).toBe(2000);
    }
    expect(resolverLocation('/api/venta/buscar/5001')).toBe(
      `${API_BASE_URL}/api/venta/buscar/5001`
    );
    expect(resolverLocation('http://otro.test/x')).toBe('http://otro.test/x');
  });

  it('sondea Location, consulta la operación y se detiene en el estado terminal', async () => {
    vi.useFakeTimers();
    const onAvance = vi.fn();
    const promesa = service.seguirOperacion(
      `${API_BASE_URL}/api/venta/buscar/5001`,
      5,
      onAvance
    );

    await vi.advanceTimersByTimeAsync(5);
    const reqVenta = http.expectOne(`${API_BASE_URL}/api/venta/buscar/5001`);
    reqVenta.flush(envelopeSingle(venta({ estadoOperacion: 'PENDIENTE' })), {
      headers: { 'Retry-After': '1' },
    });

    await vi.advanceTimersByTimeAsync(0);
    const reqOperacion = http.expectOne(
      `${API_BASE_URL}/api/venta/operaciones/20000000-0000-4000-8000-000000000001`
    );
    reqOperacion.flush(envelopeSingle(operacion({ estado: 'TERMINAL' })));

    const resultado = await promesa;
    expect(onAvance).toHaveBeenCalledTimes(1);
    expect(onAvance.mock.calls[0][0].actualizadoEn).toBeInstanceOf(Date);
    expect(resultado?.operacion?.estado).toBe('TERMINAL');
  });

  it('busca una venta y lista las ventas de una estadía con paginación', async () => {
    const buscar = service.buscarVenta(5001);
    const reqBuscar = http.expectOne(`${API_BASE_URL}/api/venta/buscar/5001`);
    reqBuscar.flush(envelopeSingle(venta()));
    expect((await buscar).idVenta).toBe(5001);

    const listar = service.listarVentasRecepcion(1001, 1, 50);
    const reqListar = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/venta/recepcion/1001` &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '50'
    );
    reqListar.flush(envelopePage([venta()], 1, 50, 51));
    const res = await listar;
    expect(res.totalElements).toBe(51);
  });
});
