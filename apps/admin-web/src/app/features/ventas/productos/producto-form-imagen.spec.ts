import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { gatewayInterceptor } from '../../../core/gateway.interceptor';
import { ClienteService } from '../../../core/services/cliente.service';
import { ETagCacheService } from '../../../core/services/etag-cache.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { VentasService } from '../../../core/services/ventas.service';
import {
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

describe('ProductoFormComponent — vista previa de imagenUrl', () => {
  let fixture: ComponentFixture<ProductoFormComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ProductoFormComponent],
      providers: [
        SessionService,
        SedeContextService,
        VentasService,
        ClienteService,
        ETagCacheService,
        ToastService,
        provideHttpClient(withInterceptors([gatewayInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({}) } },
        },
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), personaAdmin);
    TestBed.inject(SedeContextService).selectedId.set(1);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductoFormComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
  });

  function el(selector: string): HTMLInputElement {
    return (fixture.nativeElement as HTMLElement).querySelector(selector) as HTMLInputElement;
  }

  function escribir(selector: string, valor: string): void {
    const input = el(selector);
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('la vista previa aparece al escribir una URL válida con el nombre como alt', () => {
    escribir('#nombre', 'Agua 500ml');
    expect(fixture.nativeElement.querySelector('app-imagen-panel')).toBeNull();

    escribir('#imagenUrl', '/assets/productos/agua-500ml.png');
    const img = el('app-imagen-panel img');
    expect(img.getAttribute('src')).toBe('/assets/productos/agua-500ml.png');
    expect(img.getAttribute('alt')).toBe('Producto Agua 500ml');
    expect(img.getAttribute('loading')).toBe('lazy');
  });

  it('una URL inválida no pinta vista previa y deja el hueco neutro', () => {
    escribir('#imagenUrl', 'ftp://ejemplo.test/foto.png');

    expect(fixture.nativeElement.querySelector('app-imagen-panel')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Sin vista previa');
  });

  it('una URL rota muestra el texto junto al campo y conserva el valor', () => {
    escribir('#nombre', 'Snack');
    escribir('#imagenUrl', '/assets/productos/rota.png');

    el('app-imagen-panel img').dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No se pudo cargar la imagen');
    expect(el('#imagenUrl').value).toBe('/assets/productos/rota.png');
    expect(el('#imagenUrl').classList.contains('error')).toBe(false);
  });

  it('en edición la vista previa sale del producto cargado', async () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ProductoFormComponent],
      providers: [
        SessionService,
        SedeContextService,
        VentasService,
        ClienteService,
        ETagCacheService,
        ToastService,
        provideHttpClient(withInterceptors([gatewayInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '1' }) } },
        },
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), personaAdmin);
    TestBed.inject(SedeContextService).selectedId.set(1);
    const local = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductoFormComponent);
    fixture.detectChanges();
    await microtasks();
    local
      .match((r) => r.url === `${API_BASE_URL}/api/producto/buscar/1`)
      .forEach((req) =>
        req.flush(
          envelopeSingle(producto({ imagenUrl: '/assets/productos/kit-aseo.png' }))
        )
      );
    await microtasks();
    fixture.detectChanges();

    expect(el('#imagenUrl').value).toBe('/assets/productos/kit-aseo.png');
    expect(el('app-imagen-panel img').getAttribute('src')).toBe(
      '/assets/productos/kit-aseo.png'
    );
    local.verify();
  });
});
