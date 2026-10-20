import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { ClienteService } from '../../../core/services/cliente.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { VentasService } from '../../../core/services/ventas.service';
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

describe('ProductoFormComponent (alta)', () => {
  let fixture: ComponentFixture<ProductoFormComponent>;
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
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
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), personaAdmin);
    TestBed.inject(SedeContextService).selectedId.set(1);
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(ProductoFormComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  function rellenar(overrides: Record<string, string> = {}): void {
    const valores: Record<string, string> = {
      '#sku': 'AGUA-500',
      '#nombre': 'Agua 500ml',
      '#precio': '3.50',
      '#cantidadInicial': '20',
      '#umbralBajo': '5',
      '#detalle': 'Botella de agua de 500 ml',
      '#imagenUrl': '/assets/productos/agua.png',
      ...overrides,
    };
    const el = fixture.nativeElement as HTMLElement;
    for (const [selector, valor] of Object.entries(valores)) {
      const input = el.querySelector(selector) as HTMLInputElement;
      input.value = valor;
      input.dispatchEvent(new Event('input'));
    }
    fixture.detectChanges();
  }

  async function enviar(): Promise<void> {
    const form = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    await microtasks();
  }

  it('201 registra y vuelve al listado con el toast del sistema', async () => {
    rellenar();
    await enviar();
    const req = http.expectOne(`${API_BASE_URL}/api/producto/registrar`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      sku: 'AGUA-500',
      nombre: 'Agua 500ml',
      detalle: 'Botella de agua de 500 ml',
      imagenUrl: '/assets/productos/agua.png',
      idHotel: 1,
      precio: 3.5,
      cantidadInicial: 20,
      umbralBajo: 5,
    });
    for (const prohibido of ['cantidad', 'version', 'productoVersion', 'idCliente']) {
      expect(req.request.body).not.toHaveProperty(prohibido);
    }
    req.flush(envelopeSingle(producto()), { status: 201, statusText: 'Created' });
    await microtasks();
    expect(TestBed.inject(ToastService).message()).toBe('Producto registrado');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/app/productos');
  });

  it('409 DUPLICATE_RESOURCE resalta el SKU con el mensaje del catálogo', async () => {
    rellenar();
    await enviar();
    http
      .expectOne(`${API_BASE_URL}/api/producto/registrar`)
      .flush(apiError('DUPLICATE_RESOURCE', { sku: 'Ese valor ya está registrado.' }), {
        status: 409,
        statusText: 'Conflict',
      });
    await microtasks();
    fixture.detectChanges();
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Ese valor ya está registrado.');
    expect(texto).toContain('DUPLICATE_RESOURCE');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('400 VALIDATION_ERROR resalta error.fields y no navega', async () => {
    rellenar({ '#precio': '0' });
    // La validación local ya bloquea precio 0; se corrige y el backend
    // devuelve el 400 del campo para comprobar el pintado de error.fields.
    rellenar({ '#precio': '3.5' });
    await enviar();
    http
      .expectOne(`${API_BASE_URL}/api/producto/registrar`)
      .flush(apiError('VALIDATION_ERROR', { nombre: 'Máximo 120 caracteres' }), {
        status: 400,
        statusText: 'Bad Request',
      });
    await microtasks();
    fixture.detectChanges();
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Revisa los datos marcados.');
    expect(texto).toContain('Máximo 120 caracteres');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('el doble clic no reenvía la intención', async () => {
    rellenar();
    await enviar();
    const req = http.expectOne(`${API_BASE_URL}/api/producto/registrar`);
    // Segundo submit mientras la primera petición sigue en curso.
    await enviar();
    http.expectNone(`${API_BASE_URL}/api/producto/registrar`);
    req.flush(envelopeSingle(producto()), { status: 201, statusText: 'Created' });
    await microtasks();
  });

  it('valida todos los campos con los mensajes de 08 §3 antes de enviar', async () => {
    rellenar({ '#sku': '', '#nombre': '', '#precio': '0', '#cantidadInicial': '-1' });
    await enviar();
    http.expectNone(`${API_BASE_URL}/api/producto/registrar`);
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Campo obligatorio');
    expect(texto).toContain('El precio debe ser mayor que 0');
    expect(texto).toContain('La cantidad no puede ser negativa');
  });
});
