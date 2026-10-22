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

const CATEGORIA: CategoriaPublica = {
  idCategoria: 1,
  descripcion: 'Individual',
  detalle: 'Habitación individual de demostración',
  capacidad: 1,
  precioDesde: 70,
  moneda: 'PEN',
  imagenes: [
    { url: '/assets/habitaciones/lima-centro-individual-1.jpg', alt: 'Habitación de demostración' },
  ],
};

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

  it('muestra galería, capacidad y el importe del backend sin recalcular', async () => {
    const fixture = TestBed.createComponent(CategoriaDetallePage);
    fixture.detectChanges();

    http
      .expectOne(
        'http://localhost:8080/api/public/hoteles/lima-centro/categorias/1',
      )
      .flush({
        success: true,
        message: 'OK',
        data: CATEGORIA,
        meta: { correlationId: 'c-1' },
      });
    fixture.detectChanges();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent).toContain('Individual');
    expect(element.querySelectorAll('app-catalog-image').length).toBe(1);
    expect(element.querySelector('img')?.getAttribute('alt')).toBe(
      'Habitación de demostración',
    );
    expect(element.textContent).toContain('Hasta 1 persona');
    expect(element.querySelector('.tarifa__importe')?.textContent).toContain(
      'S/ 70.00',
    );
    expect(element.textContent).toContain(
      'Consulta disponibilidad con recepción. No constituye una reserva.',
    );
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
