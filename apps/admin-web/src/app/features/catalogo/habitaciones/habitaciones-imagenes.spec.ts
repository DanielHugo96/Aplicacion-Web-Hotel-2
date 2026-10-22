import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { gatewayInterceptor } from '../../../core/gateway.interceptor';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { ETagCacheService } from '../../../core/services/etag-cache.service';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { IMAGEN_FALLBACK } from '../../../shared/imagen-panel.component';
import {
  authData,
  envelopePage,
  habitacion,
  PERSONA_ADMIN,
} from '../../recepcion/testing/recepcion.fixtures';
import { HabitacionesListComponent } from './habitaciones-list.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

const GALERIA = [
  { url: '/assets/habitaciones/lima-centro-doble-2.jpg', alt: 'Dormitorio doble', orden: 2 },
  { url: '/assets/habitaciones/lima-centro-doble-1.jpg', alt: 'Vista del dormitorio doble', orden: 1 },
  { url: '/assets/habitaciones/lima-centro-doble-3.jpg', alt: 'Baño del dormitorio doble', orden: 3 },
];

describe('HabitacionesListComponent — miniatura de la galería', () => {
  let fixture: ComponentFixture<HabitacionesListComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HabitacionesListComponent],
      providers: [
        SessionService,
        SedeContextService,
        CatalogoService,
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
  });

  afterEach(() => {
    http.verify();
  });

  async function cargar(habitaciones = [habitacion()]): Promise<void> {
    fixture = TestBed.createComponent(HabitacionesListComponent);
    fixture.detectChanges();
    http
      .match((r) => r.url === `${API_BASE_URL}/api/habitacion/listar`)
      .forEach((req) => req.flush(envelopePage(habitaciones, 0, 20, habitaciones.length)));
    http
      .match((r) => r.url === `${API_BASE_URL}/api/categoria/listar`)
      .forEach((req) => req.flush(envelopePage([], 0, 100, 0)));
    await microtasks();
    fixture.detectChanges();
  }

  function miniaturas(): HTMLImageElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody app-imagen-panel img')
    ) as HTMLImageElement[];
  }

  it('muestra la primera imagen por orden de una galería de tres', async () => {
    await cargar([habitacion({ urlsImagenes: GALERIA })]);

    expect(miniaturas().length).toBe(1);
    expect(miniaturas()[0].getAttribute('src')).toBe(
      '/assets/habitaciones/lima-centro-doble-1.jpg'
    );
    expect(miniaturas()[0].getAttribute('alt')).toBe('Vista del dormitorio doble');
  });

  it('con la galería vacía muestra el fallback neutro de adorno', async () => {
    await cargar([habitacion({ urlsImagenes: [] })]);

    expect(miniaturas()[0].getAttribute('src')).toBe(IMAGEN_FALLBACK);
    expect(miniaturas()[0].getAttribute('aria-hidden')).toBe('true');
  });

  it('enlaza el detalle de la habitación desde el listado', async () => {
    await cargar();
    const botones = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody button')
    ) as HTMLButtonElement[];
    const detalle = botones.find((b) => (b.textContent ?? '').includes('Ver detalle'));
    expect(detalle).toBeDefined();
  });
});
