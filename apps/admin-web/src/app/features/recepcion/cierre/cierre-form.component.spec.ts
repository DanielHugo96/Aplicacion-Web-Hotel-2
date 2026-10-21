import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { gatewayInterceptor } from '../../../core/gateway.interceptor';
import { SessionService } from '../../../core/services/session.service';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { ToastService } from '../../../core/services/toast.service';
import { apiError, authData, envelopeSingle, PERSONA_ADMIN, recepcion } from '../testing/recepcion.fixtures';
import { CierreFormComponent } from './cierre-form.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

function esperar(ms: number): Promise<void> {
  return new Promise((r) => setTimeout(r, ms));
}

/** Mismo formato que el pipe: la expectativa no escribe la hora a mano. */
function timestampEsperado(iso: string): string {
  return new Intl.DateTimeFormat('es-PE', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
    .format(new Date(iso))
    .replace(/, /, ' ');
}

const COTIZACION = {
  idRecepcion: 1001,
  alojamiento: 140,
  adelanto: 20,
  penalidad: 0,
  saldoAlojamiento: 120,
  consumosPagados: 0,
  consumosPendientes: 7,
  cobroAhora: 127,
  moneda: 'PEN' as const,
  calculadoEn: '2026-10-21T03:00:00Z',
};

describe('CierreFormComponent', () => {
  let fixture: ComponentFixture<CierreFormComponent>;
  let http: HttpTestingController;
  let componente: CierreFormComponent;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CierreFormComponent],
      providers: [
        SessionService,
        RecepcionService,
        ToastService,
        provideHttpClient(withInterceptors([gatewayInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '1001' }) } },
        },
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), PERSONA_ADMIN);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(CierreFormComponent);
    componente = fixture.componentInstance;
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  async function abrir(data = recepcion(), cotizacion = COTIZACION): Promise<void> {
    fixture.detectChanges();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/1001`)
      .flush(envelopeSingle(data), { headers: { ETag: '"3"' } });
    await microtasks();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/recepcion/1001/cotizacion-salida` &&
          r.params.get('costoPenalidad') === '0'
      )
      .flush(envelopeSingle(cotizacion));
    await microtasks();
    fixture.detectChanges();
  }

  function texto(): string {
    return (fixture.nativeElement.textContent as string).replace(/\u00a0/g, ' ');
  }

  it('pinta el desglose de la cotización sin recalcular ningún importe', async () => {
    await abrir(recepcion(), { ...COTIZACION, cobroAhora: 1234.5 });
    expect(texto()).toContain('S/ 1,234.50');
    expect(texto()).toContain('S/ 140.00');
    expect(texto()).toContain('S/ 120.00');
    expect(texto()).toContain('S/ 7.00');
    expect(texto()).toContain(timestampEsperado(COTIZACION.calculadoEn));
    expect(componente.cotizacion()?.cobroAhora).toBe(1234.5);
  });

  it('cambiar la penalidad recotiza por query y conserva el resto del detalle', async () => {
    await abrir();
    const promesa = componente.cambiarPenalidad('20');
    const req = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/recepcion/1001/cotizacion-salida` &&
        r.params.get('costoPenalidad') === '20'
    );
    req.flush(envelopeSingle({ ...COTIZACION, penalidad: 20, cobroAhora: 147 }));
    await promesa;
    await microtasks();
    fixture.detectChanges();

    expect(componente.cotizacion()?.penalidad).toBe(20);
    expect(componente.estadia()?.idRecepcion).toBe(1001);
    expect(texto()).toContain('S/ 147.00');
    expect(texto()).toContain('Carla Cliente');
  });

  it('valida la penalidad contra el alojamiento con el mensaje exacto', async () => {
    await abrir();
    componente.costoPenalidad.set(141);
    componente.montoRecibido.set(0);
    componente.medioPago.set('EFECTIVO');
    await componente.cerrar();
    expect(componente.errores().costoPenalidad).toBe(
      'La penalidad no puede superar el alojamiento'
    );
    http.expectNone(`${API_BASE_URL}/api/recepcion/registrar-salida`);
  });

  it('bloquea el envío con penalidad sin motivo', async () => {
    await abrir();
    componente.costoPenalidad.set(10);
    componente.motivoPenalidad.set('   ');
    componente.montoRecibido.set(147);
    componente.medioPago.set('EFECTIVO');
    await componente.cerrar();
    expect(componente.errores().motivoPenalidad).toBe('Indica el motivo de la penalidad');
    http.expectNone(`${API_BASE_URL}/api/recepcion/registrar-salida`);
  });

  it('exige monto y medio de pago con sus mensajes', async () => {
    await abrir();
    componente.montoRecibido.set(null);
    componente.medioPago.set('');
    await componente.cerrar();
    expect(componente.errores().montoRecibido).toBe('Ingresa el monto recibido');
    expect(componente.errores().medioPago).toBe('Selecciona el medio de pago');
    http.expectNone(`${API_BASE_URL}/api/recepcion/registrar-salida`);
  });

  it('el 200 envía el cuerpo del contrato, refresca CERRADA y navega al detalle', async () => {
    await abrir();
    componente.costoPenalidad.set(10);
    componente.motivoPenalidad.set('Salida tardía');
    componente.montoRecibido.set(137);
    componente.medioPago.set('TARJETA');

    const promesa = componente.cerrar();
    const req = http.expectOne(`${API_BASE_URL}/api/recepcion/registrar-salida`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      idRecepcion: 1001,
      idHabitacion: 101,
      costoPenalidad: 10,
      motivoPenalidad: 'Salida tardía',
      montoRecibido: 137,
      medioPago: 'TARJETA',
    });
    req.flush(
      envelopeSingle(
        recepcion({ estadoEstadia: 'CERRADA', estado: false, version: 5, fechaSalidaConfirmacion: '2026-10-21T03:10:00Z' })
      ),
      { headers: { ETag: '"5"' } }
    );
    await promesa;
    fixture.detectChanges();

    expect(componente.estadia()?.estadoEstadia).toBe('CERRADA');
    expect(componente.etag()).toBe('"5"');
    expect(TestBed.inject(ToastService).message()).toBe('Estadía cerrada');
    expect(router.navigate).toHaveBeenCalledWith(['/app/recepcion', 1001]);
  });

  it('el doble clic no reenvía la intención', async () => {
    await abrir();
    componente.montoRecibido.set(127);
    componente.medioPago.set('EFECTIVO');
    const primera = componente.cerrar();
    const segunda = componente.cerrar();
    const req = http.expectOne(`${API_BASE_URL}/api/recepcion/registrar-salida`);
    req.flush(envelopeSingle(recepcion({ estadoEstadia: 'CERRADA', estado: false, version: 4 })));
    await Promise.all([primera, segunda]);
    http.expectNone(`${API_BASE_URL}/api/recepcion/registrar-salida`);
  });

  it('el 202 abre el banner, bloquea el cobro y sondea hasta CERRADA', async () => {
    await abrir();
    componente.montoRecibido.set(127);
    componente.medioPago.set('EFECTIVO');

    const promesa = componente.cerrar();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/registrar-salida`)
      .flush(envelopeSingle(recepcion({ estadoEstadia: 'CERRANDO', estado: true, version: 4 })), {
        status: 202,
        statusText: 'Accepted',
        headers: { Location: '/api/recepcion/1001', 'Retry-After': '0.001' },
      });
    await microtasks();
    fixture.detectChanges();

    expect(texto()).toContain('Operación en proceso. No repitas la acción.');
    expect(texto()).toContain('cierre pendiente, no volver a cobrar');
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[aria-live="polite"]')
    ).toBeTruthy();
    expect(TestBed.inject(ToastService).message()).toBeNull();
    expect(componente.pendiente()).toBe(true);

    await esperar(20);
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/1001`)
      .flush(envelopeSingle(recepcion({ estadoEstadia: 'CERRANDO', estado: true, version: 4 })), {
        headers: { 'Retry-After': '0.001' },
      });
    await microtasks();
    fixture.detectChanges();
    expect(componente.actualizadoEn()).not.toBeNull();
    expect(texto()).toContain('Actualizado');

    await esperar(20);
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/1001`)
      .flush(
        envelopeSingle(recepcion({ estadoEstadia: 'CERRADA', estado: false, version: 6 })),
        { headers: { ETag: '"6"' } }
      );
    await promesa;
    await microtasks();
    fixture.detectChanges();

    expect(componente.pendiente()).toBe(false);
    expect(componente.estadia()?.estadoEstadia).toBe('CERRADA');
    expect(componente.etag()).toBe('"6"');
    expect(TestBed.inject(ToastService).message()).toBe('Estadía cerrada');
    expect(router.navigate).toHaveBeenCalledWith(['/app/recepcion', 1001]);
  });

  it('el 409 AMOUNT_MISMATCH muestra la nueva cotización y confirma con intención nueva', async () => {
    await abrir();
    componente.montoRecibido.set(120);
    componente.medioPago.set('EFECTIVO');

    const primera = componente.cerrar();
    const req1 = http.expectOne(`${API_BASE_URL}/api/recepcion/registrar-salida`);
    const clave1 = req1.request.headers.get('Idempotency-Key');
    const nueva = { ...COTIZACION, penalidad: 0, cobroAhora: 127 };
    req1.flush(
      {
        success: false,
        message: 'El monto no coincide con el calculado. Revisa la nueva cotización.',
        data: { cotizacion: nueva, closureId: '20000000-0000-4000-8000-000000000002' },
        error: { code: 'AMOUNT_MISMATCH', fields: { montoRecibido: '120' } },
        meta: { correlationId: 'cid-409' },
      },
      { status: 409, statusText: 'Conflict' }
    );
    await primera;
    await microtasks();
    fixture.detectChanges();

    expect(texto()).toContain('El monto no coincide con el calculado. Revisa la nueva cotización.');
    expect(texto()).toContain('Nueva cotización del backend');
    expect(texto()).toContain('S/ 127.00');
    expect(componente.cotizacionRechazada()?.cobroAhora).toBe(127);
    expect(componente.closureIdRechazado()).toBe('20000000-0000-4000-8000-000000000002');

    const segunda = componente.confirmarConNuevaCotizacion();
    const req2 = http.expectOne(`${API_BASE_URL}/api/recepcion/registrar-salida`);
    expect(req2.request.headers.get('Idempotency-Key')).not.toBe(clave1);
    expect(req2.request.body).toMatchObject({ montoRecibido: 127 });
    req2.flush(envelopeSingle(recepcion({ estadoEstadia: 'CERRADA', estado: false, version: 7 })));
    await segunda;
    await microtasks();
    fixture.detectChanges();
    expect(TestBed.inject(ToastService).message()).toBe('Estadía cerrada');
  });

  it('un 409 STAY_NOT_ACTIVE muestra el mensaje del catálogo sin éxito y recarga el estado', async () => {
    await abrir();
    componente.montoRecibido.set(127);
    componente.medioPago.set('EFECTIVO');
    const promesa = componente.cerrar();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/registrar-salida`)
      .flush(apiError('STAY_NOT_ACTIVE', {}, 'cid-stay'), { status: 409, statusText: 'Conflict' });
    await promesa;
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/1001`)
      .flush(envelopeSingle(recepcion({ estadoEstadia: 'CERRADA', estado: false, version: 8 })), {
        headers: { ETag: '"8"' },
      });
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/1001/cotizacion-salida`)
      .flush(envelopeSingle(COTIZACION));
    await microtasks();
    fixture.detectChanges();
    expect(texto()).toContain('La estadía no está activa para esa operación.');
    expect(componente.error()?.code).toBe('STAY_NOT_ACTIVE');
    expect(componente.etag()).toBe('"8"');
    expect(TestBed.inject(ToastService).message()).toBeNull();
  });

  it('OPERATIONS_IN_PROGRESS y CLOSURE_IN_PROGRESS aplican su acción sin reintentar la intención', async () => {
    await abrir();
    componente.montoRecibido.set(127);
    componente.medioPago.set('EFECTIVO');
    const primera = componente.cerrar();
    const req1 = http.expectOne(`${API_BASE_URL}/api/recepcion/registrar-salida`);
    const clave1 = req1.request.headers.get('Idempotency-Key');
    req1.flush(apiError('OPERATIONS_IN_PROGRESS', {}, 'cid-ops'), {
      status: 409,
      statusText: 'Conflict',
    });
    await primera;
    fixture.detectChanges();
    expect(texto()).toContain('Hay consumos en proceso. Espera a que terminen.');
    expect(componente.error()?.action).toBe('Esperar el resultado y reintentar.');
    // No se ha reenviado sola: el operador debe esperar, no duplicar el cobro.
    http.expectNone(`${API_BASE_URL}/api/recepcion/registrar-salida`);

    const segunda = componente.cerrar();
    const req2 = http.expectOne(`${API_BASE_URL}/api/recepcion/registrar-salida`);
    // Tras un 409 de validación la intención es nueva (decisión del base).
    expect(req2.request.headers.get('Idempotency-Key')).not.toBe(clave1);
    req2.flush(apiError('CLOSURE_IN_PROGRESS', {}, 'cid-closure'), {
      status: 409,
      statusText: 'Conflict',
    });
    await segunda;
    await microtasks();
    // La acción esperada de CLOSURE_IN_PROGRESS es consultar la estadía:
    // recarga el detalle conservando el mensaje del catálogo.
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/1001`)
      .flush(envelopeSingle(recepcion({ estadoEstadia: 'CERRADA', estado: false, version: 5 })), {
        headers: { ETag: '"5"' },
      });
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/1001/cotizacion-salida`)
      .flush(envelopeSingle(COTIZACION));
    await microtasks();
    fixture.detectChanges();
    expect(texto()).toContain('Ya hay un cierre en curso; no vuelvas a cobrar.');
    expect(componente.error()?.code).toBe('CLOSURE_IN_PROGRESS');
    expect(TestBed.inject(ToastService).message()).toBeNull();
  });

  it('un 503 reintenta con la misma clave y el mismo cuerpo', async () => {
    await abrir();
    componente.montoRecibido.set(127);
    componente.medioPago.set('EFECTIVO');
    const primera = componente.cerrar();
    const req1 = http.expectOne(`${API_BASE_URL}/api/recepcion/registrar-salida`);
    const clave1 = req1.request.headers.get('Idempotency-Key');
    const cuerpo1 = req1.request.body;
    req1.flush(apiError('DEPENDENCY_UNAVAILABLE', {}, 'cid-503'), {
      status: 503,
      statusText: 'Service Unavailable',
    });
    await primera;
    fixture.detectChanges();
    expect(componente.actionLabel()).toBe('Reintentar');

    const segunda = componente.cerrar();
    const req2 = http.expectOne(`${API_BASE_URL}/api/recepcion/registrar-salida`);
    expect(req2.request.headers.get('Idempotency-Key')).toBe(clave1);
    expect(req2.request.body).toEqual(cuerpo1);
    req2.flush(envelopeSingle(recepcion({ estadoEstadia: 'CERRADA', estado: false, version: 6 })));
    await segunda;
    await microtasks();
    expect(TestBed.inject(ToastService).message()).toBe('Estadía cerrada');
  });

  it('no cobra sin cotización cargada y muestra el skeleton mientras espera', async () => {
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[aria-label="Cargando cierre"]')
    ).toBeTruthy();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/1001`)
      .flush(envelopeSingle(recepcion()), { headers: { ETag: '"3"' } });
    await microtasks();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/recepcion/1001/cotizacion-salida` &&
          r.params.get('costoPenalidad') === '0'
      )
      .flush(envelopeSingle(COTIZACION));
    await microtasks();
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('[aria-label="Cargando cierre"]')
    ).toBeNull();
  });

  it('un error de red muestra el mensaje del catálogo con correlationId y acción', async () => {
    fixture.detectChanges();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/1001`)
      .flush(apiError('DEPENDENCY_UNAVAILABLE', {}, 'cid-503'), {
        status: 503,
        statusText: 'Service Unavailable',
      });
    await microtasks();
    fixture.detectChanges();
    expect(texto()).toContain('El servicio no está disponible. Reintenta en unos segundos.');
    expect(texto()).toContain('cid-503');
    expect(componente.actionLabel()).toBe('Reintentar');
  });

  it('un 412 recarga el detalle con el ETag nuevo sin sobrescribir', async () => {
    await abrir();
    componente.montoRecibido.set(127);
    componente.medioPago.set('EFECTIVO');
    const promesa = componente.cerrar();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/registrar-salida`)
      .flush(apiError('PRECONDITION_FAILED', {}, 'cid-412'), {
        status: 412,
        statusText: 'Precondition Failed',
      });
    await promesa;
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/1001`)
      .flush(envelopeSingle(recepcion({ version: 9 })), { headers: { ETag: '"9"' } });
    await microtasks();
    await microtasks();
    http
      .expectOne(
        (r) => r.url === `${API_BASE_URL}/api/recepcion/1001/cotizacion-salida`
      )
      .flush(envelopeSingle(COTIZACION));
    await microtasks();
    fixture.detectChanges();

    expect(componente.etag()).toBe('"9"');
    expect(componente.error()?.code).toBe('PRECONDITION_FAILED');
    expect(texto()).toContain('Otra persona modificó este registro. Recarga para ver los cambios.');
  });
});
