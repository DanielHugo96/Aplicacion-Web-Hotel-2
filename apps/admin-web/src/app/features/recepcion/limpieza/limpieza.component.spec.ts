import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { gatewayInterceptor } from '../../../core/gateway.interceptor';
import { SessionService } from '../../../core/services/session.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { ETagCacheService } from '../../../core/services/etag-cache.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  apiError,
  authData,
  envelopePage,
  envelopeSingle,
  habitacion,
  limpieza,
  PERSONA_ADMIN,
} from '../testing/recepcion.fixtures';
import { LimpiezaComponent } from './limpieza.component';

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

describe('LimpiezaComponent', () => {
  let fixture: ComponentFixture<LimpiezaComponent>;
  let componente: LimpiezaComponent;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LimpiezaComponent],
      providers: [
        SessionService,
        SedeContextService,
        RecepcionService,
        ETagCacheService,
        ToastService,
        provideHttpClient(withInterceptors([gatewayInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), PERSONA_ADMIN);
    TestBed.inject(SedeContextService).selectedId.set(1);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function crear(tareas = [limpieza()], total = tareas.length): Promise<void> {
    fixture = TestBed.createComponent(LimpiezaComponent);
    componente = fixture.componentInstance;
    fixture.detectChanges();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/habitacion/limpiezas` &&
          r.params.get('idHotel') === '1' &&
          r.params.get('estado') === 'PENDIENTE' &&
          r.params.get('page') === '0' &&
          r.params.get('size') === '20'
      )
      .flush(envelopePage(tareas, 0, 20, total));
    await microtasks();
    fixture.detectChanges();
  }

  it('lista las tareas pendientes de la sede con su píldora', async () => {
    await crear();
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Habitación 101');
    expect(texto).toContain('Pendiente');
    expect(texto).toContain('Completar limpieza');
  });

  it('muestra el vacío exacto «No hay tareas pendientes»', async () => {
    await crear([], 0);
    expect(fixture.nativeElement.textContent).toContain('No hay tareas pendientes');
  });

  it('completar usa cleaningCycleId y la versión de la tarea como If-Match, y saca la tarjeta', async () => {
    const tarea = limpieza({ version: 4 });
    await crear([tarea]);
    boton(fixture, 'Completar limpieza').click();
    await microtasks();
    const req = http.expectOne(`${API_BASE_URL}/api/habitacion/101/limpieza-completada`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.headers.get('If-Match')).toBe('"4"');
    expect(req.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(req.request.body).toEqual({ cleaningCycleId: tarea.cleaningCycleId });
    req.flush(envelopeSingle({ limpieza: { ...tarea, estado: 'COMPLETADA' }, habitacion: habitacion() }));
    await microtasks();
    fixture.detectChanges();

    expect(TestBed.inject(ToastService).message()).toBe('Limpieza completada');
    expect(fixture.nativeElement.textContent).not.toContain('Completar limpieza');
    expect(componente.tareas()).toHaveLength(0);
  });

  it('con el filtro «Todas» la tarjeta queda COMPLETADA sin botón', async () => {
    await crear([limpieza({ version: 2 })]);
    componente.estado.set(null);
    componente.aplicarFiltro();
    await microtasks();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/habitacion/limpiezas` &&
          !r.params.has('estado')
      )
      .flush(envelopePage([limpieza({ version: 2 })], 0, 20, 1));
    await microtasks();
    fixture.detectChanges();

    const tarea = componente.tareas()[0];
    componente.completar(tarea);
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/habitacion/101/limpieza-completada`)
      .flush(
        envelopeSingle({
          limpieza: { ...tarea, estado: 'COMPLETADA', version: 3 },
          habitacion: habitacion(),
        })
      );
    await microtasks();
    fixture.detectChanges();

    expect(componente.tareas()[0].estado).toBe('COMPLETADA');
    expect(fixture.nativeElement.textContent).toContain('Completada');
    expect(fixture.nativeElement.textContent).not.toContain('Completar limpieza');
  });

  it('un 412 recarga el listado con el mensaje del catálogo y sin reabrir la tarea', async () => {
    await crear([limpieza({ version: 1 })]);
    boton(fixture, 'Completar limpieza').click();
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/habitacion/101/limpieza-completada`)
      .flush(apiError('PRECONDITION_FAILED', {}, 'cid-412'), {
        status: 412,
        statusText: 'Precondition Failed',
      });
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/habitacion/limpiezas`)
      .flush(envelopePage([limpieza({ version: 2 })], 0, 20, 1));
    await microtasks();
    fixture.detectChanges();

    expect(componente.error()?.code).toBe('PRECONDITION_FAILED');
    expect(fixture.nativeElement.textContent).toContain(
      'Otra persona modificó este registro. Recarga para ver los cambios.'
    );
    expect(componente.completando()).toBeNull();
  });

  it('el filtro por estado viaja como query y recarga desde page 0', async () => {
    await crear();
    const select = (fixture.nativeElement as HTMLElement).querySelector(
      '#filtro-limpieza'
    ) as HTMLSelectElement;
    select.value = 'COMPLETADA';
    select.dispatchEvent(new Event('change'));
    await microtasks();

    const req = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/habitacion/limpiezas` &&
        r.params.get('estado') === 'COMPLETADA' &&
        r.params.get('page') === '0'
    );
    req.flush(envelopePage([limpieza({ estado: 'COMPLETADA' })], 0, 20, 1));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Completada');
  });

  it('un 503 muestra el mensaje del catálogo y reintenta', async () => {
    fixture = TestBed.createComponent(LimpiezaComponent);
    componente = fixture.componentInstance;
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/habitacion/limpiezas`)
      .flush(apiError('DEPENDENCY_UNAVAILABLE', {}, 'cid-503'), {
        status: 503,
        statusText: 'Service Unavailable',
      });
    await microtasks();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'El servicio no está disponible. Reintenta en unos segundos.'
    );
    boton(fixture, 'Reintentar').click();
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/habitacion/limpiezas`)
      .flush(envelopePage([limpieza()], 0, 20, 1));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Completar limpieza');
  });
});
