import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, describe, expect, it } from 'vitest';
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
  envelopeSingle,
  habitacion,
  PERSONA_ADMIN,
} from '../../recepcion/testing/recepcion.fixtures';
import { hotel } from '../testing/catalogo.fixtures';
import { HabitacionDetalleComponent } from './habitacion-detalle.component';
import { HabitacionesListComponent } from './habitaciones-list.component';
import { SedesListComponent } from '../sedes/sedes-list.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

const GALERIA = [
  { url: '/assets/habitaciones/lima-centro-doble-1.jpg', alt: 'Dormitorio doble', orden: 1 },
  { url: '/assets/habitaciones/lima-centro-doble-2.jpg', alt: 'Ventanal del dormitorio', orden: 2 },
];

/**
 * [`08` §6](../../../../../docs/especificacion/08-admin-web.md#6-imágenes-en-el-panel): toda
 * miniatura lleva `alt`; la de adorno va `aria-hidden`; la carga usa un
 * esqueleto con la misma relación y el error cae al fallback neutro. Esta
 * prueba recorre las pantallas de imagen del catálogo y comprueba las tres
 * condiciones en el DOM pintado.
 */
describe('Estados de imagen de 08 §6 en el catálogo', () => {
  let http: HttpTestingController;

  function providers(extra: unknown[] = []) {
    return [
      SessionService,
      SedeContextService,
      CatalogoService,
      RecepcionService,
      ETagCacheService,
      ToastService,
      provideHttpClient(withInterceptors([gatewayInterceptor])),
      provideHttpClientTesting(),
      provideRouter([]),
      ...extra,
    ];
  }

  function preparar(extra: unknown[] = []): void {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: providers(extra) });
    TestBed.inject(SessionService).setSession(authData('Administrador'), PERSONA_ADMIN);
    TestBed.inject(SedeContextService).selectedId.set(1);
    http = TestBed.inject(HttpTestingController);
  }

  function imagenes(fixture: ComponentFixture<unknown>): HTMLImageElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('app-imagen-panel img')
    ) as HTMLImageElement[];
  }

  function esqueletos(fixture: ComponentFixture<unknown>): HTMLElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('.imagen-panel__esqueleto')
    ) as HTMLElement[];
  }

  /** Toda imagen tiene `alt` no vacío o es de adorno con `aria-hidden`. */
  function accesibles(lista: HTMLImageElement[]): boolean {
    return lista.every((img) => {
      const alt = img.getAttribute('alt') ?? '';
      const oculta = img.getAttribute('aria-hidden') === 'true';
      return oculta || alt.trim().length > 0;
    });
  }

  afterEach(() => http.verify());

  it('la miniatura de sede lleva alt, esqueleto con la relación y fallback en error', async () => {
    preparar();
    const fixture = TestBed.createComponent(SedesListComponent);
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/hotel/listar`)
      .flush(envelopePage([hotel()]));
    await microtasks();
    fixture.detectChanges();

    // Estado de carga: esqueleto de adorno con la misma relación reservada.
    const esqueletosAntes = esqueletos(fixture);
    expect(esqueletosAntes.length).toBe(1);
    expect(esqueletosAntes[0].getAttribute('aria-hidden')).toBe('true');
    const contenedor = (fixture.nativeElement as HTMLElement).querySelector(
      '.imagen-panel'
    ) as HTMLElement;
    expect(contenedor.style.aspectRatio).toBe('1 / 1');

    // Toda miniatura se puede anunciar: alt propio o `aria-hidden` si es de adorno.
    expect(accesibles(imagenes(fixture))).toBe(true);

    // Al cargar desaparece el esqueleto y la relación no cambia.
    imagenes(fixture)[0].dispatchEvent(new Event('load'));
    fixture.detectChanges();
    expect(esqueletos(fixture).length).toBe(0);
    expect(contenedor.style.aspectRatio).toBe('1 / 1');
  });

  it('la miniatura de habitación lleva alt y cae al fallback neutro', async () => {
    preparar();
    const fixture = TestBed.createComponent(HabitacionesListComponent);
    fixture.detectChanges();
    http
      .match((r) => r.url === `${API_BASE_URL}/api/habitacion/listar`)
      .forEach((req) =>
        req.flush(envelopePage([habitacion({ urlsImagenes: GALERIA })], 0, 20, 1))
      );
    http
      .match((r) => r.url === `${API_BASE_URL}/api/categoria/listar`)
      .forEach((req) => req.flush(envelopePage([], 0, 100, 0)));
    await microtasks();
    fixture.detectChanges();

    expect(accesibles(imagenes(fixture))).toBe(true);

    imagenes(fixture)[0].dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(imagenes(fixture)[0].getAttribute('src')).toBe(IMAGEN_FALLBACK);
  });

  it('la galería del detalle se anuncia con su nombre accesible', async () => {
    preparar([
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ id: '101' }) } },
      },
    ]);
    const fixture = TestBed.createComponent(HabitacionDetalleComponent);
    fixture.detectChanges();
    http
      .expectOne(`${API_BASE_URL}/api/habitacion/buscar/101`)
      .flush(envelopeSingle(habitacion({ urlsImagenes: GALERIA })));
    await microtasks();
    fixture.detectChanges();

    const region = (fixture.nativeElement as HTMLElement).querySelector('section.detalle');
    const titulo = (fixture.nativeElement as HTMLElement).querySelector('#galeria-titulo');
    expect(region?.getAttribute('aria-labelledby')).toBe('galeria-titulo');
    expect(titulo?.textContent).toContain('Galería de Habitación 101');
    expect(accesibles(imagenes(fixture))).toBe(true);
    // El resto de la galería se anuncia como lista.
    expect((fixture.nativeElement as HTMLElement).querySelector('.detalle__tira')?.getAttribute('aria-label')).toBe(
      'Resto de la galería en el orden guardado'
    );
  });
});
