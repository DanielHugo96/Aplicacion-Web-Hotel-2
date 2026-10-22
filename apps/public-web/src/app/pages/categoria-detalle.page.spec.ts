import { registerLocaleData } from '@angular/common';
import localeEsPe from '@angular/common/locales/es-PE';
import {
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { LOCALE_ID } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRoute,
  convertToParamMap,
  provideRouter,
} from '@angular/router';
import { of } from 'rxjs';
import { correlationInterceptor } from '../core/correlation.interceptor';
import { GATEWAY_BASE_URL } from '../core/config';
import { CategoriaPublica } from '../core/catalog.models';
import { CategoriaDetallePage } from './categoria-detalle.page';

registerLocaleData(localeEsPe);

const IMAGENES = [
  {
    url: '/assets/habitaciones/lima-centro-individual-1.jpg',
    alt: 'Habitación individual con cama de una plaza y cabecera de madera',
  },
  {
    url: '/assets/habitaciones/lima-centro-individual-2.jpg',
    alt: 'Escritorio y lámpara de lectura de la habitación individual',
  },
  {
    url: '/assets/habitaciones/lima-centro-individual-3.jpg',
    alt: 'Baño privado con ducha de la habitación individual',
  },
  {
    url: '/assets/habitaciones/lima-centro-doble-1.jpg',
    alt: 'Quinta imagen que la galería no debe mostrar',
  },
  {
    url: '/assets/habitaciones/lima-centro-doble-2.jpg',
    alt: 'Sexta imagen que la galería no debe mostrar',
  },
];

const CATEGORIA: CategoriaPublica = {
  idCategoria: 1,
  descripcion: 'Individual',
  detalle: 'Habitación individual con cama de una plaza, escritorio y baño privado.',
  capacidad: 1,
  precioDesde: 70,
  moneda: 'PEN',
  imagenes: IMAGENES,
};

const SIN_IMAGENES: CategoriaPublica = { ...CATEGORIA, imagenes: [] };

describe('CategoriaDetallePage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CategoriaDetallePage],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([correlationInterceptor])),
        provideHttpClientTesting(),
        { provide: GATEWAY_BASE_URL, useValue: 'http://localhost:8080' },
        { provide: LOCALE_ID, useValue: 'es-PE' },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(
              convertToParamMap({
                slug: 'lima-centro',
                idCategoria: '1',
              }),
            ),
            snapshot: { paramMap: convertToParamMap({ idCategoria: '1' }) },
          },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function crear(categoria: CategoriaPublica = CATEGORIA) {
    const fixture = TestBed.createComponent(CategoriaDetallePage);
    fixture.detectChanges();

    http
      .expectOne(
        'http://localhost:8080/api/public/hoteles/lima-centro/categorias/1',
      )
      .flush({
        success: true,
        message: 'OK',
        data: categoria,
        meta: { correlationId: 'c-1' },
      });
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('muestra hasta cuatro imágenes, capacidad y el importe sin recalcular', async () => {
    const { element } = await crear();

    expect(element.querySelector('h1')?.textContent).toContain('Individual');
    expect(element.querySelectorAll('.galeria__miniatura').length).toBe(4);
    expect(element.querySelector('.galeria__principal img')?.getAttribute('src')).toBe(
      IMAGENES[0].url,
    );
    expect(element.querySelector('.galeria__principal img')?.getAttribute('alt')).toBe(
      IMAGENES[0].alt,
    );
    expect(element.textContent).toContain('Hasta 1 persona');
    expect(element.querySelector('.tarifa__importe')?.textContent).toContain(
      'S/ 70.00',
    );
    expect(element.textContent).toContain(
      'Consulta disponibilidad con recepción. No constituye una reserva.',
    );
    expect(element.textContent).not.toContain('Quinta imagen');
  });

  it('cambia la imagen principal al elegir una miniatura, sin recargar', async () => {
    const { fixture, element } = await crear();
    const miniaturas = Array.from(
      element.querySelectorAll('.galeria__miniatura'),
    ) as HTMLButtonElement[];

    miniaturas[2].click();
    fixture.detectChanges();

    expect(element.querySelector('.galeria__principal img')?.getAttribute('src')).toBe(
      IMAGENES[2].url,
    );
    expect(element.querySelector('.galeria__principal img')?.getAttribute('alt')).toBe(
      IMAGENES[2].alt,
    );
    expect(miniaturas[2].getAttribute('aria-pressed')).toBe('true');
    expect(miniaturas[0].getAttribute('aria-pressed')).toBe('false');
    http.expectNone(
      'http://localhost:8080/api/public/hoteles/lima-centro/categorias/1',
    );
  });

  it('no muestra miniaturas cuando solo hay una imagen', async () => {
    const { element } = await crear({ ...CATEGORIA, imagenes: [IMAGENES[0]] });

    expect(element.querySelector('.galeria__principal img')?.getAttribute('src')).toBe(
      IMAGENES[0].url,
    );
    expect(element.querySelectorAll('.galeria__miniatura').length).toBe(0);
  });

  it('usa el fallback ocupando el hueco de la principal si la galería viene vacía', async () => {
    const { element } = await crear(SIN_IMAGENES);
    const principal = element.querySelector(
      '.galeria__principal img',
    ) as HTMLImageElement;

    expect(principal.getAttribute('src')).toBe('/assets/fallback.svg');
    expect(principal.getAttribute('alt')).toContain('Imagen no disponible');
    expect(element.querySelectorAll('.galeria__miniatura').length).toBe(0);
    expect(element.querySelector('.galeria__principal')).toBeTruthy();
  });

  it('muestra el 404 del catálogo con enlace de vuelta al listado', async () => {
    const fixture = TestBed.createComponent(CategoriaDetallePage);
    fixture.detectChanges();

    http
      .expectOne(
        'http://localhost:8080/api/public/hoteles/lima-centro/categorias/1',
      )
      .flush(null, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain('No encontramos lo que buscas.');
    expect(element.querySelector('a[href="/sedes"]')).toBeTruthy();
  });
});
