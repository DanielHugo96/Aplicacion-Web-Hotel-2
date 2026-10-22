import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { gatewayInterceptor } from '../../../core/gateway.interceptor';
import { ClienteService } from '../../../core/services/cliente.service';
import { ETagCacheService } from '../../../core/services/etag-cache.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { VentasService } from '../../../core/services/ventas.service';
import { IMAGEN_FALLBACK } from '../../../shared/imagen-panel.component';
import {
  authData,
  envelopePage,
  personaAdmin,
  producto,
} from '../testing/ventas.fixtures';
import { ProductosListComponent } from './productos-list.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

describe('ProductosListComponent — miniatura del producto', () => {
  let fixture: ComponentFixture<ProductosListComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ProductosListComponent],
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
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), personaAdmin);
    TestBed.inject(SedeContextService).selectedId.set(1);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ProductosListComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
  });

  async function cargar(productos = [producto()]): Promise<void> {
    http
      .match((r) => r.url === `${API_BASE_URL}/api/producto/listar`)
      .forEach((req) => req.flush(envelopePage(productos)));
    await microtasks();
    fixture.detectChanges();
  }

  function miniaturas(): HTMLImageElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody app-imagen-panel img')
    ) as HTMLImageElement[];
  }

  it('muestra la miniatura con el nombre del producto como alt', async () => {
    await cargar([producto({ imagenUrl: '/assets/productos/agua-500ml.png' })]);

    expect(miniaturas().length).toBe(1);
    expect(miniaturas()[0].getAttribute('src')).toBe('/assets/productos/agua-500ml.png');
    expect(miniaturas()[0].getAttribute('alt')).toBe('Producto Agua 500ml');
  });

  it('sin imagenUrl pinta el fallback neutro de adorno', async () => {
    await cargar([producto({ imagenUrl: null })]);

    expect(miniaturas()[0].getAttribute('src')).toBe(IMAGEN_FALLBACK);
    expect(miniaturas()[0].getAttribute('aria-hidden')).toBe('true');
  });

  it('una imagen rota cae al fallback y conserva el resto de la fila', async () => {
    await cargar([producto({ imagenUrl: '/assets/productos/rota.png' })]);
    miniaturas()[0].dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(miniaturas()[0].getAttribute('src')).toBe(IMAGEN_FALLBACK);
    expect(fixture.nativeElement.textContent).toContain('AGUA-500');
    expect(fixture.nativeElement.textContent).toContain('Agua 500ml');
  });
});
