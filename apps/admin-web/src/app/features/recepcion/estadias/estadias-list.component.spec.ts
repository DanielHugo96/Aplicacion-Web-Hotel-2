import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { SessionService } from '../../../core/services/session.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { apiError, authData, envelopePage, PERSONA_ADMIN, recepcion } from '../testing/recepcion.fixtures';
import { EstadiasListComponent } from './estadias-list.component';

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

describe('EstadiasListComponent', () => {
  let fixture: ComponentFixture<EstadiasListComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [EstadiasListComponent],
      providers: [
        SessionService,
        SedeContextService,
        RecepcionService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), PERSONA_ADMIN);
    TestBed.inject(SedeContextService).selectedId.set(1);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EstadiasListComponent);
  });

  afterEach(() => http.verify());

  async function cargar(data = [recepcion()], page = 0, size = 20, total = data.length): Promise<void> {
    fixture.detectChanges();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/recepcion/listar` &&
          r.params.get('idHotel') === '1' &&
          r.params.get('page') === String(page) &&
          r.params.get('size') === '20'
      )
      .flush(envelopePage(data, page, size, total));
    await microtasks();
    fixture.detectChanges();
  }

  it('pide page 0 y size 20 con idHotel y muestra la tabla con formatos es-PE', async () => {
    await cargar([recepcion({ precioInicial: 1234.5, precioRestante: 100 })]);
    const texto = (fixture.nativeElement.textContent as string).replace(/\u00a0/g, ' ');
    expect(texto).toContain('Carla Cliente');
    expect(texto).toContain('19/10/2026');
    expect(texto).toContain('21/10/2026');
    expect(texto).toContain('S/ 1,234.50');
    expect(texto).toContain('Activa');
    expect(texto).toContain('Página 1 de 1 · 1 registros');
  });

  it('avanza y retrocede la paginación desde meta', async () => {
    await cargar([recepcion()], 0, 20, 21);
    expect(fixture.nativeElement.textContent).toContain('Página 1 de 2 · 21 registros');

    boton(fixture, 'Siguiente').click();
    await microtasks();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/recepcion/listar` && r.params.get('page') === '1'
      )
      .flush(envelopePage([recepcion({ idRecepcion: 1002 })], 1, 20, 21));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Página 2 de 2 · 21 registros');

    boton(fixture, 'Anterior').click();
    await microtasks();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/recepcion/listar` && r.params.get('page') === '0'
      )
      .flush(envelopePage([recepcion()], 0, 20, 21));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Página 1 de 2 · 21 registros');
  });

  it('envía estadoEstadia como filtro y lo limpia', async () => {
    await cargar();
    const select = (fixture.nativeElement as HTMLElement).querySelector(
      '#filtro-estado'
    ) as HTMLSelectElement;
    select.value = 'ACTIVA';
    select.dispatchEvent(new Event('change'));
    await microtasks();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/recepcion/listar` &&
          r.params.get('estadoEstadia') === 'ACTIVA'
      )
      .flush(envelopePage([recepcion()], 0, 20, 1));
    await microtasks();

    boton(fixture, 'Limpiar filtro').click();
    await microtasks();
    const limpio = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/recepcion/listar`
    );
    expect(limpio.request.params.has('estadoEstadia')).toBe(false);
    limpio.flush(envelopePage([], 0, 20, 0));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No hay estadías en esta sede');
  });

  it('pinta «Estadía vencida sin cierre» solo en una ACTIVA vencida', async () => {
    const hoy = new Date();
    const iso = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(
      hoy.getDate()
    ).padStart(2, '0')}`;
    await cargar([
      recepcion({ idRecepcion: 1, estadoEstadia: 'ACTIVA', vencida: true, fechaSalida: '2026-10-18' }),
      recepcion({ idRecepcion: 2, estadoEstadia: 'ACTIVA', vencida: false, fechaSalida: iso }),
      recepcion({ idRecepcion: 3, estadoEstadia: 'CERRADA', vencida: true }),
    ]);
    const filas = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr')
    );
    expect(filas).toHaveLength(3);
    expect(filas[0].textContent).toContain('Estadía vencida sin cierre');
    expect(filas[1].textContent).not.toContain('Estadía vencida sin cierre');
    expect(filas[2].textContent).not.toContain('Estadía vencida sin cierre');
    expect(filas[0].classList.contains('fila-vencida')).toBe(true);
  });

  it('un 503 muestra el mensaje del catálogo con correlationId y reintenta', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/listar`)
      .flush(apiError('DEPENDENCY_UNAVAILABLE', {}, 'cid-503'), {
        status: 503,
        statusText: 'Service Unavailable',
      });
    await microtasks();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'El servicio no está disponible. Reintenta en unos segundos.'
    );
    expect(fixture.nativeElement.textContent).toContain('cid-503');
    boton(fixture, 'Reintentar').click();
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/listar`)
      .flush(envelopePage([recepcion()], 0, 20, 1));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Carla Cliente');
  });
});
