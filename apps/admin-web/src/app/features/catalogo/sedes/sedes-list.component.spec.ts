import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { gatewayInterceptor } from '../../../core/gateway.interceptor';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { ETagCacheService } from '../../../core/services/etag-cache.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { IMAGEN_FALLBACK } from '../../../shared/imagen-panel.component';
import {
  authData,
  envelopePage,
  PERSONA_ADMIN,
} from '../../recepcion/testing/recepcion.fixtures';
import { hotel } from '../testing/catalogo.fixtures';
import { SedesListComponent } from './sedes-list.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

describe('SedesListComponent — miniatura de la sede', () => {
  let fixture: ComponentFixture<SedesListComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SedesListComponent],
      providers: [
        SessionService,
        CatalogoService,
        ETagCacheService,
        ToastService,
        provideHttpClient(withInterceptors([gatewayInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), PERSONA_ADMIN);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  async function cargar(sedes = [hotel()]): Promise<void> {
    fixture = TestBed.createComponent(SedesListComponent);
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/hotel/listar`)
      .flush(envelopePage(sedes));
    await microtasks();
    fixture.detectChanges();
  }

  function miniaturas(): HTMLImageElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody app-imagen-panel img')
    ) as HTMLImageElement[];
  }

  it('pinta la miniatura de la sede junto al nombre con su alt del contrato', async () => {
    await cargar([hotel()]);

    expect(miniaturas().length).toBe(1);
    expect(miniaturas()[0].getAttribute('src')).toBe('/assets/sedes/lima-centro-fachada.jpg');
    expect(miniaturas()[0].getAttribute('alt')).toBe(
      'Fachada de piedra de la sede de Lima Centro al atardecer'
    );
    expect((fixture.nativeElement as HTMLElement).querySelector('tbody td')?.textContent).toContain(
      'Casa Sillar Lima Centro'
    );
  });

  it('sin imagenUrl pinta el fallback neutro de adorno, nunca un icono roto', async () => {
    await cargar([hotel({ imagenUrl: null, imagenAlt: null })]);

    const img = miniaturas()[0];
    expect(img.getAttribute('src')).toBe(IMAGEN_FALLBACK);
    expect(img.getAttribute('aria-hidden')).toBe('true');
    expect(img.getAttribute('alt')).toBe('');
  });

  it('si la imagen de la sede falla cae al fallback sin alterar el nombre', async () => {
    await cargar([hotel()]);
    const img = miniaturas()[0];
    img.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(img.getAttribute('src')).toBe(IMAGEN_FALLBACK);
    expect((fixture.nativeElement as HTMLElement).querySelector('tbody td')?.textContent).toContain(
      'Casa Sillar Lima Centro'
    );
  });
});
