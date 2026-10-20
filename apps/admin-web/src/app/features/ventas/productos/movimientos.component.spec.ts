import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { VentasService } from '../../../core/services/ventas.service';
import {
  apiError,
  authData,
  envelopePage,
  envelopeSingle,
  movimientoAjuste,
  movimientoVenta,
  personaAdmin,
  producto,
} from '../testing/ventas.fixtures';
import { MovimientosComponent } from './movimientos.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

describe('MovimientosComponent', () => {
  let fixture: ComponentFixture<MovimientosComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MovimientosComponent],
      providers: [
        SessionService,
        SedeContextService,
        VentasService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '1' }) } },
        },
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Empleado', [1]), personaAdmin);
    TestBed.inject(SedeContextService).selectedId.set(1);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MovimientosComponent);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  async function cargar(data = [movimientoVenta(), movimientoAjuste()]): Promise<void> {
    fixture.detectChanges();
    http
      .expectOne(`${API_BASE_URL}/api/producto/buscar/1?idHotel=1`)
      .flush(envelopeSingle(producto()));
    await microtasks();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/producto/1/movimientos` &&
          r.params.get('idHotel') === '1' &&
          r.params.get('page') === '0' &&
          r.params.get('size') === '20'
      )
      .flush(envelopePage(data));
    await microtasks();
    fixture.detectChanges();
  }

  it('pinta las dos ramas de la unión con estado literal y delta con signo', async () => {
    await cargar([
      movimientoVenta({ estado: 'DESCONTADO', cantidad: 2, precioUnitario: 1234.5 }),
      movimientoAjuste({ delta: -3, motivo: 'Merma de inventario' }),
    ]);
    const texto = (fixture.nativeElement.textContent as string).replace(/\u00a0/g, ' ');
    expect(texto).toContain('Venta');
    expect(texto).toContain('#5001');
    expect(texto).toContain('DESCONTADO');
    expect(texto).toContain('S/ 1,234.50');
    expect(texto).toContain('Ajuste');
    expect(texto).toContain('\u22123');
    expect(texto).toContain('Merma de inventario');
  });

  it('muestra el vacío exacto «Sin movimientos en el rango»', async () => {
    await cargar([]);
    expect(fixture.nativeElement.textContent).toContain('Sin movimientos en el rango');
  });

  it('envía el rango elegido en ISO-8601 y permite limpiarlo', async () => {
    await cargar();
    fixture.componentInstance.inicio = '2026-10-01';
    fixture.componentInstance.fin = '2026-10-20';
    fixture.componentInstance.filtrar();
    await microtasks();
    const req = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/producto/1/movimientos` &&
        r.params.get('page') === '0'
    );
    expect(req.request.params.get('inicio')).toBe(
      new Date('2026-10-01T00:00:00').toISOString()
    );
    expect(req.request.params.get('fin')).toBe(new Date('2026-10-20T23:59:59').toISOString());
    req.flush(envelopePage([movimientoVenta()]));
    await microtasks();

    fixture.componentInstance.limpiar();
    await microtasks();
    const limpio = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/producto/1/movimientos`
    );
    expect(limpio.request.params.has('inicio')).toBe(false);
    expect(limpio.request.params.has('fin')).toBe(false);
    limpio.flush(envelopePage([]));
    await microtasks();
  });

  it('el 503 muestra el mensaje del catálogo sin precios y reintenta', async () => {
    fixture.detectChanges();
    http
      .expectOne(`${API_BASE_URL}/api/producto/buscar/1?idHotel=1`)
      .flush(envelopeSingle(producto()));
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/producto/1/movimientos`)
      .flush(apiError('DEPENDENCY_UNAVAILABLE'), { status: 503, statusText: 'Service Unavailable' });
    await microtasks();
    fixture.detectChanges();
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('El servicio no está disponible. Reintenta en unos segundos.');
    expect(texto).not.toContain('S/');

    const boton = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button')
    ).find((b) => (b.textContent ?? '').trim() === 'Reintentar') as HTMLButtonElement;
    boton.click();
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/producto/1/movimientos`)
      .flush(envelopePage([movimientoVenta()]));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('DESCONTADO');
  });
});
