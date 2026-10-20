import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { ClienteService } from '../../../core/services/cliente.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { VentasService } from '../../../core/services/ventas.service';
import {
  apiError,
  authData,
  envelopePage,
  personaAdmin,
  producto,
} from '../testing/ventas.fixtures';
import { ProductosListComponent } from './productos-list.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

function boton(fixture: ComponentFixture<unknown>, texto: string): HTMLButtonElement {
  const botones = Array.from(
    (fixture.nativeElement as HTMLElement).querySelectorAll('button')
  ) as HTMLButtonElement[];
  const encontrado = botones.find((b) => (b.textContent ?? '').trim() === texto);
  if (!encontrado) throw new Error(`No hay botón "${texto}"`);
  return encontrado;
}

describe('ProductosListComponent', () => {
  let fixture: ComponentFixture<ProductosListComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductosListComponent],
      providers: [
        SessionService,
        SedeContextService,
        VentasService,
        ClienteService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), personaAdmin);
    TestBed.inject(SedeContextService).selectedId.set(1);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductosListComponent);
  });

  afterEach(() => http.verify());

  async function cargar(
    data = [producto()],
    page = 0,
    size = 20,
    total = data.length
  ): Promise<void> {
    fixture.detectChanges();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/producto/listar` &&
          r.params.get('idHotel') === '1' &&
          r.params.get('page') === String(page) &&
          r.params.get('size') === '20' &&
          r.params.get('estado') === 'true'
      )
      .flush(envelopePage(data, page, size, total));
    await microtasks();
    fixture.detectChanges();
  }

  it('pide los activos de la sede y muestra precios y cantidades del backend', async () => {
    await cargar([
      producto({ precio: 1234.5, cantidad: 20, umbralBajo: 5, sku: 'AGUA-500' }),
    ]);
    const texto = (fixture.nativeElement.textContent as string).replace(/\u00a0/g, ' ');
    expect(texto).toContain('AGUA-500');
    expect(texto).toContain('S/ 1,234.50');
    expect(texto).toContain('20');
    expect(texto).toContain('Activo');
    expect(texto).toContain('Página 1 de 1 · 1 registros');
  });

  it('avanza y retrocede la paginación desde meta', async () => {
    await cargar([producto()], 0, 20, 21);
    expect(fixture.nativeElement.textContent).toContain('Página 1 de 2 · 21 registros');

    boton(fixture, 'Siguiente').click();
    await microtasks();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/producto/listar` && r.params.get('page') === '1'
      )
      .flush(envelopePage([producto({ idProducto: 2 })], 1, 20, 21));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Página 2 de 2 · 21 registros');

    boton(fixture, 'Anterior').click();
    await microtasks();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/producto/listar` && r.params.get('page') === '0'
      )
      .flush(envelopePage([producto()], 0, 20, 21));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Página 1 de 2 · 21 registros');
  });

  it('filtra por dados de baja enviando estado=false', async () => {
    await cargar();
    const select = (fixture.nativeElement as HTMLElement).querySelector(
      '#filtro-estado-producto'
    ) as HTMLSelectElement;
    select.value = 'inactivos';
    select.dispatchEvent(new Event('change'));
    await microtasks();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/producto/listar` &&
          r.params.get('estado') === 'false' &&
          r.params.get('page') === '0'
      )
      .flush(envelopePage([producto({ estado: false })], 0, 20, 1));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Dado de baja');
  });

  it('muestra «Bajo stock» solo cuando cantidad ≤ umbralBajo', async () => {
    await cargar([
      producto({ idProducto: 1, cantidad: 3, umbralBajo: 5 }),
      producto({ idProducto: 2, cantidad: 6, umbralBajo: 5 }),
      producto({ idProducto: 3, cantidad: 5, umbralBajo: 5 }),
    ]);
    const texto = fixture.nativeElement.textContent as string;
    const bajos = texto.split('Bajo stock').length - 1;
    expect(bajos).toBe(2);
  });

  it('el 503 muestra el mensaje del catálogo, sin precios de respaldo, y reintenta', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/producto/listar`)
      .flush(apiError('DEPENDENCY_UNAVAILABLE'), { status: 503, statusText: 'Service Unavailable' });
    await microtasks();
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('El servicio no está disponible. Reintenta en unos segundos.');
    expect(texto).toContain('DEPENDENCY_UNAVAILABLE');
    expect(texto).not.toContain('AGUA-500');
    expect(texto).not.toContain('S/');

    boton(fixture, 'Reintentar').click();
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/producto/listar`)
      .flush(envelopePage([producto()], 0, 20, 1));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('AGUA-500');
  });

  it('sin sede seleccionada no pide el listado', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ProductosListComponent],
      providers: [
        SessionService,
        SedeContextService,
        VentasService,
        ClienteService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), personaAdmin);
    const httpVacio = TestBed.inject(HttpTestingController);
    const sinSede = TestBed.createComponent(ProductosListComponent);
    sinSede.detectChanges();
    httpVacio.expectNone(`${API_BASE_URL}/api/producto/listar`);
    expect(sinSede.nativeElement.textContent).toContain('Sin sede asignada');
    httpVacio.verify();
  });
});
