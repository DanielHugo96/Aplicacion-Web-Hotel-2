import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../api.config';
import { ClienteService } from './cliente.service';
import { VentasService } from './ventas.service';
import {
  carritoItem,
  envelopePage,
  envelopeSingle,
  movimientoAjuste,
  movimientoVenta,
  operacionVenta,
  producto,
  productoAlta,
  venta,
} from '../../features/ventas/testing/ventas.fixtures';

function sinHeadersDeIdentidad(req: { headers: { has(name: string): boolean } }): void {
  expect(req.headers.has('X-User-Id')).toBe(false);
  expect(req.headers.has('X-Role')).toBe(false);
  expect(req.headers.has('X-Hotel-Id')).toBe(false);
}

describe('VentasService', () => {
  let service: VentasService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        VentasService,
        ClienteService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(VentasService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista productos activos por defecto con idHotel, page, size y estado=true', async () => {
    const promesa = service.listarProductos(1, 0, 20);
    const req = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/producto/listar` &&
        r.params.get('idHotel') === '1' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '20' &&
        r.params.get('estado') === 'true'
    );
    expect(req.request.method).toBe('GET');
    sinHeadersDeIdentidad(req.request);
    req.flush(envelopePage([producto()], 0, 20, 1));
    const res = await promesa;
    expect(res.data[0].sku).toBe('AGUA-500');
    expect(res.totalElements).toBe(1);
  });

  it('envía estado=false para dados de baja y lo omite en «todos»', async () => {
    const inactivos = service.listarProductos(1, 0, 20, 'inactivos');
    const reqInactivos = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/producto/listar` && r.params.get('estado') === 'false'
    );
    reqInactivos.flush(envelopePage([], 0, 20, 0));
    await inactivos;

    const todos = service.listarProductos(1, 0, 20, 'todos');
    const reqTodos = http.expectOne((r) => r.url === `${API_BASE_URL}/api/producto/listar`);
    expect(reqTodos.request.params.has('estado')).toBe(false);
    reqTodos.flush(envelopePage([], 0, 20, 0));
    await todos;
  });

  it('busca un producto con idHotel y devuelve su ETag', async () => {
    const promesa = service.buscarProducto(1, 1);
    const req = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/producto/buscar/1` && r.params.get('idHotel') === '1'
    );
    req.flush(envelopeSingle(producto()), { headers: { ETag: '"3"' } });
    const res = await promesa;
    expect(res.data.idProducto).toBe(1);
    expect(res.etag).toBe('"3"');
  });

  it('registra un producto con el cuerpo cerrado del alta', async () => {
    const body = productoAlta();
    const promesa = service.registrarProducto(body);
    const req = http.expectOne(`${API_BASE_URL}/api/producto/registrar`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    expect(req.request.body).not.toHaveProperty('cantidad');
    expect(req.request.body).not.toHaveProperty('version');
    sinHeadersDeIdentidad(req.request);
    req.flush(envelopeSingle(producto()), { status: 201, statusText: 'Created' });
    expect((await promesa).sku).toBe('AGUA-500');
  });

  it('actualiza con If-Match, idHotel y productoVersion sin cantidad', async () => {
    const promesa = service.actualizarProducto(
      1,
      1,
      { nombre: 'Agua 1L', precio: 5, productoVersion: 2 },
      '"3"'
    );
    const req = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/producto/actualizar/1` && r.params.get('idHotel') === '1'
    );
    expect(req.request.method).toBe('PUT');
    expect(req.request.headers.get('If-Match')).toBe('"3"');
    expect(req.request.body).toEqual({ nombre: 'Agua 1L', precio: 5, productoVersion: 2 });
    expect('cantidad' in req.request.body).toBe(false);
    req.flush(envelopeSingle(producto({ version: 4 })), { headers: { ETag: '"4"' } });
    const res = await promesa;
    expect(res.etag).toBe('"4"');
  });

  it('da de baja con DELETE e If-Match', async () => {
    const promesa = service.eliminarProducto(1, 1, '"3"');
    const req = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/producto/eliminar/1` && r.params.get('idHotel') === '1'
    );
    expect(req.request.method).toBe('DELETE');
    expect(req.request.headers.get('If-Match')).toBe('"3"');
    req.flush(envelopeSingle(producto({ estado: false, version: 4 })));
    expect((await promesa).data.estado).toBe(false);
  });

  it('asigna a sede nueva con POST /api/producto/{id}/sedes', async () => {
    const body = { idHotel: 2, precio: 4, cantidadInicial: 10, umbralBajo: 2 };
    const promesa = service.asignarProductoSede(1, body);
    const req = http.expectOne(`${API_BASE_URL}/api/producto/1/sedes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush(envelopeSingle(producto({ idHotel: 2 })), {
      status: 201,
      statusText: 'Created',
    });
    expect((await promesa).idHotel).toBe(2);
  });

  it('ajusta stock con delta, motivo e If-Match', async () => {
    const promesa = service.ajustarStock(1, { idHotel: 1, delta: -3, motivo: 'Merma' }, '"3"');
    const req = http.expectOne(`${API_BASE_URL}/api/producto/1/ajustes`);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('If-Match')).toBe('"3"');
    expect(req.request.params.has('idHotel')).toBe(false);
    expect(req.request.body).toEqual({ idHotel: 1, delta: -3, motivo: 'Merma' });
    req.flush(
      envelopeSingle({ ajusteId: '40000000-0000-4000-8000-000000000004', cantidad: 17, version: 4 }),
      { status: 201, statusText: 'Created' }
    );
    const res = await promesa;
    expect(res.cantidad).toBe(17);
    expect(res.version).toBe(4);
  });

  it('lista movimientos con rango ISO y paginación', async () => {
    const promesa = service.listarMovimientos(
      1,
      1,
      0,
      20,
      '2026-10-01T00:00:00-05:00',
      '2026-10-31T00:00:00-05:00'
    );
    const req = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/producto/1/movimientos` &&
        r.params.get('idHotel') === '1' &&
        r.params.get('inicio') === '2026-10-01T00:00:00-05:00' &&
        r.params.get('fin') === '2026-10-31T00:00:00-05:00'
    );
    req.flush(envelopePage([movimientoVenta(), movimientoAjuste()], 0, 20, 2));
    const res = await promesa;
    expect(res.data.map((m) => m.tipo)).toEqual(['VENTA', 'AJUSTE']);
  });

  it('lista ventas con idHotel y filtros de pago y operación', async () => {
    const promesa = service.listarVentas(1, 0, 20, 'PENDIENTE', 'CANCELACION_PENDIENTE');
    const req = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/venta` &&
        r.params.get('idHotel') === '1' &&
        r.params.get('estado') === 'PENDIENTE' &&
        r.params.get('estadoOperacion') === 'CANCELACION_PENDIENTE'
    );
    req.flush(envelopePage([venta()], 0, 20, 1));
    expect((await promesa).data[0].idVenta).toBe(5001);
  });

  it('busca una venta y conserva el ETag del detalle', async () => {
    const promesa = service.buscarVenta(5001);
    const req = http.expectOne(`${API_BASE_URL}/api/venta/buscar/5001`);
    req.flush(envelopeSingle(venta()), { headers: { ETag: '"2"' } });
    const res = await promesa;
    expect(res.data.total).toBe(7);
    expect(res.etag).toBe('"2"');
  });

  it('registra el pago con cuerpo cerrado y devuelve 200 o 202', async () => {
    const ok = service.pagarVenta(5001, 'EFECTIVO', '"2"');
    const reqOk = http.expectOne(`${API_BASE_URL}/api/venta/5001`);
    expect(reqOk.request.method).toBe('PUT');
    expect(reqOk.request.headers.get('If-Match')).toBe('"2"');
    expect(reqOk.request.body).toEqual({ estado: 'PAGADO', medioPago: 'EFECTIVO' });
    reqOk.flush(envelopeSingle(venta({ estado: 'PAGADO', medioPago: 'EFECTIVO', paidAt: '2026-10-20T16:00:00Z' })));
    const confirmada = await ok;
    expect(confirmada.kind).toBe('confirmada');
    expect(confirmada.venta.estado).toBe('PAGADO');

    const pendiente = service.pagarVenta(5001, 'TARJETA', '"2"');
    const req202 = http.expectOne(`${API_BASE_URL}/api/venta/5001`);
    req202.flush(envelopeSingle(venta({ estadoOperacion: 'PENDIENTE' })), {
      status: 202,
      statusText: 'Accepted',
      headers: {
        Location: '/api/venta/buscar/5001',
        'Retry-After': '2',
      },
    });
    const enCurso = await pendiente;
    expect(enCurso.kind).toBe('pendiente');
    expect(enCurso.kind === 'pendiente' ? enCurso.location : null).toBe(
      `${API_BASE_URL}/api/venta/buscar/5001`
    );
    expect(enCurso.kind === 'pendiente' ? enCurso.retryAfterMs : null).toBe(2000);
  });

  it('anula con motivo en query, If-Match y lee 202 y 200', async () => {
    const pendiente = service.anularVenta(5001, 'Error de cobro', '"2"');
    const req = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/venta/5001` &&
        r.params.get('motivo') === 'Error de cobro'
    );
    expect(req.request.method).toBe('DELETE');
    expect(req.request.headers.get('If-Match')).toBe('"2"');
    req.flush(envelopeSingle(venta({ estadoOperacion: 'CANCELACION_PENDIENTE' })), {
      status: 202,
      statusText: 'Accepted',
      headers: { Location: '/api/venta/buscar/5001', 'Retry-After': '3' },
    });
    const res = await pendiente;
    expect(res.kind).toBe('pendiente');
    expect(res.venta.estadoOperacion).toBe('CANCELACION_PENDIENTE');

    const yaCancelada = service.anularVenta(5001, 'Error de cobro', '"2"');
    const req200 = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/venta/5001` &&
        r.params.get('motivo') === 'Error de cobro'
    );
    req200.flush(envelopeSingle(venta({ estadoOperacion: 'CANCELADA' })));
    const terminal = await yaCancelada;
    expect(terminal.kind).toBe('confirmada');
    expect(terminal.venta.estadoOperacion).toBe('CANCELADA');
  });

  it('crea la venta con JSON cerrado y consulta la operación compartida', async () => {
    const body = {
      idRecepcion: 1001,
      estado: 'PENDIENTE' as const,
      detalles: [{ idProducto: 1, cantidad: 2 }],
    };
    const promesa = service.crearVenta(body);
    const req = http.expectOne(`${API_BASE_URL}/api/venta`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    const serializado = JSON.stringify(req.request.body);
    for (const prohibido of ['idHotel', 'idCliente', 'precioUnitario', 'subTotal', 'total']) {
      expect(serializado).not.toContain(prohibido);
    }
    req.flush(envelopeSingle(venta()), { status: 201, statusText: 'Created' });
    const res = await promesa;
    expect(res.kind).toBe('confirmada');

    const operacion = service.consultarOperacion(operacionVenta().operationId);
    const reqOp = http.expectOne(
      `${API_BASE_URL}/api/venta/operaciones/55555555-5555-4555-8555-555555555555`
    );
    reqOp.flush(envelopeSingle(operacionVenta({ estado: 'TERMINAL', estadoOperacion: 'CONFIRMADA' })));
    expect((await operacion).estado).toBe('TERMINAL');
  });

  it('delega carrito y catálogo por la estadía sin mandar identidad por headers', async () => {
    const catalogo = service.listarCatalogo(1001, 0, 20);
    const reqCatalogo = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/venta/catalogo` && r.params.get('idRecepcion') === '1001'
    );
    sinHeadersDeIdentidad(reqCatalogo.request);
    reqCatalogo.flush(envelopePage([producto()], 0, 20, 1));
    expect((await catalogo).data[0].nombre).toBe('Agua 500ml');

    const carrito = service.listarCarrito(1001);
    const reqCarrito = http.expectOne(`${API_BASE_URL}/api/carrito/listar/1001`);
    reqCarrito.flush(envelopeSingle([carritoItem()]));
    expect((await carrito)[0].cantidad).toBe(2);
  });
});
