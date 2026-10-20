import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { VentasService } from '../../../core/services/ventas.service';
import {
  apiError,
  authData,
  envelopeSingle,
  operacionVenta,
  personaAdmin,
  venta,
  ventaDetalle,
} from '../testing/ventas.fixtures';
import { VentaDetalleComponent } from './venta-detalle.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

function esperar(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

describe('VentaDetalleComponent', () => {
  let fixture: ComponentFixture<VentaDetalleComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [VentaDetalleComponent],
      providers: [
        SessionService,
        VentasService,
        ToastService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '5001' }) } },
        },
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), personaAdmin);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(VentaDetalleComponent);
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    TestBed.resetTestingModule();
  });

  async function abrir(data = venta()): Promise<void> {
    fixture.detectChanges();
    http
      .expectOne(`${API_BASE_URL}/api/venta/buscar/5001`)
      .flush(envelopeSingle(data), { status: 200, statusText: 'OK', headers: { ETag: '"2"' } });
    await microtasks();
    fixture.detectChanges();
  }

  function enviarModal(): void {
    const form = (fixture.nativeElement as HTMLElement).querySelector(
      '.modal form'
    ) as HTMLFormElement;
    if (!form) throw new Error('No hay formulario en el diálogo');
    form.dispatchEvent(new Event('submit'));
  }

  it('pinta estado, importes y líneas tal como llegan del backend', async () => {
    await abrir(
      venta({
        total: 1234.5,
        medioPago: null,
        paidAt: null,
        fechaCreacion: '2026-10-20T15:05:00Z',
        detalles: [
          ventaDetalle({ nombreProducto: 'Agua 500ml', cantidad: 2, precioUnitario: 3.5, subTotal: 7 }),
        ],
      })
    );
    const texto = (fixture.nativeElement.textContent as string).replace(/\u00a0/g, ' ');
    expect(texto).toContain('S/ 1,234.50');
    expect(texto).toContain('Agua 500ml');
    expect(texto).toContain('S/ 7.00');
    expect(texto).toContain('No registrado');
    expect(texto).toContain('20/10/2026 10:05');
    const pills = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('span.pill')
    ).map((p) => (p.textContent ?? '').trim());
    expect(pills).toContain('Pendiente');
    expect(pills).toContain('Confirmada');
  });

  it('un 404 muestra el mensaje del catálogo sin datos de la venta', async () => {
    fixture.detectChanges();
    http
      .expectOne(`${API_BASE_URL}/api/venta/buscar/5001`)
      .flush(apiError('RESOURCE_NOT_FOUND'), { status: 404, statusText: 'Not Found' });
    await microtasks();
    fixture.detectChanges();
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('No encontramos lo que buscas.');
    expect(texto).not.toContain('Agua 500ml');
  });

  it('200 del pago envía cuerpo cerrado e If-Match y muestra «Pagado»', async () => {
    await abrir();
    (fixture.nativeElement as HTMLElement)
      .querySelectorAll('button')
      .forEach((b) => {
        if ((b.textContent ?? '').trim() === 'Registrar pago') (b as HTMLButtonElement).click();
      });
    fixture.detectChanges();
    fixture.componentInstance.medioPago = 'EFECTIVO';
    enviarModal();
    await microtasks();
    const put = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/venta/5001` && r.method === 'PUT'
    );
    expect(put.request.body).toEqual({ estado: 'PAGADO', medioPago: 'EFECTIVO' });
    expect(put.request.headers.get('If-Match')).toBe('"2"');
    put.flush(
      envelopeSingle(
        venta({
          estado: 'PAGADO',
          medioPago: 'EFECTIVO',
          paidAt: '2026-10-20T16:00:00Z',
          version: 3,
        })
      ),
      { status: 200, statusText: 'OK' }
    );
    await microtasks();
    fixture.detectChanges();
    expect(TestBed.inject(ToastService).message()).toBe('Pagado');
    const texto = (fixture.nativeElement.textContent as string).replace(/\u00a0/g, ' ');
    expect(texto).toContain('Pagado');
    expect(texto).toContain('EFECTIVO');
    expect(texto).toContain('20/10/2026 11:00');
  });

  it('202 del pago abre el banner, bloquea la acción y sondea hasta el terminal', async () => {
    await abrir();
    fixture.componentInstance.abrirPago();
    fixture.detectChanges();
    fixture.componentInstance.medioPago = 'TARJETA';
    enviarModal();
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta/5001` && r.method === 'PUT')
      .flush(
        envelopeSingle(venta({ estadoOperacion: 'PENDIENTE', operationId: 'op-pago' })),
        {
          status: 202,
          statusText: 'Accepted',
          headers: { Location: '/api/venta/5001', 'Retry-After': '0.001' },
        }
      );
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'Operación en proceso. No repitas la acción.'
    );
    expect(TestBed.inject(ToastService).message()).toBeNull();
    expect(fixture.componentInstance.puedePagar()).toBe(false);
    expect(fixture.componentInstance.pendiente()).toBe(true);

    await esperar(20);
    http
      .expectOne(`${API_BASE_URL}/api/venta/5001`)
      .flush(
        envelopeSingle(
          venta({
            estado: 'PAGADO',
            estadoOperacion: 'CONFIRMADA',
            operationId: 'op-pago',
            medioPago: 'TARJETA',
          })
        ),
        { status: 200, statusText: 'OK' }
      );
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/venta/operaciones/op-pago`)
      .flush(
        envelopeSingle(operacionVenta({ operationId: 'op-pago', estadoOperacion: 'CONFIRMADA' }))
      );
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/venta/buscar/5001`)
      .flush(
        envelopeSingle(
          venta({ estado: 'PAGADO', estadoOperacion: 'CONFIRMADA', medioPago: 'TARJETA' })
        ),
        { status: 200, statusText: 'OK', headers: { ETag: '"3"' } }
      );
    await microtasks();
    fixture.detectChanges();
    expect(fixture.componentInstance.pendiente()).toBe(false);
    expect(TestBed.inject(ToastService).message()).toBe('Pagado');
    expect(fixture.nativeElement.textContent).not.toContain('Operación en proceso.');
  });

  it('409 y 412 del pago muestran el catálogo', async () => {
    await abrir();
    for (const [codigo, status, textoEsperado] of [
      ['SALE_ALREADY_PAID', 409, 'Una venta pagada no se anula; requiere devolución (fuera del MVP).'],
      ['PRECONDITION_FAILED', 412, 'Otra persona modificó este registro. Recarga para ver los cambios.'],
    ] as const) {
      fixture.componentInstance.abrirPago();
      fixture.detectChanges();
      fixture.componentInstance.medioPago = 'EFECTIVO';
      enviarModal();
      await microtasks();
      http
        .expectOne((r) => r.url === `${API_BASE_URL}/api/venta/5001` && r.method === 'PUT')
        .flush(apiError(codigo), {
          status,
          statusText: status === 409 ? 'Conflict' : 'Precondition Failed',
        });
      await microtasks();
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain(textoEsperado);
    }
  });

  it('202 de la anulación muestra «Anulación en proceso» y explica la consecuencia', async () => {
    await abrir();
    fixture.componentInstance.abrirAnulacion();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'La venta quedará anulada y el stock volverá a la sede.'
    );
    fixture.componentInstance.motivo = 'Cliente desiste';
    enviarModal();
    await microtasks();
    const del = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/venta/5001` && r.method === 'DELETE'
    );
    expect(del.request.params.get('motivo')).toBe('Cliente desiste');
    expect(del.request.headers.get('If-Match')).toBe('"2"');
    del.flush(
      envelopeSingle(venta({ estadoOperacion: 'CANCELACION_PENDIENTE' })),
      {
        status: 202,
        statusText: 'Accepted',
        headers: { Location: '/api/venta/5001', 'Retry-After': '5' },
      }
    );
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Anulación en proceso');
    expect(fixture.nativeElement.textContent).toContain(
      'Operación en proceso. No repitas la acción.'
    );
    expect(fixture.componentInstance.puedeAnular()).toBe(false);
    // Cancela el sondeo pendiente para no dejar temporizadores abiertos.
    fixture.destroy();
    TestBed.resetTestingModule();
  });

  it('200 de la anulación confirma y un motivo vacío no envía', async () => {
    await abrir();
    fixture.componentInstance.abrirAnulacion();
    fixture.detectChanges();
    enviarModal();
    await microtasks();
    http.expectNone((r) => r.url === `${API_BASE_URL}/api/venta/5001` && r.method === 'DELETE');
    expect(fixture.nativeElement.textContent).toContain('Indica el motivo de la anulación');

    fixture.componentInstance.motivo = 'Error de cobro';
    enviarModal();
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta/5001` && r.method === 'DELETE')
      .flush(
        envelopeSingle(venta({ estado: 'PENDIENTE', estadoOperacion: 'CANCELADA' })),
        { status: 200, statusText: 'OK' }
      );
    await microtasks();
    fixture.detectChanges();
    expect(TestBed.inject(ToastService).message()).toBe('Venta anulada');
    expect(fixture.nativeElement.textContent).toContain('Cancelada');
  });

  it('los dos 409 de la anulación muestran su mensaje y acción', async () => {
    await abrir();
    for (const [codigo, textoEsperado] of [
      ['SALE_ALREADY_PAID', 'Una venta pagada no se anula; requiere devolución (fuera del MVP).'],
      ['LEGACY_MOVEMENT_UNVERIFIED', 'Esta venta antigua no puede anularse automáticamente.'],
    ] as const) {
      fixture.componentInstance.abrirAnulacion();
      fixture.detectChanges();
      fixture.componentInstance.motivo = 'Motivo válido';
      enviarModal();
      await microtasks();
      http
        .expectOne((r) => r.url === `${API_BASE_URL}/api/venta/5001` && r.method === 'DELETE')
        .flush(apiError(codigo), { status: 409, statusText: 'Conflict' });
      await microtasks();
      fixture.detectChanges();
      expect(fixture.nativeElement.textContent).toContain(textoEsperado);
    }
  });
});
