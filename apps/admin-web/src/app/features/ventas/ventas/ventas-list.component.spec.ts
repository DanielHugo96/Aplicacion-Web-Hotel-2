import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { VentasService } from '../../../core/services/ventas.service';
import {
  apiError,
  authData,
  envelopePage,
  operacionVenta,
  personaAdmin,
  venta,
} from '../testing/ventas.fixtures';
import { VentasListComponent } from './ventas-list.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

describe('VentasListComponent', () => {
  let fixture: ComponentFixture<VentasListComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [VentasListComponent],
      providers: [
        SessionService,
        SedeContextService,
        VentasService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), personaAdmin);
    TestBed.inject(SedeContextService).selectedId.set(1);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(VentasListComponent);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  async function cargar(data = [venta()], page = 0, total = data.length): Promise<void> {
    fixture.detectChanges();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/venta` &&
          r.method === 'GET' &&
          r.params.get('idHotel') === '1' &&
          r.params.get('page') === '0' &&
          r.params.get('size') === '20'
      )
      .flush(envelopePage(data, page, 20, total));
    await microtasks();
    fixture.detectChanges();
  }

  it('muestra el skeleton mientras carga las ventas', async () => {
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.skeleton-table')).toBeTruthy();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta`)
      .flush(envelopePage([venta()]));
    await microtasks();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.skeleton-table')).toBeFalsy();
  });

  it('filtra por pago y operación como query params y pagina desde meta', async () => {
    await cargar([venta()], 0, 21);
    const estado = (fixture.nativeElement as HTMLElement).querySelector(
      '#filtro-pago'
    ) as HTMLSelectElement;
    estado.value = 'PAGADO';
    estado.dispatchEvent(new Event('change'));
    await microtasks();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/venta` && r.params.get('estado') === 'PAGADO'
      )
      .flush(envelopePage([venta({ estado: 'PAGADO' })], 0, 20, 21));
    await microtasks();

    const operacion = (fixture.nativeElement as HTMLElement).querySelector(
      '#filtro-operacion'
    ) as HTMLSelectElement;
    operacion.value = 'PENDIENTE';
    operacion.dispatchEvent(new Event('change'));
    await microtasks();
    const req = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/venta` &&
        r.params.get('estado') === 'PAGADO' &&
        r.params.get('estadoOperacion') === 'PENDIENTE'
    );
    // La fila PENDIENTE dispara el sondeo de su operación.
    req.flush(envelopePage([venta({ estadoOperacion: 'CONFIRMADA' })], 0, 20, 21));
    await microtasks();
  });

  it('muestra pago y operación en píldoras separadas e importes a la derecha', async () => {
    await cargar([
      venta({ estado: 'PENDIENTE', estadoOperacion: 'CONFIRMADA', total: 1234.5 }),
    ]);
    const pills = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('span.pill')
    ).map((p) => (p.textContent ?? '').trim());
    expect(pills).toContain('Pendiente');
    expect(pills).toContain('Confirmada');
    expect(pills.length).toBeGreaterThanOrEqual(2);
    const texto = (fixture.nativeElement.textContent as string).replace(/\u00a0/g, ' ');
    expect(texto).toContain('S/ 1,234.50');
    expect(texto).toContain('Página 1 de 1 · 1 registros');
  });

  it('solo sondea las filas no terminales', async () => {
    await cargar([
      venta({ idVenta: 1, estadoOperacion: 'CONFIRMADA', operationId: 'op-confirmada' }),
      venta({ idVenta: 2, estadoOperacion: 'PENDIENTE', operationId: 'op-pendiente' }),
    ]);
    http.expectNone(`${API_BASE_URL}/api/venta/operaciones/op-confirmada`);
    const req = http.expectOne(`${API_BASE_URL}/api/venta/operaciones/op-pendiente`);
    expect(req.request.method).toBe('GET');
    req.flush(
      {
        success: true,
        message: 'OK',
        data: operacionVenta({ operationId: 'op-pendiente', estadoOperacion: 'CONFIRMADA' }),
        meta: { correlationId: 'cid-op' },
      }
    );
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta` && r.method === 'GET')
      .flush(envelopePage([venta({ estadoOperacion: 'CONFIRMADA' })]));
    await microtasks();
  });

  it('refresca el listado al llegar al estado terminal', async () => {
    await cargar([venta({ estadoOperacion: 'PENDIENTE' })]);
    http
      .expectOne(
        (r) => r.url === `${API_BASE_URL}/api/venta/operaciones/55555555-5555-4555-8555-555555555555`
      )
      .flush(
        {
          success: true,
          message: 'OK',
          data: operacionVenta({ estadoOperacion: 'CONFIRMADA' }),
          meta: { correlationId: 'cid-op' },
        }
      );
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta` && r.method === 'GET')
      .flush(envelopePage([venta({ estadoOperacion: 'CONFIRMADA' })]));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Confirmada');
  });

  it('muestra «No hay ventas» y un 412 permite recargar', async () => {
    await cargar([]);
    expect(fixture.nativeElement.textContent).toContain('No hay ventas');

    fixture.componentInstance.cargar(0);
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta`)
      .flush(apiError('PRECONDITION_FAILED'), { status: 412, statusText: 'Precondition Failed' });
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'Otra persona modificó este registro. Recarga para ver los cambios.'
    );
    expect(fixture.nativeElement.textContent).toContain('Recargar');
  });
});
