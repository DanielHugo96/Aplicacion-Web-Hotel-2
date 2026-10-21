import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { ClienteService } from '../../../core/services/cliente.service';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { VentasService } from '../../../core/services/ventas.service';
import {
  apiError,
  authData,
  carritoItem,
  envelopePage,
  envelopeSingle,
  operacionVenta,
  personaAdmin,
  producto,
  venta,
} from '../testing/ventas.fixtures';
import { recepcion } from '../../recepcion/testing/recepcion.fixtures';
import { VentaNuevaComponent } from './venta-nueva.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

function esperar(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

describe('VentaNuevaComponent', () => {
  let fixture: ComponentFixture<VentaNuevaComponent>;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [VentaNuevaComponent],
      providers: [
        SessionService,
        SedeContextService,
        VentasService,
        RecepcionService,
        ClienteService,
        ToastService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { queryParamMap: convertToParamMap({}) } },
        },
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), personaAdmin);
    TestBed.inject(SedeContextService).selectedId.set(1);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(VentaNuevaComponent);
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  async function abrir(
    items = [carritoItem()],
    catalogo = [producto()]
  ): Promise<void> {
    fixture.detectChanges();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/recepcion/listar` &&
          r.params.get('idHotel') === '1' &&
          r.params.get('estadoEstadia') === 'ACTIVA'
      )
      .flush(envelopePage([recepcion()]));
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/carrito/listar/1001`)
      .flush(envelopePage(items));
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/venta/catalogo` &&
          r.params.get('idRecepcion') === '1001'
      )
      .flush(envelopePage(catalogo));
    await microtasks();
    fixture.detectChanges();
  }

  it('sin estadía activa no pide carrito ni catálogo', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/listar`)
      .flush(envelopePage([]));
    await microtasks();
    fixture.detectChanges();
    http.expectNone(`${API_BASE_URL}/api/carrito/listar/1001`);
    http.expectNone(`${API_BASE_URL}/api/venta/catalogo`);
    expect(fixture.nativeElement.textContent).toContain('No hay una estadía activa en esta sede');
  });

  it('carga carrito y catálogo de la estadía y suma al agregar', async () => {
    await abrir([], [producto({ precio: 3.5 })]);
    expect(fixture.nativeElement.textContent).toContain('El carrito está vacío');
    const agregar = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button')
    ).find((b) => (b.textContent ?? '').trim() === 'Agregar') as HTMLButtonElement;
    agregar.click();
    await microtasks();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/carrito/agregar` &&
          r.params.get('idRecepcion') === '1001' &&
          r.params.get('idProducto') === '1' &&
          r.params.get('cantidad') === '1'
      )
      .flush(envelopeSingle(carritoItem({ cantidad: 2 })), { status: 200, statusText: 'OK' });
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/carrito/listar/1001`)
      .flush(envelopePage([carritoItem({ cantidad: 2 })]));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.componentInstance.carrito()[0].cantidad).toBe(2);
    expect((fixture.nativeElement.textContent as string).replace(/ /g, ' ')).toContain(
      'S/ 3.50'
    );
  });

  it('quitar elimina la línea y cambiar cantidad es quitar y agregar', async () => {
    await abrir([carritoItem({ cantidad: 2 })]);
    void fixture.componentInstance.cambiarCantidad(fixture.componentInstance.carrito()[0], 3);
    await microtasks();
    http
      .expectOne(
        (r) => r.url === `${API_BASE_URL}/api/carrito/eliminar` && r.method === 'DELETE'
      )
      .flush(null, { status: 204, statusText: 'No Content' });
    await microtasks();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/carrito/agregar` &&
          r.params.get('cantidad') === '3'
      )
      .flush(envelopeSingle(carritoItem({ cantidad: 3 })), { status: 200, statusText: 'OK' });
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/carrito/listar/1001`)
      .flush(envelopePage([carritoItem({ cantidad: 3 })]));
    await microtasks();

    void fixture.componentInstance.quitar(fixture.componentInstance.carrito()[0]);
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/carrito/eliminar`)
      .flush(null, { status: 204, statusText: 'No Content' });
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/carrito/listar/1001`)
      .flush(envelopePage([]));
    await microtasks();
    expect(fixture.nativeElement.textContent).toContain('El carrito está vacío');
  });

  it('valida cantidad y el máximo de 50 productos distintos', async () => {
    const muchos = Array.from({ length: 50 }, (_, i) =>
      carritoItem({ idProducto: i + 1, idCarrito: i + 1 })
    );
    await abrir(muchos, [producto({ idProducto: 99 })]);
    void fixture.componentInstance.cambiarCantidad(fixture.componentInstance.carrito()[0], 0);
    await microtasks();
    http.expectNone(`${API_BASE_URL}/api/carrito/agregar`);
    expect(fixture.nativeElement.textContent).toContain('Cantidad inválida');

    void fixture.componentInstance.agregar(producto({ idProducto: 99 }));
    await microtasks();
    http.expectNone(`${API_BASE_URL}/api/carrito/agregar`);
    expect(fixture.nativeElement.textContent).toContain('Máximo 50 productos distintos');
  });

  it('el catálogo caído (503) no muestra precios de respaldo y reintenta', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/listar`)
      .flush(envelopePage([recepcion()]));
    await microtasks();
    http.expectOne(`${API_BASE_URL}/api/carrito/listar/1001`).flush(envelopePage([]));
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta/catalogo`)
      .flush(apiError('DEPENDENCY_UNAVAILABLE'), {
        status: 503,
        statusText: 'Service Unavailable',
      });
    await microtasks();
    fixture.detectChanges();
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('El servicio no está disponible. Reintenta en unos segundos.');
    expect(texto).not.toContain('S/');

    const reintentar = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button')
    ).filter((b) => (b.textContent ?? '').trim() === 'Reintentar');
    (reintentar[reintentar.length - 1] as HTMLButtonElement).click();
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta/catalogo`)
      .flush(envelopePage([producto()]));
    await microtasks();
    expect((fixture.nativeElement.textContent as string).replace(/ /g, ' ')).toContain(
      'S/ 3.50'
    );
  });

  it('201 confirma con cuerpo cerrado y navega al detalle', async () => {
    await abrir([carritoItem({ cantidad: 2 })]);
    void fixture.componentInstance.confirmar(new Event('submit'));
    await microtasks();
    const post = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/venta` && r.method === 'POST'
    );
    expect(post.request.body).toEqual({
      idRecepcion: 1001,
      estado: 'PENDIENTE',
      detalles: [{ idProducto: 1, cantidad: 2 }],
    });
    for (const prohibido of ['idHotel', 'idCliente', 'precioUnitario', 'subTotal', 'total', 'estadoOperacion']) {
      expect(post.request.body).not.toHaveProperty(prohibido);
    }
    post.flush(envelopeSingle(venta()), { status: 201, statusText: 'Created' });
    await microtasks();
    expect(TestBed.inject(ToastService).message()).toBe('Venta registrada');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/app/ventas/5001');
  });

  it('202 abre el banner y sondea hasta el terminal antes de navegar', async () => {
    await abrir([carritoItem()]);
    void fixture.componentInstance.confirmar(new Event('submit'));
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta` && r.method === 'POST')
      .flush(envelopeSingle(venta({ estadoOperacion: 'PENDIENTE' })), {
        status: 202,
        statusText: 'Accepted',
        headers: { Location: '/api/venta/5001', 'Retry-After': '0.001' },
      });
    await microtasks();
    fixture.detectChanges();
    expect(fixture.componentInstance.pendiente()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain(
      'Operación en proceso. No repitas la acción.'
    );
    expect(router.navigateByUrl).not.toHaveBeenCalled();

    await esperar(20);
    http
      .expectOne(`${API_BASE_URL}/api/venta/5001`)
      .flush(
        envelopeSingle(venta({ estadoOperacion: 'CONFIRMADA' })),
        { status: 200, statusText: 'OK' }
      );
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/venta/operaciones/55555555-5555-4555-8555-555555555555`)
      .flush(
        envelopeSingle(operacionVenta({ estadoOperacion: 'CONFIRMADA' }))
      );
    await microtasks();
    expect(fixture.componentInstance.pendiente()).toBe(false);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/app/ventas/5001');
  });

  it('409 de stock y producto inactivo se pintan en la línea afectada', async () => {
    await abrir([carritoItem({ idProducto: 1 })]);
    void fixture.componentInstance.confirmar(new Event('submit'));
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta` && r.method === 'POST')
      .flush(
        apiError('STOCK_INSUFFICIENT', { idProducto: '1', cantidad: 'No hay stock suficiente' }),
        { status: 409, statusText: 'Conflict' }
      );
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No hay stock suficiente');
    expect(fixture.nativeElement.textContent).toContain(
      'No hay stock suficiente de uno o más productos.'
    );

    void fixture.componentInstance.confirmar(new Event('submit'));
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta` && r.method === 'POST')
      .flush(apiError('PRODUCT_INACTIVE', { idProducto: '1' }), {
        status: 409,
        statusText: 'Conflict',
      });
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'El producto ya no está disponible en esta sede.'
    );
  });

  it('400 de validación resalta el campo y la línea por índice', async () => {
    await abrir([carritoItem({ cantidad: 2 })]);
    void fixture.componentInstance.confirmar(new Event('submit'));
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta` && r.method === 'POST')
      .flush(apiError('VALIDATION_ERROR', { 'detalles[0].cantidad': 'Cantidad inválida' }), {
        status: 400,
        statusText: 'Bad Request',
      });
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Revisa los datos marcados.');
    expect(fixture.nativeElement.textContent).toContain('Cantidad inválida');
  });

  it('PAGADO exige medio de pago y lo envía en el cuerpo cerrado', async () => {
    await abrir([carritoItem()]);
    fixture.componentInstance.estado = 'PAGADO';
    void fixture.componentInstance.confirmar(new Event('submit'));
    await microtasks();
    http.expectNone(`${API_BASE_URL}/api/venta`);
    expect(fixture.nativeElement.textContent).toContain('Selecciona el medio de pago');

    fixture.componentInstance.medioPago = 'EFECTIVO';
    void fixture.componentInstance.confirmar(new Event('submit'));
    await microtasks();
    const post = http.expectOne(`${API_BASE_URL}/api/venta`);
    expect(post.request.body).toEqual({
      idRecepcion: 1001,
      estado: 'PAGADO',
      medioPago: 'EFECTIVO',
      detalles: [{ idProducto: 1, cantidad: 2 }],
    });
    post.flush(envelopeSingle(venta({ estado: 'PAGADO' })), {
      status: 201,
      statusText: 'Created',
    });
    await microtasks();
  });
});
