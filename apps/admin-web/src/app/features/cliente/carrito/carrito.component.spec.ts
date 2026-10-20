import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { ClienteContextService } from '../../../core/services/cliente-context.service';
import { ClienteService } from '../../../core/services/cliente.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  carritoItem,
  envelopePage,
  envelopeSingle,
  error409,
  PERSONA_CLIENTE,
  recepcion,
  tokenConSub,
  venta,
} from '../testing/cliente.fixtures';
import { CarritoComponent } from './carrito.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

function boton(fixture: ComponentFixture<unknown>, texto: string): HTMLButtonElement {
  const botones = Array.from(
    (fixture.nativeElement as HTMLElement).querySelectorAll('button')
  ) as HTMLButtonElement[];
  const encontrado = botones.find((b) => (b.textContent ?? '').trim().includes(texto));
  if (!encontrado) throw new Error(`No hay botón "${texto}"`);
  return encontrado;
}

describe('CarritoComponent', () => {
  let fixture: ComponentFixture<CarritoComponent>;
  let http: HttpTestingController;
  let navigateSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CarritoComponent],
      providers: [
        SessionService,
        ClienteService,
        ClienteContextService,
        ToastService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    TestBed.inject(SessionService).setSession(
      {
        idPersona: 100,
        nombre: 'Cliente',
        apellido: 'Uno',
        correo: 'cliente.uno@hotel.test',
        tipoPersona: 'Cliente',
        token: tokenConSub(100),
        expiresIn: 900,
        sedes: [],
      },
      PERSONA_CLIENTE
    );
    http = TestBed.inject(HttpTestingController);
    navigateSpy = vi
      .spyOn(TestBed.inject(Router), 'navigateByUrl')
      .mockResolvedValue(true as never);
    fixture = TestBed.createComponent(CarritoComponent);
  });

  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });

  async function cargarCarrito(items = [carritoItem()]): Promise<void> {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/cliente/100`)
      .flush(envelopePage([recepcion()], 0, 100, 1));
    await microtasks();
    fixture.detectChanges();
    http
      .expectOne(`${API_BASE_URL}/api/carrito/listar/1001`)
      .flush(envelopeSingle(items));
    await microtasks();
    fixture.detectChanges();
  }

  it('muestra el vacío exacto del carrito', async () => {
    await cargarCarrito([]);
    expect(fixture.nativeElement.textContent).toContain('Tu carrito está vacío');
  });

  it('cambia cantidad con eliminar + agregar y el doble clic no reenvía', async () => {
    await cargarCarrito();
    boton(fixture, '+').click();
    http
      .expectOne(
        (r) =>
          r.method === 'DELETE' &&
          r.url === `${API_BASE_URL}/api/carrito/eliminar` &&
          r.params.get('idRecepcion') === '1001' &&
          r.params.get('idProducto') === '1'
      )
      .flush(null, { status: 204, statusText: 'No Content' });

    // Segundo clic mientras la línea está ocupada: no abre otra intención.
    boton(fixture, '+').click();
    expect(
      http.match((r) => r.method === 'DELETE' && r.url === `${API_BASE_URL}/api/carrito/eliminar`)
        .length
    ).toBe(0);

    await microtasks();
    const alta = http.expectOne(
      (r) =>
        r.method === 'POST' &&
        r.url === `${API_BASE_URL}/api/carrito/agregar` &&
        r.params.get('cantidad') === '3'
    );
    alta.flush(envelopeSingle(carritoItem({ cantidad: 3 })));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.componentInstance.items()[0].cantidad).toBe(3);
  });

  it('un 400 de cantidad resalta el campo cantidad', async () => {
    await cargarCarrito();
    boton(fixture, '+').click();
    http
      .expectOne((r) => r.method === 'DELETE' && r.url === `${API_BASE_URL}/api/carrito/eliminar`)
      .flush(null, { status: 204, statusText: 'No Content' });

    await microtasks();
    http.expectOne((r) => r.method === 'POST' && r.url === `${API_BASE_URL}/api/carrito/agregar`)
      .flush(
        {
          success: false,
          message: 'Revisa los datos marcados.',
          data: null,
          error: { code: 'VALIDATION_ERROR', fields: { cantidad: 'máximo 9999 por línea' } },
          meta: { correlationId: 'cid-400' },
        },
        { status: 400, statusText: 'Bad Request' }
      );
    await microtasks();
    fixture.detectChanges();
    // La recarga silenciosa resincroniza la línea con el backend.
    http
      .expectOne(`${API_BASE_URL}/api/carrito/listar/1001`)
      .flush(envelopeSingle([carritoItem()]));
    await microtasks();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('máximo 9999 por línea');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.cantidad-input.error')
    ).not.toBeNull();
  });

  it('un 409 PRODUCT_INACTIVE se pinta en la línea afectada', async () => {
    await cargarCarrito();
    boton(fixture, 'Confirmar venta').click();
    http
      .expectOne((r) => r.method === 'POST' && r.url === `${API_BASE_URL}/api/venta`)
      .flush(error409('PRODUCT_INACTIVE', { idProducto: '1' }, 'cid-409'), {
        status: 409,
        statusText: 'Conflict',
      });
    await microtasks();
    fixture.detectChanges();

    const texto = (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
    expect(texto).toContain('El producto ya no está disponible en esta sede.');
    expect(texto).toContain('Quitarlo del carrito; recargar catálogo.');
    expect((fixture.nativeElement as HTMLElement).querySelector('.fila-error')).not.toBeNull();
  });

  it('un 409 STOCK_INSUFFICIENT con una sola línea se pinta en la línea', async () => {
    await cargarCarrito([carritoItem()]);
    boton(fixture, 'Confirmar venta').click();
    http
      .expectOne((r) => r.method === 'POST' && r.url === `${API_BASE_URL}/api/venta`)
      .flush(error409('STOCK_INSUFFICIENT', { cantidad: 'stock insuficiente' }, 'cid-stock'), {
        status: 409,
        statusText: 'Conflict',
      });
    await microtasks();
    fixture.detectChanges();

    const texto = (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
    expect(texto).toContain('No hay stock suficiente de uno o más productos.');
    expect(texto).toContain('Ajustar cantidades y crear una venta nueva.');
    expect((fixture.nativeElement as HTMLElement).querySelector('.linea-error')).not.toBeNull();
    expect(fixture.componentInstance.erroresLinea()[1].correlationId).toBe('cid-stock');
  });

  it('un 201 confirma y navega a mis ventas con el cuerpo cerrado', async () => {
    await cargarCarrito();
    boton(fixture, 'Confirmar venta').click();
    const req = http.expectOne((r) => r.method === 'POST' && r.url === `${API_BASE_URL}/api/venta`);
    expect(req.request.body).toEqual({
      idRecepcion: 1001,
      estado: 'PENDIENTE',
      detalles: [{ idProducto: 1, cantidad: 2 }],
    });
    req.flush(envelopeSingle(venta()), { status: 201, statusText: 'Created' });
    await microtasks();

    expect(TestBed.inject(ToastService).message()).toBe('Venta registrada');
    expect(navigateSpy).toHaveBeenCalledWith('/mis-ventas');
  });

  it('un 202 muestra el banner, sondea y nunca da éxito optimista', async () => {
    await cargarCarrito();
    vi.useFakeTimers();

    boton(fixture, 'Confirmar venta').click();
    http
      .expectOne((r) => r.method === 'POST' && r.url === `${API_BASE_URL}/api/venta`)
      .flush(envelopeSingle(venta({ estadoOperacion: 'PENDIENTE' })), {
        status: 202,
        statusText: 'Accepted',
        headers: { Location: '/api/venta/buscar/5001', 'Retry-After': '1' },
      });
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();

    const textoPendiente = (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
    expect(textoPendiente).toContain('Operación en proceso. No repitas la acción.');
    expect(textoPendiente).toContain('Actualizado');
    expect((fixture.nativeElement as HTMLElement).querySelector('.banner-pendiente')).not.toBeNull();
    expect(navigateSpy).not.toHaveBeenCalled();

    await vi.advanceTimersByTimeAsync(1000);
    http
      .expectOne(`${API_BASE_URL}/api/venta/buscar/5001`)
      .flush(envelopeSingle(venta({ estadoOperacion: 'CONFIRMADA' })));
    await vi.advanceTimersByTimeAsync(0);
    http
      .expectOne(
        `${API_BASE_URL}/api/venta/operaciones/20000000-0000-4000-8000-000000000001`
      )
      .flush(envelopeSingle({ operationId: 'op-1', idVenta: 5001, tipo: 'VENTA', estado: 'TERMINAL', estadoOperacion: 'CONFIRMADA' }));
    await vi.advanceTimersByTimeAsync(0);

    expect(navigateSpy).toHaveBeenCalledWith('/mis-ventas');
    expect(TestBed.inject(ToastService).message()).toBe('Venta registrada');
  });
});
