import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { PeTimestampPipe } from '../../../core/pipes/timestamp.pipe';
import { ClienteContextService } from '../../../core/services/cliente-context.service';
import { ClienteService } from '../../../core/services/cliente.service';
import { SessionService } from '../../../core/services/session.service';
import {
  envelopeSingle,
  error404,
  PERSONA_CLIENTE,
  tokenConSub,
  venta,
} from '../testing/cliente.fixtures';
import { VentaDetalleComponent } from './venta-detalle.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

describe('VentaDetalleComponent', () => {
  let fixture: ComponentFixture<VentaDetalleComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [VentaDetalleComponent],
      providers: [
        SessionService,
        ClienteService,
        ClienteContextService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => '5001' } } },
        },
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
    fixture = TestBed.createComponent(VentaDetalleComponent);
  });

  afterEach(() => http.verify());

  it('muestra píldoras separadas, total y líneas inmutables como llegan', async () => {
    fixture.detectChanges();
    const v = venta({
      total: 1234.5,
      fechaCreacion: '2026-10-20T15:30:00-05:00',
      medioPago: null,
      paidAt: null,
      detalles: [
        {
          idDetalleVenta: 1,
          idProducto: 1,
          nombreProducto: 'Agua 500ml',
          cantidad: 2,
          precioUnitario: 617.25,
          subTotal: 1234.5,
        },
      ],
    });
    http.expectOne(`${API_BASE_URL}/api/venta/buscar/5001`).flush(envelopeSingle(v));
    await microtasks();
    fixture.detectChanges();

    const texto = (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
    expect(texto).toContain('S/ 1,234.50');
    expect(texto).toContain(new PeTimestampPipe().transform(v.fechaCreacion)!);
    expect(texto).toContain('No registrado');
    expect(texto).toContain('Agua 500ml');
    expect(texto).toContain('S/ 617.25');
    const pills = (fixture.nativeElement as HTMLElement).querySelectorAll('.pill');
    expect(pills.length).toBe(2);
    expect(pills[0].textContent).toContain('Pago pendiente');
    expect(pills[1].textContent).toContain('Confirmada');
  });

  it('un 404 muestra el mensaje del catálogo sin datos', async () => {
    fixture.detectChanges();
    http
      .expectOne(`${API_BASE_URL}/api/venta/buscar/5001`)
      .flush(error404('cid-venta'), { status: 404, statusText: 'Not Found' });
    await microtasks();
    fixture.detectChanges();

    const texto = (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
    expect(texto).toContain('No encontramos lo que buscas.');
    expect(texto).toContain('cid-venta');
    expect(texto).not.toContain('Agua 500ml');
  });

  it('una venta de otra persona no se renderiza', async () => {
    fixture.detectChanges();
    http
      .expectOne(`${API_BASE_URL}/api/venta/buscar/5001`)
      .flush(envelopeSingle(venta({ idCliente: 999 })));
    await microtasks();
    fixture.detectChanges();

    const texto = (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
    expect(texto).toContain('No encontramos lo que buscas.');
    expect(texto).not.toContain('Agua 500ml');
  });
});
