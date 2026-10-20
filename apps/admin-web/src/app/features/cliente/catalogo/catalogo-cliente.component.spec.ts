import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
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
  PERSONA_CLIENTE,
  producto,
  recepcion,
  tokenConSub,
} from '../testing/cliente.fixtures';
import { CatalogoClienteComponent } from './catalogo-cliente.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

function boton(fixture: ComponentFixture<unknown>, texto: string): HTMLButtonElement {
  const botones = Array.from(
    (fixture.nativeElement as HTMLElement).querySelectorAll('button')
  ) as HTMLButtonElement[];
  const encontrado = botones.find((b) => (b.textContent ?? '').includes(texto));
  if (!encontrado) throw new Error(`No hay botón "${texto}"`);
  return encontrado;
}

describe('CatalogoClienteComponent', () => {
  let fixture: ComponentFixture<CatalogoClienteComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CatalogoClienteComponent],
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
    fixture = TestBed.createComponent(CatalogoClienteComponent);
  });

  afterEach(() => http.verify());

  async function cargarConEstadiaActiva(): Promise<void> {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/cliente/100`)
      .flush(envelopePage([recepcion()], 0, 100, 1));
    await microtasks();
    fixture.detectChanges();
  }

  it('sin estadía ACTIVA no pide catálogo y muestra el aviso de estadía', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/cliente/100`)
      .flush(envelopePage([recepcion({ estadoEstadia: 'CERRADA' })], 0, 100, 1));
    await microtasks();
    fixture.detectChanges();

    expect(((fixture.nativeElement.textContent as string).replace(/ /g, ' '))).toContain(
      'No tienes una estadía activa; consulta en recepción'
    );
    http.expectNone((r) => r.url === `${API_BASE_URL}/api/venta/catalogo`);
  });

  it('muestra el catálogo, avanza la paginación y agrega al carrito', async () => {
    await cargarConEstadiaActiva();
    const primera = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/venta/catalogo` &&
        r.params.get('idRecepcion') === '1001' &&
        r.params.get('page') === '0'
    );
    primera.flush(envelopePage([producto()], 0, 12, 13));
    await microtasks();
    fixture.detectChanges();

    const texto = (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
    expect(texto).toContain('Agua 500ml');
    expect(texto).toContain('S/ 3.50');
    expect(texto).toContain('Página 1 de 2 · 13 registros');

    boton(fixture, 'Siguiente').click();
    await microtasks();
    const segunda = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/venta/catalogo` && r.params.get('page') === '1'
    );
    segunda.flush(envelopePage([producto({ idProducto: 2, nombre: 'Café' })], 1, 12, 13));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Página 2 de 2 · 13 registros');

    boton(fixture, 'Agregar').click();
    const agregar = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/carrito/agregar` &&
        r.params.get('idRecepcion') === '1001' &&
        r.params.get('idProducto') === '2' &&
        r.params.get('cantidad') === '1'
    );
    agregar.flush(envelopeSingle(carritoItem({ idProducto: 2 })));
    await microtasks();
    expect(TestBed.inject(ToastService).message()).toBe('Agregado al carrito');
  });

  it('muestra el vacío exacto del catálogo', async () => {
    await cargarConEstadiaActiva();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta/catalogo`)
      .flush(envelopePage([], 0, 12, 0));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('El catálogo está vacío');
  });

  it('un 503 muestra el mensaje del catálogo y reintenta', async () => {
    await cargarConEstadiaActiva();
    http.expectOne((r) => r.url === `${API_BASE_URL}/api/venta/catalogo`).flush(
      {
        success: false,
        message: 'El servicio no está disponible. Reintenta en unos segundos.',
        data: null,
        error: { code: 'DEPENDENCY_UNAVAILABLE', fields: {} },
        meta: { correlationId: 'cid-503' },
      },
      { status: 503, statusText: 'Service Unavailable' }
    );
    await microtasks();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'El servicio no está disponible. Reintenta en unos segundos.'
    );
    expect(fixture.nativeElement.textContent).toContain('cid-503');
    boton(fixture, 'Reintentar').click();
    await microtasks();
    const reintento = http.expectOne((r) => r.url === `${API_BASE_URL}/api/venta/catalogo`);
    reintento.flush(envelopePage([producto()], 0, 12, 1));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Agua 500ml');
  });
});
