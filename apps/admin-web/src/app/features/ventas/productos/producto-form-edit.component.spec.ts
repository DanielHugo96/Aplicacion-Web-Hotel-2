import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { VentasService } from '../../../core/services/ventas.service';
import { ClienteService } from '../../../core/services/cliente.service';
import {
  apiError,
  authData,
  envelopeSingle,
  personaAdmin,
  producto,
} from '../testing/ventas.fixtures';
import { ProductoFormComponent } from './producto-form.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

describe('ProductoFormComponent (edición)', () => {
  let fixture: ComponentFixture<ProductoFormComponent>;
  let http: HttpTestingController;
  let componente: ProductoFormComponent;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [ProductoFormComponent],
      providers: [
        SessionService,
        SedeContextService,
        VentasService,
        ClienteService,
        ToastService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '7' }) } },
        },
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), personaAdmin);
    TestBed.inject(SedeContextService).selectedId.set(1);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductoFormComponent);
    componente = fixture.componentInstance;
    fixture.detectChanges();
    await microtasks();
    const req = http.expectOne(`${API_BASE_URL}/api/producto/buscar/7?idHotel=1`);
    expect(req.request.method).toBe('GET');
    req.flush(envelopeSingle(producto({ idProducto: 7, productoVersion: 2, version: 3, cantidad: 11 })), {
      status: 200,
      statusText: 'OK',
      headers: { ETag: '"3"' },
    });
    await microtasks();
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
  });

  function cambiar(selector: string, valor: string): void {
    const input = fixture.nativeElement.querySelector(selector) as HTMLInputElement;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  async function enviar(): Promise<void> {
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    await microtasks();
  }

  it('carga el producto y muestra la cantidad sin permitir editarla', () => {
    expect(componente.sku).toBe('AGUA-500');
    expect(componente.producto()?.cantidad).toBe(11);
    const cantidad = fixture.nativeElement.querySelector('#cantidadActual') as HTMLInputElement;
    expect(cantidad.readOnly).toBe(true);
    expect(fixture.nativeElement.querySelector('#sku').readOnly).toBe(true);
  });

  it('200 envía If-Match y productoVersion, sin cantidad, y refresca el ETag', async () => {
    cambiar('#nombre', 'Agua 500ml premium');
    cambiar('#precio', '4.25');
    await enviar();
    const req = http.expectOne(`${API_BASE_URL}/api/producto/actualizar/7?idHotel=1`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.headers.get('If-Match')).toBe('"3"');
    expect(req.request.body).toEqual({
      nombre: 'Agua 500ml premium',
      detalle: 'Botella de agua de 500 ml',
      imagenUrl: '/assets/productos/agua.png',
      precio: 4.25,
      umbralBajo: 5,
      estado: true,
      productoVersion: 2,
    });
    expect(req.request.body).not.toHaveProperty('cantidad');
    req.flush(envelopeSingle(producto({ idProducto: 7, nombre: 'Agua 500ml premium', version: 4 })), {
      status: 200,
      statusText: 'OK',
      headers: { ETag: '"4"' },
    });
    await microtasks();
    expect(componente.etag()).toBe('"4"');
    expect(TestBed.inject(ToastService).message()).toBe('Cambios guardados');
  });

  it('412 muestra el mensaje del catálogo y no sobrescribe', async () => {
    cambiar('#nombre', 'Nombre en conflicto');
    await enviar();
    http
      .expectOne(`${API_BASE_URL}/api/producto/actualizar/7?idHotel=1`)
      .flush(apiError('PRECONDITION_FAILED'), { status: 412, statusText: 'Precondition Failed' });
    await microtasks();
    fixture.detectChanges();
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Otra persona modificó este registro. Recarga para ver los cambios.');
    expect(texto).toContain('Recargar');
    expect(componente.producto()?.nombre).toBe('Agua 500ml');
  });

  it('428 se resuelve recargando el recurso', async () => {
    await enviar();
    http
      .expectOne(`${API_BASE_URL}/api/producto/actualizar/7?idHotel=1`)
      .flush(apiError('PRECONDITION_REQUIRED'), {
        status: 428,
        statusText: 'Precondition Required',
      });
    await microtasks();
    fixture.detectChanges();
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Falta la versión del registro.');
    const boton = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>
    ).find((b) => b.textContent?.includes('Recargar')) as HTMLButtonElement;
    expect(boton).toBeTruthy();
    boton.click();
    await microtasks();
    http.expectOne(`${API_BASE_URL}/api/producto/buscar/7?idHotel=1`).flush(
      envelopeSingle(producto({ idProducto: 7 })),
      { status: 200, statusText: 'OK', headers: { ETag: '"3"' } }
    );
    await microtasks();
  });
});
