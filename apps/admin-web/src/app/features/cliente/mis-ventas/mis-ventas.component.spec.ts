import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { ClienteContextService } from '../../../core/services/cliente-context.service';
import { ClienteService } from '../../../core/services/cliente.service';
import { SessionService } from '../../../core/services/session.service';
import {
  envelopePage,
  PERSONA_CLIENTE,
  recepcion,
  tokenConSub,
  venta,
} from '../testing/cliente.fixtures';
import { MisVentasComponent } from './mis-ventas.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
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

describe('MisVentasComponent', () => {
  let fixture: ComponentFixture<MisVentasComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MisVentasComponent],
      providers: [
        SessionService,
        ClienteService,
        ClienteContextService,
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
    fixture = TestBed.createComponent(MisVentasComponent);
  });

  afterEach(() => http.verify());

  it('sin consumos muestra el vacío exacto', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/cliente/100`)
      .flush(envelopePage([], 0, 100, 0));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Aún no tienes consumos');
  });

  it('lista las ventas propias con las dos píldoras separadas y pagina', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/cliente/100`)
      .flush(
        envelopePage(
          [
            recepcion(),
            recepcion({
              idRecepcion: 1002,
              fechaEntrada: '2026-09-01',
              estadoEstadia: 'CERRADA',
            }),
          ],
          0,
          100,
          2
        )
      );
    await microtasks();
    fixture.detectChanges();

    const primeras = Array.from({ length: 20 }, (_, i) =>
      venta({
        idVenta: 5001 + i,
        fechaCreacion: `2026-10-${String(20 - (i % 20)).padStart(2, '0')}T10:00:00Z`,
      })
    );
    primeras.push(
      venta({
        idVenta: 9999,
        idCliente: 999,
        detalles: [
          {
            idDetalleVenta: 9,
            idProducto: 9,
            nombreProducto: 'Producto ajeno',
            cantidad: 1,
            precioUnitario: 1,
            subTotal: 1,
          },
        ],
      })
    );
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta/recepcion/1001`)
      .flush(envelopePage(primeras, 0, 100, primeras.length));
    await microtasks();
    fixture.detectChanges();

    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta/recepcion/1002`)
      .flush(
        envelopePage(
          [venta({ idVenta: 7001, idRecepcion: 1002, fechaCreacion: '2026-09-02T10:00:00Z' })],
          0,
          100,
          1
        )
      );
    await microtasks();
    fixture.detectChanges();

    const texto = (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
    expect(texto).toContain('Página 1 de 2 · 21 registros');
    expect(texto).toContain('Pago pendiente');
    expect(texto).toContain('Confirmada');
    expect(texto).toContain('S/ 7.00');
    expect(texto).not.toContain('Producto ajeno');

    const filas = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    expect(filas.length).toBe(20);
    const primeraFila = filas[0] as HTMLElement;
    const pills = primeraFila.querySelectorAll('.pill');
    expect(pills.length).toBe(2);
    expect(pills[0].textContent).toContain('Pago');
    expect(pills[1].textContent).toContain('Confirmada');

    boton(fixture, 'Siguiente').click();
    fixture.detectChanges();
    const segundaPagina = (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
    expect(segundaPagina).toContain('Página 2 de 2 · 21 registros');
    expect(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr').length
    ).toBe(1);
  });
});
