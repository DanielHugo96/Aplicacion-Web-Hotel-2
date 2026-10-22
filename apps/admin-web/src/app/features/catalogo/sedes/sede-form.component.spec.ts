import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { gatewayInterceptor } from '../../../core/gateway.interceptor';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { ETagCacheService } from '../../../core/services/etag-cache.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  authData,
  envelopeSingle,
  PERSONA_ADMIN,
} from '../../recepcion/testing/recepcion.fixtures';
import { hotel } from '../testing/catalogo.fixtures';
import { SedeFormComponent } from './sede-form.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

describe('SedeFormComponent — alta con imagenUrl e imagenAlt', () => {
  let fixture: ComponentFixture<SedeFormComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SedeFormComponent],
      providers: [
        SessionService,
        CatalogoService,
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
    TestBed.inject(SessionService).setSession(authData('Administrador'), PERSONA_ADMIN);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SedeFormComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
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

  function rellenarObligatorios(): void {
    escribir('#slug', 'lima-centro');
    escribir('#nombre', 'Casa Sillar Lima Centro');
    escribir('#direccion', 'Jr. de la Unión 540');
    escribir('#ciudad', 'Lima');
    escribir('#correoContacto', 'contacto@casasillar.pe');
  }

  async function enviar(): Promise<void> {
    (fixture.nativeElement as HTMLElement)
      .querySelector('form')
      ?.dispatchEvent(new Event('submit'));
    await microtasks();
    fixture.detectChanges();
  }

  it('una URL http:// marca imagenUrl con «URL no permitida» y no envía', async () => {
    rellenarObligatorios();
    escribir('#imagenUrl', 'http://ejemplo.test/foto.jpg');
    escribir('#imagenAlt', 'Fachada de la sede');
    await enviar();

    expect(http.match(`${API_BASE_URL}/api/hotel/registrar`).length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('URL no permitida');
    expect(el('#imagenUrl').classList.contains('error')).toBe(true);
  });

  it('una URL válida sin alt marca imagenAlt con su mensaje y no envía', async () => {
    rellenarObligatorios();
    escribir('#imagenUrl', '/assets/sedes/lima-centro-fachada.jpg');
    await enviar();

    expect(http.match(`${API_BASE_URL}/api/hotel/registrar`).length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('Describe la imagen para accesibilidad');
    expect(el('#imagenAlt').classList.contains('error')).toBe(true);
  });

  it('la vista previa aparece con una URL válida y desaparece con una inválida', () => {
    expect(fixture.nativeElement.querySelector('app-imagen-panel')).toBeNull();

    escribir('#imagenUrl', 'http://ejemplo.test/foto.jpg');
    expect(fixture.nativeElement.querySelector('app-imagen-panel')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Sin vista previa');

    escribir('#imagenUrl', '/assets/sedes/arequipa-fachada.jpg');
    const img = el('app-imagen-panel img');
    expect(img.getAttribute('src')).toBe('/assets/sedes/arequipa-fachada.jpg');
    expect(img.getAttribute('loading')).toBe('lazy');
  });

  it('una URL de imagen rota muestra el texto junto al campo, conserva el valor y guarda', async () => {
    rellenarObligatorios();
    escribir('#imagenUrl', '/assets/sedes/inexistente.jpg');
    escribir('#imagenAlt', 'Fachada de la sede de prueba');

    el('app-imagen-panel img').dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No se pudo cargar la imagen');
    expect(el('#imagenUrl').value).toBe('/assets/sedes/inexistente.jpg');
    expect(el('#imagenUrl').classList.contains('error')).toBe(false);

    await enviar();
    const req = http.expectOne(`${API_BASE_URL}/api/hotel/registrar`);
    expect(req.request.body.imagenUrl).toBe('/assets/sedes/inexistente.jpg');
    expect(req.request.body.imagenAlt).toBe('Fachada de la sede de prueba');
    req.flush(envelopeSingle(hotel()));
    await microtasks();
  });

  it('sin URL de imagen envía el par nulo', async () => {
    rellenarObligatorios();
    await enviar();

    const req = http.expectOne(`${API_BASE_URL}/api/hotel/registrar`);
    expect(req.request.body.imagenUrl).toBeNull();
    expect(req.request.body.imagenAlt).toBeNull();
    req.flush(envelopeSingle(hotel({ imagenUrl: null, imagenAlt: null })));
    await microtasks();
  });
});

describe('SedeFormComponent — edición con el par de imagen', () => {
  let fixture: ComponentFixture<SedeFormComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SedeFormComponent],
      providers: [
        SessionService,
        CatalogoService,
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
    TestBed.inject(SessionService).setSession(authData('Administrador'), PERSONA_ADMIN);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(SedeFormComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
  });

  it('carga el par de imagen de la sede y su vista previa', async () => {
    http
      .expectOne(`${API_BASE_URL}/api/hotel/buscar/1`)
      .flush(envelopeSingle(hotel()));
    await microtasks();
    fixture.detectChanges();

    const url = (fixture.nativeElement as HTMLElement).querySelector(
      '#imagenUrl'
    ) as HTMLInputElement;
    const alt = (fixture.nativeElement as HTMLElement).querySelector(
      '#imagenAlt'
    ) as HTMLInputElement;

    expect(url.value).toBe('/assets/sedes/lima-centro-fachada.jpg');
    expect(alt.value).toBe('Fachada de piedra de la sede de Lima Centro al atardecer');
    expect(
      (fixture.nativeElement as HTMLElement)
        .querySelector('app-imagen-panel img')
        ?.getAttribute('src')
    ).toBe('/assets/sedes/lima-centro-fachada.jpg');
  });
});
