import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { gatewayInterceptor } from '../../../core/gateway.interceptor';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { ETagCacheService } from '../../../core/services/etag-cache.service';
import { SessionService } from '../../../core/services/session.service';
import { IMAGEN_FALLBACK } from '../../../shared/imagen-panel.component';
import {
  authData,
  envelopeSingle,
  habitacion,
  PERSONA_ADMIN,
} from '../../recepcion/testing/recepcion.fixtures';
import { HabitacionDetalleComponent } from './habitacion-detalle.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

const GALERIA_DESORDENADA = [
  { url: '/assets/habitaciones/lima-centro-doble-3.jpg', alt: 'Baño del dormitorio', orden: 3 },
  { url: '/assets/habitaciones/lima-centro-doble-1.jpg', alt: 'Dormitorio doble', orden: 1 },
  { url: '/assets/habitaciones/lima-centro-doble-2.jpg', alt: 'Ventanal del dormitorio', orden: 2 },
];

describe('HabitacionDetalleComponent — galería completa', () => {
  let fixture: ComponentFixture<HabitacionDetalleComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [HabitacionDetalleComponent],
      providers: [
        SessionService,
        CatalogoService,
        ETagCacheService,
        provideHttpClient(withInterceptors([gatewayInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '101' }) } },
        },
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), PERSONA_ADMIN);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HabitacionDetalleComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.verify();
  });

  async function cargar(urlsImagenes = GALERIA_DESORDENADA): Promise<void> {
    http
      .expectOne(`${API_BASE_URL}/api/habitacion/buscar/101`)
      .flush(envelopeSingle(habitacion({ urlsImagenes })));
    await microtasks();
    fixture.detectChanges();
  }

  function imagenes(): HTMLImageElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('app-imagen-panel img')
    ) as HTMLImageElement[];
  }

  it('pinta la galería completa en el orden guardado, no en el del array', async () => {
    await cargar();

    expect(imagenes().map((i) => i.getAttribute('src'))).toEqual([
      '/assets/habitaciones/lima-centro-doble-1.jpg',
      '/assets/habitaciones/lima-centro-doble-2.jpg',
      '/assets/habitaciones/lima-centro-doble-3.jpg',
    ]);
    expect(imagenes().map((i) => i.getAttribute('alt'))).toEqual([
      'Dormitorio doble',
      'Ventanal del dormitorio',
      'Baño del dormitorio',
    ]);
  });

  it('la imagen principal carga con prioridad alta y el resto es diferida', async () => {
    await cargar();

    expect(imagenes()[0].getAttribute('loading')).toBe('eager');
    expect(imagenes()[0].getAttribute('fetchpriority')).toBe('high');
    for (const diferida of imagenes().slice(1)) {
      expect(diferida.getAttribute('loading')).toBe('lazy');
      expect(diferida.getAttribute('fetchpriority')).toBe('auto');
    }
  });

  it('reserva una relación fija en cada imagen de la galería', async () => {
    await cargar();

    const relaciones = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>('.imagen-panel')
    ).map((el) => el.style.aspectRatio);
    expect(relaciones.length).toBe(3);
    expect(new Set(relaciones)).toEqual(new Set(['4 / 3']));
  });

  it('sin galería explica el vacío y no deja un hueco gris sin texto', async () => {
    await cargar([]);

    expect(imagenes().length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('Aún no hay imágenes en la galería');
  });

  it('una imagen rota cae al fallback neutro sin perder el alt', async () => {
    await cargar([{ url: '/assets/habitaciones/inexistente.jpg', alt: 'Dormitorio', orden: 1 }]);

    imagenes()[0].dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(imagenes()[0].getAttribute('src')).toBe(IMAGEN_FALLBACK);
    expect(imagenes()[0].getAttribute('alt')).toBe('Dormitorio');
  });

  it('el detalle es accesible como galería con su nombre', async () => {
    await cargar();

    const titulo = (fixture.nativeElement as HTMLElement).querySelector('#galeria-titulo');
    expect(titulo?.textContent).toContain('Galería de Habitación 101');
  });
});
