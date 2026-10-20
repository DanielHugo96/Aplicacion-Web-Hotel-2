import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
  PERSONA_ADMIN,
  PERSONA_CLIENTE,
  persona,
  recepcion,
} from '../testing/recepcion.fixtures';
import { CheckInComponent, fechaIsoLima } from './check-in.component';

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

describe('CheckInComponent', () => {
  let fixture: ComponentFixture<CheckInComponent>;
  let componente: CheckInComponent;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CheckInComponent],
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
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  async function crear(habitaciones = [habitacion()]): Promise<void> {
    fixture = TestBed.createComponent(CheckInComponent);
    componente = fixture.componentInstance;
    fixture.detectChanges();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/habitacion/listar` &&
          r.params.get('idHotel') === '1' &&
          r.params.get('page') === '0' &&
          r.params.get('size') === '100'
      )
      .flush(envelopePage(habitaciones, 0, 100, habitaciones.length));
    await microtasks();
    fixture.detectChanges();
  }

  it('busca por documento exacto y solo deja seleccionar Cliente activo', async () => {
    await crear();
    componente.documento.set('70000001');
    const promesa = componente.buscarCliente();
    const req = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/persona/listar` &&
        r.params.get('documento') === '70000001' &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '20'
    );
    expect(req.request.params.has('idTipoPersona')).toBe(false);
    req.flush(
      envelopePage([
        persona(),
        persona({ idPersona: 1, tipoPersona: 'Administrador', estado: true }),
        persona({ idPersona: 101, estado: false }),
      ])
    );
    await promesa;
    fixture.detectChanges();

    expect(componente.cliente()?.idPersona).toBe(100);
    expect(componente.resultados()).toHaveLength(1);
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('70000001');
    expect(texto).not.toContain('Ana Admin');
  });

  it('sin resultados muestra «Cliente no encontrado: créalo primero» y el enlace al alta', async () => {
    await crear([habitacion()]);
    componente.documento.set('99999999');
    const promesa = componente.buscarCliente();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/persona/listar`)
      .flush(envelopePage([]));
    await promesa;
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Cliente no encontrado: créalo primero');
    const enlace = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href="/app/personas/nuevo"]'
    );
    expect(enlace).not.toBeNull();
    expect(componente.cliente()).toBeNull();
  });

  it('el 400 de documento se pinta en el campo y no selecciona cliente', async () => {
    await crear([habitacion()]);
    componente.documento.set('12');
    const promesa = componente.buscarCliente();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/persona/listar`)
      .flush(apiError('VALIDATION_ERROR', { documento: 'Documento inválido' }, 'cid-400'), {
        status: 400,
        statusText: 'Bad Request',
      });
    await promesa;
    fixture.detectChanges();

    expect(componente.errores().idCliente).toBe('Documento inválido');
    expect(fixture.nativeElement.textContent).toContain('Documento inválido');
    expect(componente.cliente()).toBeNull();
  });

  it('solo lista habitaciones activas y muestra DESCONOCIDA como «Sin datos», nunca libre', async () => {
    await crear([
      habitacion(),
      habitacion({
        idHabitacion: 102,
        numero: '102',
        estado: true,
        ocupacion: { estadoEstadia: 'DESCONOCIDA', idRecepcion: null, updatedAt: '2026-10-20T12:00:00Z' },
      }),
      habitacion({ idHabitacion: 103, numero: '103', estado: false }),
    ]);

    const filas = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr');
    expect(filas).toHaveLength(2);
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Sin datos');
    expect(texto).not.toContain('Disponible');
    expect(componente.seleccionable(habitacion({
      idHabitacion: 102,
      ocupacion: { estadoEstadia: 'DESCONOCIDA', idRecepcion: null, updatedAt: '2026-10-20T12:00:00Z' },
    }))).toBe(false);
    const radio102 = (fixture.nativeElement as HTMLElement).querySelector(
      '#habitacion-102'
    ) as HTMLInputElement;
    expect(radio102.disabled).toBe(true);
  });

  it('sin sede seleccionada pide elegirla y no pide habitaciones', async () => {
    TestBed.inject(SedeContextService).selectedId.set(null);
    fixture = TestBed.createComponent(CheckInComponent);
    componente = fixture.componentInstance;
    fixture.detectChanges();
    await microtasks();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Elige una sede en el selector del panel para ver sus habitaciones.'
    );
    http.expectNone((r) => r.url === `${API_BASE_URL}/api/habitacion/listar`);
  });

  it('valida el adelanto contra el alojamiento y exige medio de pago', async () => {
    await crear([habitacion({ precio: 70 })]);
    componente.seleccionarCliente(persona());
    componente.idHabitacion.set(101);
    componente.fechaSalida.set(fechaIsoLima(1));
    componente.adelanto.set(100);
    await componente.registrar();
    expect(componente.errores().adelanto).toBe('El adelanto no puede superar el alojamiento');
    http.expectNone(`${API_BASE_URL}/api/recepcion/registrar`);

    componente.adelanto.set(20);
    await componente.registrar();
    expect(componente.errores().medioPago).toBe('Selecciona el medio de pago del adelanto');
    http.expectNone(`${API_BASE_URL}/api/recepcion/registrar`);
  });

  it('rechaza una salida anterior a hoy', async () => {
    await crear([habitacion()]);
    componente.seleccionarCliente(persona());
    componente.idHabitacion.set(101);
    componente.fechaSalida.set('2020-01-01');
    await componente.registrar();
    expect(componente.errores().fechaSalida).toBe(
      'La fecha de salida no puede ser anterior a hoy'
    );
    http.expectNone(`${API_BASE_URL}/api/recepcion/registrar`);
  });

  it('un 201 navega al detalle y envía solo los campos del contrato', async () => {
    await crear([habitacion({ precio: 70 })]);
    componente.seleccionarCliente(persona());
    componente.idHabitacion.set(101);
    componente.fechaSalida.set(fechaIsoLima(2));
    componente.adelanto.set(20);
    componente.medioPago.set('EFECTIVO');
    componente.observacion.set('Ingreso presencial');

    const promesa = componente.registrar();
    const req = http.expectOne(`${API_BASE_URL}/api/recepcion/registrar`);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(req.request.body).toEqual({
      idHotel: 1,
      idCliente: 100,
      idHabitacion: 101,
      fechaSalida: fechaIsoLima(2),
      adelanto: 20,
      medioPago: 'EFECTIVO',
      observacion: 'Ingreso presencial',
    });
    req.flush(envelopeSingle(recepcion()), { status: 201, statusText: 'Created' });
    await promesa;

    expect(router.navigateByUrl).toHaveBeenCalledWith('/app/recepcion/1001');
    expect(TestBed.inject(ToastService).message()).toBe('Check-in registrado');
  });

  it('un 409 ROOM_OCCUPIED se pinta junto al campo idHabitacion', async () => {
    await crear([habitacion()]);
    componente.seleccionarCliente(persona());
    componente.idHabitacion.set(101);

    const promesa = componente.registrar();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/registrar`)
      .flush(apiError('ROOM_OCCUPIED', { idHabitacion: '101' }, 'cid-409'), {
        status: 409,
        statusText: 'Conflict',
      });
    await promesa;
    fixture.detectChanges();

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('La habitación ya está ocupada.');
    expect(componente.errores().idHabitacion).toBe('La habitación ya está ocupada.');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('un 503 se reintenta con la misma clave y el mismo cuerpo', async () => {
    await crear([habitacion()]);
    componente.seleccionarCliente(persona());
    componente.idHabitacion.set(101);
    componente.adelanto.set(0);

    const primera = componente.registrar();
    const req1 = http.expectOne(`${API_BASE_URL}/api/recepcion/registrar`);
    const clave = req1.request.headers.get('Idempotency-Key');
    const cuerpo = req1.request.body;
    req1.flush(apiError('DEPENDENCY_UNAVAILABLE', {}, 'cid-503'), {
      status: 503,
      statusText: 'Service Unavailable',
    });
    await primera;
    fixture.detectChanges();
    expect(componente.actionLabel()).toBe('Reintentar');

    boton(fixture, 'Reintentar').click();
    await microtasks();
    const req2 = http.expectOne(`${API_BASE_URL}/api/recepcion/registrar`);
    expect(req2.request.headers.get('Idempotency-Key')).toBe(clave);
    expect(req2.request.body).toEqual(cuerpo);
    req2.flush(envelopeSingle(recepcion()), { status: 201, statusText: 'Created' });
    await microtasks();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/app/recepcion/1001');
  });

  it('no envía medioPago cuando el adelanto es 0', async () => {
    await crear([habitacion()]);
    componente.seleccionarCliente(PERSONA_CLIENTE);
    componente.idHabitacion.set(101);
    componente.adelanto.set(0);

    const promesa = componente.registrar();
    const req = http.expectOne(`${API_BASE_URL}/api/recepcion/registrar`);
    expect('medioPago' in (req.request.body as object)).toBe(false);
    expect('observacion' in (req.request.body as object)).toBe(false);
    req.flush(envelopeSingle(recepcion()), { status: 201, statusText: 'Created' });
    await promesa;
  });
});
