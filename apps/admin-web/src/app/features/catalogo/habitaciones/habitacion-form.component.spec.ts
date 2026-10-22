import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { gatewayInterceptor } from '../../../core/gateway.interceptor';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { ETagCacheService } from '../../../core/services/etag-cache.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { IMAGEN_FALLBACK } from '../../../shared/imagen-panel.component';
import {
  authData,
  envelopePage,
  envelopeSingle,
  habitacion,
  PERSONA_ADMIN,
} from '../../recepcion/testing/recepcion.fixtures';
import { HabitacionFormComponent } from './habitacion-form.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

const GALERIA = [
  { url: '/assets/habitaciones/lima-centro-doble-1.jpg', alt: 'Dormitorio doble', orden: 1 },
  { url: '/assets/habitaciones/lima-centro-doble-2.jpg', alt: 'Ventanal del dormitorio', orden: 2 },
  { url: '/assets/habitaciones/lima-centro-doble-3.jpg', alt: 'Baño del dormitorio', orden: 3 },
];

describe('HabitacionFormComponent — vista previa de urlsImagenes', () => {
  let fixture: ComponentFixture<HabitacionFormComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [HabitacionFormComponent],
      providers: [
        SessionService,
        SedeContextService,
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
    TestBed.inject(SedeContextService).selectedId.set(1);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(HabitacionFormComponent);
    fixture.detectChanges();
  });

  afterEach(() => {
    http.match(() => true).forEach((req) => req.flush(envelopePage([], 0, 100, 0)));
    http.verify();
  });

  /**
   * `ngOnInit` encadena tres GET (categorías → habitación → pisos). Se
   * resuelven en el orden en que llegan, con la galería indicada.
   */
  async function cargarHabitacion(urlsImagenes = GALERIA): Promise<void> {
    for (let vuelta = 0; vuelta < 6; vuelta++) {
      await microtasks();
      const abiertas = http.match(() => true);
      if (abiertas.length === 0) break;
      for (const req of abiertas) {
        if (req.request.url.endsWith('/api/habitacion/buscar/1')) {
          req.flush(envelopeSingle(habitacion({ urlsImagenes })));
        } else {
          req.flush(envelopePage([], 0, 100, 0));
        }
      }
      fixture.detectChanges();
    }
    await microtasks();
    fixture.detectChanges();
  }

  function miniaturas(): HTMLImageElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.image-row app-imagen-panel img')
    ) as HTMLImageElement[];
  }

  it('pinta la miniatura de cada fila de urlsImagenes con su alt', async () => {
    await cargarHabitacion();

    expect(miniaturas().length).toBe(3);
    expect(miniaturas().map((i) => i.getAttribute('src'))).toEqual([
      '/assets/habitaciones/lima-centro-doble-1.jpg',
      '/assets/habitaciones/lima-centro-doble-2.jpg',
      '/assets/habitaciones/lima-centro-doble-3.jpg',
    ]);
    expect(miniaturas()[0].getAttribute('alt')).toBe('Dormitorio doble');
  });

  it('reordenar cambia el orden visual de las miniaturas', async () => {
    await cargarHabitacion();

    const subires = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.image-row__acciones button')
    ).filter((b) => (b.textContent ?? '').trim() === 'Subir') as HTMLButtonElement[];
    // El primer «Subir» habilitado pertenece a la segunda fila.
    const boton = subires.find((b) => !b.disabled) as HTMLButtonElement;
    boton.click();
    fixture.detectChanges();

    expect(miniaturas().map((i) => i.getAttribute('src'))).toEqual([
      '/assets/habitaciones/lima-centro-doble-2.jpg',
      '/assets/habitaciones/lima-centro-doble-1.jpg',
      '/assets/habitaciones/lima-centro-doble-3.jpg',
    ]);
  });

  it('una URL rota muestra el fallback, el texto junto al campo y conserva el valor', async () => {
    await cargarHabitacion([
      { url: '/assets/habitaciones/inexistente.jpg', alt: 'Dormitorio', orden: 1 },
    ]);

    miniaturas()[0].dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(miniaturas()[0].getAttribute('src')).toBe(IMAGEN_FALLBACK);
    expect(fixture.nativeElement.textContent).toContain('No se pudo cargar la imagen');
    const input = (fixture.nativeElement as HTMLElement).querySelector(
      '#imagen-url-0'
    ) as HTMLInputElement;
    expect(input.value).toBe('/assets/habitaciones/inexistente.jpg');
  });

  it('con una URL inválida no pinta miniatura y deja el hueco neutro', async () => {
    await cargarHabitacion([{ url: 'http://ejemplo.test/foto.jpg', alt: 'Dormitorio', orden: 1 }]);

    expect(miniaturas().length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('Sin vista previa');
  });

  it('sin imágenes muestra el vacío del formulario', async () => {
    await cargarHabitacion([]);

    expect(miniaturas().length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('Sin imágenes');
  });
});
