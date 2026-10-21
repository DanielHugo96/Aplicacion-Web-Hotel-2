import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { gatewayInterceptor } from '../../../core/gateway.interceptor';
import { Cierre, Recepcion } from '../../../core/models/recepcion.models';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  apiError,
  authData,
  envelopeSingle,
  PERSONA_ADMIN,
  PERSONA_EMPLEADO,
  personaAdmin,
  personaCliente,
  recepcion,
} from '../testing/recepcion.fixtures';
import { CierrePanelComponent } from './cierre-panel.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

const CIERRE_COMPLETADO: Cierre = {
  closureId: '20000000-0000-4000-8000-000000000002',
  estado: 'COMPLETADO',
  lastErrorCode: null,
  calculo: {
    alojamientoTotal: 140,
    saldoAlojamiento: 120,
    consumosPagadosAntes: 0,
    consumosPendientes: 7,
    consumosTotal: 7,
    cobroAhora: 127,
    totalGeneral: 147,
    expectedPendingTotal: 7,
  },
  medioPago: 'EFECTIVO',
};

describe('CierrePanelComponent', () => {
  let fixture: ComponentFixture<CierrePanelComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CierrePanelComponent],
      providers: [
        SessionService,
        RecepcionService,
        ToastService,
        provideHttpClient(withInterceptors([gatewayInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    TestBed.resetTestingModule();
  });

  function crear(data: Recepcion, rol = 'Administrador'): void {
    TestBed.inject(SessionService).setSession(
      authData(rol),
      rol === 'Empleado' ? PERSONA_EMPLEADO : rol === 'Cliente' ? personaCliente : PERSONA_ADMIN
    );
    fixture = TestBed.createComponent(CierrePanelComponent);
    fixture.componentRef.setInput('recepcion', data);
    fixture.componentRef.setInput('etag', '"3"');
    fixture.detectChanges();
  }

  function texto(): string {
    return (fixture.nativeElement.textContent as string).replace(/\u00a0/g, ' ');
  }

  function botones(): HTMLButtonElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button')
    ) as HTMLButtonElement[];
  }

  it('pinta closureId, estado del cierre y el cálculo congelado', () => {
    crear(recepcion({ estadoEstadia: 'CERRADA', estado: false, cierre: CIERRE_COMPLETADO }));
    expect(texto()).toContain('Completado');
    expect(texto()).toContain('20000000-0000-4000-8000-000000000002');
    expect(texto()).toContain('S/ 127.00');
    expect(texto()).toContain('S/ 147.00');
    expect(texto()).toContain('EFECTIVO');
    expect(texto()).not.toContain('Cotizar y cerrar');
  });

  it('con estadía ACTIVA ofrece cotizar y cerrar; CERRANDO, ver el cierre en curso', () => {
    crear(recepcion());
    expect(texto()).toContain('Cotizar y cerrar');

    fixture.componentRef.setInput('recepcion', recepcion({ estadoEstadia: 'CERRANDO', estado: true }));
    fixture.detectChanges();
    expect(texto()).toContain('Ver cierre en curso');
  });

  it('muestra la penalidad destacada con su motivo', () => {
    crear(recepcion({ costoPenalidad: 25, motivoPenalidad: 'Salida tardía' }));
    expect(texto()).toContain('Penalidad');
    expect(texto()).toContain('S/ 25.00');
    expect(texto()).toContain('Salida tardía');
  });

  it('un 404 del detalle ya se resuelve en la pantalla padre; aquí no hay lectura propia', () => {
    crear(recepcion());
    http.expectNone(`${API_BASE_URL}/api/recepcion/1001`);
  });

  it('el diálogo de anulación describe la acción y deja el motivo listo para teclado', () => {
    crear(recepcion());
    fixture.componentInstance.abrirAnulacion();
    fixture.detectChanges();
    const dialogo = (fixture.nativeElement as HTMLElement).querySelector('[role="dialog"]');
    expect(dialogo?.getAttribute('aria-modal')).toBe('true');
    expect(dialogo?.getAttribute('aria-labelledby')).toBe('anular-titulo');
    expect(dialogo?.getAttribute('aria-describedby')).toBe('anular-descripcion');
    const motivo = dialogo?.querySelector('textarea#motivo-anulacion') as HTMLTextAreaElement;
    expect(motivo).toBeTruthy();
    expect(motivo.getAttribute('maxlength')).toBe('300');
    expect(motivo.hasAttribute('autofocus')).toBe(true);
  });

  it('un 200 de anular usa If-Match del detalle y avisa de recarga', async () => {
    crear(recepcion());
    fixture.componentInstance.abrirAnulacion();
    fixture.detectChanges();
    fixture.componentInstance.motivoAnulacion.set('Check-in equivocado');
    const emitir = vi.fn();
    fixture.componentInstance.recargar.subscribe(emitir);

    const promesa = fixture.componentInstance.confirmarAnulacion();
    const req = http.expectOne(`${API_BASE_URL}/api/recepcion/1001/anular-checkin`);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('If-Match')).toBe('"3"');
    expect(req.request.body).toEqual({ motivo: 'Check-in equivocado' });
    req.flush(
      envelopeSingle(
        recepcion({ estadoEstadia: 'CERRADA', estado: false, archivada: true, version: 4 })
      ),
      { headers: { ETag: '"4"' } }
    );
    await promesa;
    await microtasks();
    fixture.detectChanges();

    expect(TestBed.inject(ToastService).message()).toBe('Check-in anulado');
    expect(emitir).toHaveBeenCalled();
    expect(fixture.componentInstance.anularAbierto()).toBe(false);
  });

  it('el 409 de anular muestra el catálogo y no cambia nada', async () => {
    crear(recepcion());
    fixture.componentInstance.abrirAnulacion();
    fixture.componentInstance.motivoAnulacion.set('Duplicado');
    const promesa = fixture.componentInstance.confirmarAnulacion();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/1001/anular-checkin`)
      .flush(apiError('OPERATIONS_IN_PROGRESS', {}, 'cid-ops'), {
        status: 409,
        statusText: 'Conflict',
      });
    await promesa;
    fixture.detectChanges();
    expect(texto()).toContain('Hay consumos en proceso. Espera a que terminen.');
    expect(fixture.componentInstance.recepcion.estadoEstadia).toBe('ACTIVA');
  });

  it('el 412 de anular emite recarga y conserva el mensaje del catálogo', async () => {
    crear(recepcion());
    fixture.componentInstance.abrirAnulacion();
    fixture.componentInstance.motivoAnulacion.set('Duplicado');
    const emitir = vi.fn();
    fixture.componentInstance.recargar.subscribe(emitir);
    const promesa = fixture.componentInstance.confirmarAnulacion();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/1001/anular-checkin`)
      .flush(apiError('PRECONDITION_FAILED', {}, 'cid-412'), {
        status: 412,
        statusText: 'Precondition Failed',
      });
    await promesa;
    fixture.detectChanges();
    expect(emitir).toHaveBeenCalled();
    expect(texto()).toContain('Otra persona modificó este registro. Recarga para ver los cambios.');
  });

  it('un motivo vacío no envía la anulación', async () => {
    crear(recepcion());
    fixture.componentInstance.abrirAnulacion();
    fixture.detectChanges();
    fixture.componentInstance.motivoAnulacion.set('  ');
    await fixture.componentInstance.confirmarAnulacion();
    fixture.detectChanges();
    http.expectNone(`${API_BASE_URL}/api/recepcion/1001/anular-checkin`);
    expect(texto()).toContain('Indica el motivo de la anulación');
  });

  it('un motivo de más de 300 caracteres no envía la anulación', async () => {
    crear(recepcion());
    fixture.componentInstance.abrirAnulacion();
    fixture.detectChanges();
    fixture.componentInstance.motivoAnulacion.set('x'.repeat(301));
    await fixture.componentInstance.confirmarAnulacion();
    fixture.detectChanges();
    http.expectNone(`${API_BASE_URL}/api/recepcion/1001/anular-checkin`);
    expect(texto()).toContain('Máximo 300 caracteres');
  });

  it('usa una píldora por estado del cierre con sus clases', () => {
    crear(recepcion({ estadoEstadia: 'CERRANDO', cierre: { ...CIERRE_COMPLETADO, estado: 'CONFIRMANDO', calculo: null } }));
    const pill = (fixture.nativeElement as HTMLElement).querySelector('.pill');
    expect(pill?.textContent).toContain('Confirmando');
    expect(pill?.className).toContain('pill-proceso');

    fixture.componentRef.setInput(
      'recepcion',
      recepcion({ estadoEstadia: 'CERRADA', estado: false, cierre: { ...CIERRE_COMPLETADO, estado: 'RECHAZADO', calculo: null } })
    );
    fixture.detectChanges();
    const rechazado = (fixture.nativeElement as HTMLElement).querySelector('.pill');
    expect(rechazado?.textContent).toContain('Rechazado');
    expect(rechazado?.className).toContain('pill-error');
  });

  it('sin intento de cierre muestra el vacío del bloque', () => {
    crear(recepcion());
    expect(texto()).toContain('Todavía no hay un intento de cierre para esta estadía.');
  });

  it('el 200 de archivar solo aparece con CERRADA y rol A', async () => {
    crear(recepcion({ estadoEstadia: 'CERRADA', estado: false, cierre: CIERRE_COMPLETADO }));
    expect(botones().some((b) => (b.textContent ?? '').includes('Archivar'))).toBe(true);
    fixture.componentInstance.abrirArchivar();
    const emitir = vi.fn();
    fixture.componentInstance.recargar.subscribe(emitir);
    const promesa = fixture.componentInstance.confirmarArchivar();
    const req = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/recepcion/eliminar/1001` && r.method === 'DELETE'
    );
    expect(req.request.headers.get('If-Match')).toBe('"3"');
    req.flush(envelopeSingle(recepcion({ estadoEstadia: 'CERRADA', estado: false, archivada: true, version: 5 })));
    await promesa;
    await microtasks();
    fixture.detectChanges();
    expect(TestBed.inject(ToastService).message()).toBe('Estadía archivada');
    expect(emitir).toHaveBeenCalled();
  });

  it('un 409 de archivar no marca archivada y recarga el estado', async () => {
    crear(recepcion({ estadoEstadia: 'CERRADA', estado: false }));
    fixture.componentInstance.abrirArchivar();
    const emitir = vi.fn();
    fixture.componentInstance.recargar.subscribe(emitir);
    const promesa = fixture.componentInstance.confirmarArchivar();
    http
      .expectOne(
        (r) => r.url === `${API_BASE_URL}/api/recepcion/eliminar/1001` && r.method === 'DELETE'
      )
      .flush(apiError('STAY_NOT_ACTIVE', {}, 'cid-409'), { status: 409, statusText: 'Conflict' });
    await promesa;
    fixture.detectChanges();
    expect(texto()).toContain('La estadía no está activa para esa operación.');
    expect(fixture.componentInstance.recepcion.archivada).toBe(false);
    expect(emitir).toHaveBeenCalled();
  });

  it('el 412 de archivar recarga sin sobrescribir', async () => {
    crear(recepcion({ estadoEstadia: 'CERRADA', estado: false }));
    fixture.componentInstance.abrirArchivar();
    const emitir = vi.fn();
    fixture.componentInstance.recargar.subscribe(emitir);
    const promesa = fixture.componentInstance.confirmarArchivar();
    http
      .expectOne(
        (r) => r.url === `${API_BASE_URL}/api/recepcion/eliminar/1001` && r.method === 'DELETE'
      )
      .flush(apiError('PRECONDITION_FAILED', {}, 'cid-412'), {
        status: 412,
        statusText: 'Precondition Failed',
      });
    await promesa;
    fixture.detectChanges();
    expect(emitir).toHaveBeenCalled();
    expect(fixture.componentInstance.recepcion.archivada).toBe(false);
  });

  it('E cotiza y cierra en su sede, pero no ve anular ni archivar; C tampoco', () => {
    crear(recepcion(), 'Empleado');
    expect(texto()).toContain('Cotizar y cerrar');
    expect(botones().some((b) => (b.textContent ?? '').includes('Anular check-in'))).toBe(false);
    expect(botones().some((b) => (b.textContent ?? '').includes('Archivar'))).toBe(false);

    // El getter de rol se reevalúa en cada detección con la sesión vigente.
    TestBed.inject(SessionService).setSession(authData('Cliente'), personaCliente);
    fixture.componentInstance.recepcion = recepcion({ estadoEstadia: 'CERRADA', estado: false });
    fixture.detectChanges();
    expect(
      botones().some(
        (b) =>
          (b.textContent ?? '').includes('Anular check-in') ||
          (b.textContent ?? '').includes('Archivar')
      )
    ).toBe(false);
  });

  it('no ofrece archivar ni anular una estadía ya archivada', () => {
    crear(
      recepcion({
        estadoEstadia: 'CERRADA',
        estado: false,
        archivada: true,
        cierre: CIERRE_COMPLETADO,
      })
    );
    expect(botones().some((b) => (b.textContent ?? '').includes('Archivar'))).toBe(false);
  });
});
