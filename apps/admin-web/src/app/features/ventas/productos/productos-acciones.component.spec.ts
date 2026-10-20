import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { ClienteService } from '../../../core/services/cliente.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { VentasService } from '../../../core/services/ventas.service';
import {
  apiError,
  authData,
  envelopePage,
  envelopeSingle,
  personaAdmin,
  producto,
} from '../testing/ventas.fixtures';
import { ProductosListComponent } from './productos-list.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

function botones(fixture: ComponentFixture<unknown>): HTMLButtonElement[] {
  return Array.from(
    (fixture.nativeElement as HTMLElement).querySelectorAll('button')
  ) as HTMLButtonElement[];
}

function boton(fixture: ComponentFixture<unknown>, texto: string): HTMLButtonElement {
  const encontrado = botones(fixture).find((b) => (b.textContent ?? '').trim() === texto);
  if (!encontrado) throw new Error(`No hay botón "${texto}"`);
  return encontrado;
}

function enviarFormulario(fixture: ComponentFixture<unknown>): void {
  const form = (fixture.nativeElement as HTMLElement).querySelector(
    '.modal form'
  ) as HTMLFormElement;
  if (!form) throw new Error('No hay formulario en el diálogo');
  form.dispatchEvent(new Event('submit'));
}

function botonModal(fixture: ComponentFixture<unknown>, texto: string): HTMLButtonElement {
  const modal = (fixture.nativeElement as HTMLElement).querySelector('.modal');
  const encontrado = Array.from(modal?.querySelectorAll('button') ?? []).find(
    (b) => (b.textContent ?? '').trim() === texto
  );
  if (!encontrado) throw new Error(`No hay botón "${texto}" en el diálogo`);
  return encontrado as HTMLButtonElement;
}

describe('ProductosListComponent (acciones A)', () => {
  let fixture: ComponentFixture<ProductosListComponent>;
  let http: HttpTestingController;
  let sedes: SedeContextService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ProductosListComponent],
      providers: [
        SessionService,
        SedeContextService,
        VentasService,
        ClienteService,
        ToastService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), personaAdmin);
    sedes = TestBed.inject(SedeContextService);
    sedes.selectedId.set(1);
    sedes.sedes.set([
      { idHotel: 1, nombre: 'Lima' } as any,
      { idHotel: 2, nombre: 'Arequipa' } as any,
    ]);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductosListComponent);
  });

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  async function cargar(data = [producto()]): Promise<void> {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/producto/listar`)
      .flush(envelopePage(data));
    await microtasks();
    fixture.detectChanges();
  }

  it('200 da de baja con If-Match y recarga el listado sin el producto', async () => {
    await cargar();
    boton(fixture, 'Dar de baja').click();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.modal')).toBeTruthy();
    botonModal(fixture, 'Dar de baja').click();
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/producto/buscar/1?idHotel=1`)
      .flush(envelopeSingle(producto()), { status: 200, statusText: 'OK', headers: { ETag: '"3"' } });
    await microtasks();
    const del = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/producto/eliminar/1` && r.method === 'DELETE'
    );
    expect(del.request.params.get('idHotel')).toBe('1');
    expect(del.request.headers.get('If-Match')).toBe('"3"');
    del.flush(envelopeSingle(producto({ estado: false })), {
      status: 200,
      statusText: 'OK',
      headers: { ETag: '"4"' },
    });
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/producto/listar`)
      .flush(envelopePage([]));
    await microtasks();
    expect(TestBed.inject(ToastService).message()).toBe('Producto dado de baja');
    expect(fixture.nativeElement.textContent).toContain('Aún no hay registros');
  });

  it('412 en la baja muestra el mensaje del catálogo sin sobrescribir', async () => {
    await cargar();
    boton(fixture, 'Dar de baja').click();
    fixture.detectChanges();
    botonModal(fixture, 'Dar de baja').click();
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/producto/buscar/1?idHotel=1`)
      .flush(envelopeSingle(producto()), { status: 200, statusText: 'OK', headers: { ETag: '"3"' } });
    await microtasks();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/producto/eliminar/1` &&
          r.params.get('idHotel') === '1'
      )
      .flush(apiError('PRECONDITION_FAILED'), { status: 412, statusText: 'Precondition Failed' });
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'Otra persona modificó este registro. Recarga para ver los cambios.'
    );
  });

  it('la reactivación conserva la cantidad y manda solo estado y productoVersion', async () => {
    await cargar([producto({ estado: false, cantidad: 11 })]);
    boton(fixture, 'Reactivar').click();
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/producto/buscar/1?idHotel=1`)
      .flush(envelopeSingle(producto({ estado: false, cantidad: 11, productoVersion: 2 })), {
        status: 200,
        statusText: 'OK',
        headers: { ETag: '"3"' },
      });
    await microtasks();
    const put = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/producto/actualizar/1` && r.method === 'PUT'
    );
    expect(put.request.body).toEqual({ estado: true, productoVersion: 2 });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    put.flush(envelopeSingle(producto({ estado: true, cantidad: 11 })), {
      status: 200,
      statusText: 'OK',
      headers: { ETag: '"4"' },
    });
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/producto/listar`)
      .flush(envelopePage([producto({ estado: true, cantidad: 11 })]));
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('11');
  });

  it('201 del ajuste actualiza cantidad y versión sin recalcular precios', async () => {
    await cargar([producto({ cantidad: 20 })]);
    boton(fixture, 'Ajustar stock').click();
    fixture.detectChanges();
    fixture.componentInstance.ajusteDelta = 5;
    fixture.componentInstance.ajusteMotivo = 'Reposición de proveedor';
    fixture.detectChanges();
    enviarFormulario(fixture);
    await microtasks();
    expect(fixture.componentInstance.ajusteErrores()).toEqual({});
    http
      .expectOne(`${API_BASE_URL}/api/producto/buscar/1?idHotel=1`)
      .flush(envelopeSingle(producto()), { status: 200, statusText: 'OK', headers: { ETag: '"3"' } });
    await microtasks();
    const post = http.expectOne(`${API_BASE_URL}/api/producto/1/ajustes`);
    expect(post.request.body).toEqual({
      idHotel: 1,
      delta: 5,
      motivo: 'Reposición de proveedor',
    });
    expect(post.request.headers.get('If-Match')).toBe('"3"');
    post.flush(
      envelopeSingle({ ajusteId: 'ajuste-1', cantidad: 25, version: 4 }),
      { status: 201, statusText: 'Created' }
    );
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('25');
    expect(TestBed.inject(ToastService).message()).toBe('Stock ajustado');
  });

  it('no permite enviar delta 0 y el 400 resalta el campo', async () => {
    await cargar();
    boton(fixture, 'Ajustar stock').click();
    fixture.detectChanges();
    fixture.componentInstance.ajusteDelta = 0;
    fixture.componentInstance.ajusteMotivo = 'Reposición de proveedor';
    fixture.detectChanges();
    enviarFormulario(fixture);
    await microtasks();
    http.expectNone(`${API_BASE_URL}/api/producto/1/ajustes`);
    expect(fixture.nativeElement.textContent).toContain('El delta no puede ser 0');

    fixture.componentInstance.ajusteDelta = 3;
    fixture.detectChanges();
    enviarFormulario(fixture);
    await microtasks();
    expect(fixture.componentInstance.ajusteErrores()).toEqual({});
    http
      .expectOne(`${API_BASE_URL}/api/producto/buscar/1?idHotel=1`)
      .flush(envelopeSingle(producto()), { status: 200, statusText: 'OK', headers: { ETag: '"3"' } });
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/producto/1/ajustes`)
      .flush(apiError('VALIDATION_ERROR', { delta: 'El delta no puede ser 0' }), {
        status: 400,
        statusText: 'Bad Request',
      });
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('El delta no puede ser 0');
  });

  it('412 del ajuste muestra la acción esperada', async () => {
    await cargar();
    boton(fixture, 'Ajustar stock').click();
    fixture.detectChanges();
    fixture.componentInstance.ajusteDelta = -2;
    fixture.componentInstance.ajusteMotivo = 'Reposición de proveedor';
    fixture.detectChanges();
    enviarFormulario(fixture);
    await microtasks();
    expect(fixture.componentInstance.ajusteErrores()).toEqual({});
    http
      .expectOne(`${API_BASE_URL}/api/producto/buscar/1?idHotel=1`)
      .flush(envelopeSingle(producto()), { status: 200, statusText: 'OK', headers: { ETag: '"3"' } });
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/producto/1/ajustes`)
      .flush(apiError('PRECONDITION_FAILED'), { status: 412, statusText: 'Precondition Failed' });
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain(
      'Otra persona modificó este registro. Recarga para ver los cambios.'
    );
  });

  it('201 de la asignación envía los cuatro campos y recarga', async () => {
    await cargar();
    boton(fixture, 'Asignar a sede').click();
    fixture.detectChanges();
    const post = async () => {
      botonModal(fixture, 'Asignar a sede').click();
      await microtasks();
      return http.expectOne(`${API_BASE_URL}/api/producto/1/sedes`);
    };
    const req = await post();
    expect(req.request.body).toEqual({
      idHotel: 2,
      precio: 3.5,
      cantidadInicial: 0,
      umbralBajo: 5,
    });
    req.flush(envelopeSingle(producto({ idHotel: 2 })), {
      status: 201,
      statusText: 'Created',
    });
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/producto/listar`)
      .flush(envelopePage([producto()]));
    await microtasks();
    expect(TestBed.inject(ToastService).message()).toBe('Producto asignado a la sede');
  });

  it('409 de la asignación muestra el mensaje del catálogo', async () => {
    await cargar();
    boton(fixture, 'Asignar a sede').click();
    fixture.detectChanges();
    enviarFormulario(fixture);
    await microtasks();
    http
      .expectOne(`${API_BASE_URL}/api/producto/1/sedes`)
      .flush(apiError('DUPLICATE_RESOURCE'), { status: 409, statusText: 'Conflict' });
    await microtasks();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Ese valor ya está registrado.');
  });

  it('con rol E no aparecen las acciones de mutación', async () => {
    TestBed.inject(SessionService).setSession(authData('Empleado', [1]), {
      ...personaAdmin,
      idPersona: 3,
      tipoPersona: 'Empleado',
    });
    await cargar();
    const textos = botones(fixture).map((b) => (b.textContent ?? '').trim());
    expect(textos).not.toContain('Editar');
    expect(textos).not.toContain('Ajustar stock');
    expect(textos).not.toContain('Asignar a sede');
    expect(textos).not.toContain('Dar de baja');
    expect(textos).toContain('Movimientos');
  });
});
