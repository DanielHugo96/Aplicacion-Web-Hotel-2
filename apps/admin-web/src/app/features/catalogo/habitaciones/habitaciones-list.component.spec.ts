import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { gatewayInterceptor } from '../../../core/gateway.interceptor';
import { SessionService } from '../../../core/services/session.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { ETagCacheService } from '../../../core/services/etag-cache.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  apiError,
  authData,
  envelopePage,
  envelopeSingle,
  habitacion,
  PERSONA_ADMIN,
  PERSONA_EMPLEADO,
} from '../../recepcion/testing/recepcion.fixtures';
import { HabitacionesListComponent } from './habitaciones-list.component';

const BLOQUEADA = {
  idEstadoHabitacion: 3,
  codigo: 'MANTENIMIENTO' as const,
  descripcion: 'Mantenimiento',
  estado: true,
  version: 1,
};

const LISTA = {
  idEstadoHabitacion: 1,
  codigo: 'LISTA' as const,
  descripcion: 'Lista',
  estado: true,
  version: 1,
};

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

describe('HabitacionesListComponent — operaciones de habitación', () => {
  let fixture: ComponentFixture<HabitacionesListComponent>;
  let componente: HabitacionesListComponent;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HabitacionesListComponent],
      providers: [
        SessionService,
        SedeContextService,
        CatalogoService,
        RecepcionService,
        ETagCacheService,
        ToastService,
        provideHttpClient(withInterceptors([gatewayInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    const session = TestBed.inject(SessionService);
    session.setSession(authData('Administrador'), PERSONA_ADMIN);
    TestBed.inject(SedeContextService).selectedId.set(1);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  async function crear(habitaciones = [habitacion()], rol: 'Administrador' | 'Empleado' = 'Administrador'): Promise<void> {
    if (rol === 'Empleado') {
      TestBed.inject(SessionService).setSession(
        { ...authData('Empleado'), sedes: [1] },
        PERSONA_EMPLEADO
      );
    }
    fixture = TestBed.createComponent(HabitacionesListComponent);
    componente = fixture.componentInstance;
    fixture.detectChanges();
    http
      .match((r) => r.url === `${API_BASE_URL}/api/habitacion/listar`)
      .forEach((req) => req.flush(envelopePage(habitaciones, 0, 20, habitaciones.length)));
    http
      .match((r) => r.url === `${API_BASE_URL}/api/categoria/listar`)
      .forEach((req) => req.flush(envelopePage([], 0, 100, 0)));
    await microtasks();
    fixture.detectChanges();
  }

  function botones(): HTMLButtonElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody button')
    ) as HTMLButtonElement[];
  }

  it('ADMIN ve Iniciar mantenimiento y Dar de baja en una habitación libre', async () => {
    await crear();
    const textos = botones().map((b) => b.textContent ?? '');
    expect(textos.some((t) => t.includes('Iniciar mantenimiento'))).toBe(true);
    expect(textos.some((t) => t.includes('Dar de baja'))).toBe(true);
  });

  it('EMPLEADO no ve acciones de mantenimiento, baja ni reactivación', async () => {
    await crear([habitacion()], 'Empleado');
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Solo lectura');
    expect(texto).not.toContain('Iniciar mantenimiento');
    expect(texto).not.toContain('Dar de baja');
    expect(texto).not.toContain('Reactivar');
  });

  it('200 de mantenimiento refresca la fila y confirma con un toast', async () => {
    const h = habitacion({ version: 3 });
    await crear([h]);
    componente.abrirMantenimiento(h);
    fixture.detectChanges();
    componente.motivo.set('Fuga de agua');
    const promesa = componente.confirmarMantenimiento();
    const req = http.expectOne(`${API_BASE_URL}/api/habitacion/101/mantenimiento`);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('If-Match')).toBe('"3"');
    expect(req.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(req.request.body).toEqual({ motivo: 'Fuga de agua' });
    req.flush(
      envelopeSingle(
        habitacion({
          version: 4,
          idEstadoHabitacion: 3,
          estadoFisico: BLOQUEADA,
          blockId: '33333333-3333-4333-8333-333333333333',
          endpoint: '/api/habitacion/operaciones/33333333-3333-4333-8333-333333333333',
        })
      )
    );
    await promesa;
    fixture.detectChanges();

    expect(TestBed.inject(ToastService).message()).toBe('Mantenimiento iniciado');
    expect(componente.dialogo()).toBeNull();
    expect(componente.habitaciones()[0].blockId).toBe('33333333-3333-4333-8333-333333333333');
    expect(fixture.nativeElement.textContent).toContain('Completar mantenimiento');
  });

  it('202 de baja muestra el banner, sondea hasta APLICADA y refresca la vista', async () => {
    const h = habitacion({ version: 2 });
    await crear([h]);
    vi.useFakeTimers();
    componente.abrirBaja(h);
    const promesa = componente.confirmarBaja();
    const req = http.expectOne(`${API_BASE_URL}/api/habitacion/eliminar/101`);
    expect(req.request.method).toBe('DELETE');
    expect(req.request.headers.get('If-Match')).toBe('"2"');
    req.flush(
      envelopeSingle({
        operationId: null,
        closureId: null,
        blockId: '33333333-3333-4333-8333-333333333333',
        estadoOperacion: 'PREPARANDO',
      }),
      {
        status: 202,
        statusText: 'Accepted',
        headers: {
          Location: '/api/habitacion/operaciones/33333333-3333-4333-8333-333333333333',
          'Retry-After': '2',
        },
      }
    );
    await promesa;
    fixture.detectChanges();

    const banner = (fixture.nativeElement as HTMLElement).querySelector('.banner-pendiente');
    expect(banner?.textContent).toContain('Operación en proceso. No repitas la acción.');
    expect(componente.pendiente()?.tipo).toBe('BAJA');

    await vi.advanceTimersByTimeAsync(2000);
    http
      .expectOne(`${API_BASE_URL}/api/habitacion/operaciones/33333333-3333-4333-8333-333333333333`)
      .flush(
        envelopeSingle({
          blockId: '33333333-3333-4333-8333-333333333333',
          idHabitacion: 101,
          tipo: 'BAJA',
          estado: 'PREPARANDO',
        })
      );
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();
    expect(componente.pendiente()?.actualizadoEn).toBeInstanceOf(Date);

    await vi.advanceTimersByTimeAsync(2000);
    http
      .expectOne(`${API_BASE_URL}/api/habitacion/operaciones/33333333-3333-4333-8333-333333333333`)
      .flush(
        envelopeSingle({
          blockId: '33333333-3333-4333-8333-333333333333',
          idHabitacion: 101,
          tipo: 'BAJA',
          estado: 'APLICADA',
        })
      );
    await vi.advanceTimersByTimeAsync(0);
    http
      .match((r) => r.url === `${API_BASE_URL}/api/habitacion/listar`)
      .forEach((r) => r.flush(envelopePage([habitacion({ estado: false, blockId: 'b' })], 0, 20, 1)));
    await vi.advanceTimersByTimeAsync(0);
    fixture.detectChanges();

    expect(componente.pendiente()).toBeNull();
    expect(TestBed.inject(ToastService).message()).toBe('Baja aplicada');
    expect(fixture.nativeElement.textContent).not.toContain('Operación en proceso. No repitas la acción.');
  });

  it('409 GATE_NOT_AVAILABLE muestra el mensaje y no reintenta la intención', async () => {
    const h = habitacion({ version: 2 });
    await crear([h]);
    componente.abrirBaja(h);
    const promesa = componente.confirmarBaja();
    http
      .expectOne(`${API_BASE_URL}/api/habitacion/eliminar/101`)
      .flush(apiError('GATE_NOT_AVAILABLE', { idHabitacion: '101' }, 'cid-409'), {
        status: 409,
        statusText: 'Conflict',
      });
    await promesa;
    fixture.detectChanges();

    expect(componente.operacionError()?.code).toBe('GATE_NOT_AVAILABLE');
    expect(fixture.nativeElement.textContent).toContain(
      'No se puede bloquear la habitación: está ocupada o en limpieza.'
    );
    expect(componente.operationActionLabel()).toBe('Recargar');
    http.expectNone(`${API_BASE_URL}/api/habitacion/eliminar/101`);
  });

  it('412 recarga el listado y muestra el mensaje del catálogo', async () => {
    const h = habitacion({ version: 2 });
    await crear([h]);
    componente.abrirMantenimiento(h);
    componente.motivo.set('Pintura');
    const promesa = componente.confirmarMantenimiento();
    http
      .expectOne(`${API_BASE_URL}/api/habitacion/101/mantenimiento`)
      .flush(apiError('PRECONDITION_FAILED', {}, 'cid-412'), {
        status: 412,
        statusText: 'Precondition Failed',
      });
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/habitacion/listar`)
      .flush(envelopePage([habitacion({ version: 3 })], 0, 20, 1));
    await promesa;
    await microtasks();
    fixture.detectChanges();

    expect(componente.operacionError()?.code).toBe('PRECONDITION_FAILED');
    expect(fixture.nativeElement.textContent).toContain(
      'Otra persona modificó este registro. Recarga para ver los cambios.'
    );
  });

  it('completa mantenimiento con el blockId vigente de la fila', async () => {
    const h = habitacion({
      version: 5,
      idEstadoHabitacion: 3,
      estadoFisico: BLOQUEADA,
      blockId: '33333333-3333-4333-8333-333333333333',
    });
    await crear([h]);
    expect(fixture.nativeElement.textContent).toContain('Completar mantenimiento');
    const promesa = componente.completarMantenimiento(h);
    const req = http.expectOne(`${API_BASE_URL}/api/habitacion/101/mantenimiento-completado`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.headers.get('If-Match')).toBe('"5"');
    expect(req.request.body).toEqual({ blockId: '33333333-3333-4333-8333-333333333333' });
    req.flush(
      envelopeSingle(
        habitacion({ version: 6, idEstadoHabitacion: 1, estadoFisico: LISTA, blockId: null })
      )
    );
    await promesa;
    fixture.detectChanges();

    expect(TestBed.inject(ToastService).message()).toBe('Mantenimiento completado');
    expect(componente.habitaciones()[0].estadoFisico.codigo).toBe('LISTA');
    expect(fixture.nativeElement.textContent).not.toContain('Completar mantenimiento');
  });

  it('reactiva una habitación dada de baja con su blockId', async () => {
    const h = habitacion({
      version: 7,
      estado: false,
      idEstadoHabitacion: 3,
      estadoFisico: BLOQUEADA,
      blockId: '33333333-3333-4333-8333-333333333333',
    });
    await crear([h]);
    expect(fixture.nativeElement.textContent).toContain('Reactivar');
    const promesa = componente.reactivar(h);
    const req = http.expectOne(`${API_BASE_URL}/api/habitacion/101/reactivar`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.headers.get('If-Match')).toBe('"7"');
    expect(req.request.body).toEqual({ blockId: '33333333-3333-4333-8333-333333333333' });
    req.flush(
      envelopeSingle(habitacion({ version: 8, estado: true, idEstadoHabitacion: 1, estadoFisico: LISTA, blockId: null }))
    );
    await promesa;
    fixture.detectChanges();

    expect(TestBed.inject(ToastService).message()).toBe('Habitación reactivada');
    expect(componente.habitaciones()[0].estado).toBe(true);
  });
});
