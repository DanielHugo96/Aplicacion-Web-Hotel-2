import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { gatewayInterceptor } from '../../../core/gateway.interceptor';
import { SessionService } from '../../../core/services/session.service';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { ETagCacheService } from '../../../core/services/etag-cache.service';
import { ToastService } from '../../../core/services/toast.service';
import { apiError, authData, envelopeSingle, PERSONA_ADMIN, recepcion } from '../testing/recepcion.fixtures';
import { EstadiaDetalleComponent } from './estadia-detalle.component';

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

describe('EstadiaDetalleComponent', () => {
  let fixture: ComponentFixture<EstadiaDetalleComponent>;
  let http: HttpTestingController;
  let componente: EstadiaDetalleComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [EstadiaDetalleComponent],
      providers: [
        SessionService,
        RecepcionService,
        ETagCacheService,
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
    fixture = TestBed.createComponent(EstadiaDetalleComponent);
    componente = fixture.componentInstance;
  });

  afterEach(() => http.verify());

  async function cargar(data = recepcion(), etag = '"7"'): Promise<void> {
    fixture.detectChanges();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/1001`)
      .flush(envelopeSingle(data), { headers: { ETag: etag } });
    await microtasks();
    fixture.detectChanges();
  }

  it('muestra los snapshots y los importes del schema completo', async () => {
    await cargar();
    const texto = (fixture.nativeElement.textContent as string).replace(/\u00a0/g, ' ');
    expect(texto).toContain('Carla Cliente');
    expect(texto).toContain('DNI 70000001');
    expect(texto).toContain('Habitación 101');
    expect(texto).toContain('19/10/2026');
    expect(texto).toContain('21/10/2026');
    expect(texto).toContain('S/ 140.00');
    expect(texto).toContain('S/ 20.00');
    expect(texto).toContain('EFECTIVO');
    expect(texto).toContain('S/ 120.00');
    expect(texto).toContain('Ingreso presencial');
    expect(texto).toContain('Activa');
    expect(componente.etag()).toBe('"7"');
  });

  it('muestra «No registrado» cuando el adelanto no tiene medio de pago', async () => {
    await cargar(recepcion({ medioPagoAdelanto: null }));
    expect(fixture.nativeElement.textContent).toContain('No registrado');
  });

  it('un 404 de estadía ajena muestra el mensaje del catálogo sin datos', async () => {
    fixture.detectChanges();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/1001`)
      .flush(apiError('RESOURCE_NOT_FOUND', {}, 'cid-404'), { status: 404, statusText: 'Not Found' });
    await microtasks();
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('No encontramos lo que buscas.');
    expect(texto).toContain('cid-404');
    expect(texto).not.toContain('Carla Cliente');
    expect(componente.estadia()).toBeNull();
  });

  it('el detalle monta el bloque de cierre con la estadía y el ETag ya cargados', async () => {
    await cargar(
      recepcion({
        estadoEstadia: 'CERRADA',
        estado: false,
        cierre: {
          closureId: '20000000-0000-4000-8000-000000000002',
          estado: 'COMPLETADO',
          lastErrorCode: null,
          calculo: null,
          medioPago: 'EFECTIVO',
        },
      }),
      '"7"'
    );
    const panel = (fixture.nativeElement as HTMLElement).querySelector('app-cierre-panel');
    expect(panel).toBeTruthy();
    expect(panel!.textContent).toContain('20000000-0000-4000-8000-000000000002');
    expect(panel!.textContent).toContain('Completado');
    // El bloque no relee la estadía: usa la que cargó el detalle.
    http.expectNone(`${API_BASE_URL}/api/recepcion/1001`);
  });

  it('una estadía vencida muestra «Estadía vencida sin cierre» en píldora', async () => {
    await cargar(recepcion({ vencida: true }));
    const pills = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.pill')
    ) as HTMLElement[];
    expect(pills.some((p) => p.textContent?.includes('Estadía vencida sin cierre'))).toBe(true);
    expect(pills.some((p) => p.textContent?.includes('Activa'))).toBe(true);
  });

  it('un 200 de edición refresca el detalle con su nuevo ETag', async () => {
    await cargar();
    componente.abrirEdicion();
    componente.fechaSalida.set('2026-10-22');
    componente.observacion.set('Extiende una noche');

    const promesa = componente.guardar();
    const req = http.expectOne(`${API_BASE_URL}/api/recepcion/actualizar/1001`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.headers.get('If-Match')).toBe('"7"');
    expect(req.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(req.request.body).toEqual({
      fechaSalida: '2026-10-22',
      observacion: 'Extiende una noche',
    });
    req.flush(
      envelopeSingle(recepcion({ fechaSalida: '2026-10-22', precioInicial: 210, version: 2 })),
      { headers: { ETag: '"8"' } }
    );
    await promesa;
    fixture.detectChanges();

    expect(componente.etag()).toBe('"8"');
    expect(componente.estadia()?.fechaSalida).toBe('2026-10-22');
    expect(componente.editando()).toBe(false);
    expect(TestBed.inject(ToastService).message()).toBe('Cambios guardados');
    expect(fixture.nativeElement.textContent).toContain('22/10/2026');
  });

  it('un 412 recarga el detalle sin sobrescribir y conserva el mensaje del catálogo', async () => {
    await cargar();
    componente.abrirEdicion();
    componente.fechaSalida.set('2026-10-23');

    const promesa = componente.guardar();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/actualizar/1001`)
      .flush(apiError('PRECONDITION_FAILED', {}, 'cid-412'), {
        status: 412,
        statusText: 'Precondition Failed',
      });
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/1001`)
      .flush(envelopeSingle(recepcion({ fechaSalida: '2026-10-24', version: 9 })), {
        headers: { ETag: '"9"' },
      });
    await promesa;
    await microtasks();
    fixture.detectChanges();

    expect(componente.etag()).toBe('"9"');
    expect(componente.errorSalida()?.code).toBe('PRECONDITION_FAILED');
    expect(fixture.nativeElement.textContent).toContain(
      'Otra persona modificó este registro. Recarga para ver los cambios.'
    );
  });

  it('un 409 ADVANCE_EXCEEDS_NEW_TOTAL pinta el mensaje y el campo fechaSalida', async () => {
    await cargar();
    componente.abrirEdicion();
    // Pasa la validación local (1 noche, alojamiento 70 ≥ adelanto 20); el
    // backend rechaza igualmente y el front pinta el mensaje del catálogo.
    componente.fechaSalida.set('2026-10-20');

    const promesa = componente.guardar();
    const req = http.expectOne(`${API_BASE_URL}/api/recepcion/actualizar/1001`);
    req.flush(
      apiError('ADVANCE_EXCEEDS_NEW_TOTAL', { fechaSalida: 'La nueva fecha deja el alojamiento por debajo del adelanto registrado.' }, 'cid-409'),
      { status: 409, statusText: 'Conflict' }
    );
    await promesa;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'La nueva fecha deja el alojamiento por debajo del adelanto registrado.'
    );
    expect(componente.errores().fechaSalida).toBe(
      'La nueva fecha deja el alojamiento por debajo del adelanto registrado.'
    );
    expect(componente.editando()).toBe(true);
  });

  it('valida la fecha contra la entrada y el adelanto antes de enviar', async () => {
    await cargar();
    componente.abrirEdicion();
    componente.fechaSalida.set('2026-10-18');
    await componente.guardar();
    expect(componente.errores().fechaSalida).toBe(
      'La fecha de salida no puede ser anterior a la entrada.'
    );
    http.expectNone(`${API_BASE_URL}/api/recepcion/actualizar/1001`);
  });

  it('el doble clic no reenvía la intención', async () => {
    await cargar();
    componente.abrirEdicion();
    componente.fechaSalida.set('2026-10-22');
    const primera = componente.guardar();
    const segunda = componente.guardar();
    const req = http.expectOne(`${API_BASE_URL}/api/recepcion/actualizar/1001`);
    req.flush(envelopeSingle(recepcion({ fechaSalida: '2026-10-22' })), { headers: { ETag: '"8"' } });
    await Promise.all([primera, segunda]);
    http.expectNone(`${API_BASE_URL}/api/recepcion/actualizar/1001`);
    expect(componente.editando()).toBe(false);
  });

  it('un 503 conserva la misma clave y el mismo cuerpo al reintentar', async () => {
    await cargar();
    componente.abrirEdicion();
    componente.fechaSalida.set('2026-10-22');

    const primera = componente.guardar();
    const req1 = http.expectOne(`${API_BASE_URL}/api/recepcion/actualizar/1001`);
    const clave = req1.request.headers.get('Idempotency-Key');
    const cuerpo = req1.request.body;
    req1.flush(apiError('DEPENDENCY_UNAVAILABLE', {}, 'cid-503'), {
      status: 503,
      statusText: 'Service Unavailable',
    });
    await primera;
    fixture.detectChanges();
    expect(componente.errorSalida()?.retryable).toBe(true);

    boton(fixture, 'Reintentar').click();
    await microtasks();
    const req2 = http.expectOne(`${API_BASE_URL}/api/recepcion/actualizar/1001`);
    expect(req2.request.headers.get('Idempotency-Key')).toBe(clave);
    expect(req2.request.body).toEqual(cuerpo);
    req2.flush(envelopeSingle(recepcion({ fechaSalida: '2026-10-22' })), {
      headers: { ETag: '"8"' },
    });
    await microtasks();
  });
});
