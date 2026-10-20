import {
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { correlationInterceptor } from '../core/correlation.interceptor';
import { GATEWAY_BASE_URL } from '../core/config';
import { CategoriaPublica, HotelPublico } from '../core/catalog.models';
import { SedeDetallePage } from './sede-detalle.page';

const HOTEL: HotelPublico = {
  idHotel: 1,
  slug: 'lima-centro',
  nombre: 'Hotel Demo Lima Centro',
  descripcion: 'Sede de demostración',
  direccion: 'Dirección de demostración',
  ciudad: 'Lima',
  telefono: '+51 900 000 001',
  correoContacto: 'contacto@hotel.test',
};

const CATEGORIAS: CategoriaPublica[] = [
  {
    idCategoria: 1,
    descripcion: 'Individual',
    detalle: 'Habitación individual de demostración',
    capacidad: 1,
    precioDesde: 70,
    moneda: 'PEN',
    imagenes: [
      { url: '/assets/demo/hab-101-1.jpg', alt: 'Habitación de demostración' },
    ],
  },
  {
    idCategoria: 2,
    descripcion: 'Doble',
    detalle: 'Habitación doble de demostración',
    capacidad: 2,
    precioDesde: 100,
    moneda: 'PEN',
    imagenes: [
      { url: '/assets/demo/hab-102-1.jpg', alt: 'Habitación de demostración' },
    ],
  },
];

describe('SedeDetallePage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SedeDetallePage],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([correlationInterceptor])),
        provideHttpClientTesting(),
        { provide: GATEWAY_BASE_URL, useValue: 'http://localhost:8080' },
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ slug: 'lima-centro' })) },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function crearPagina(): Promise<{
    fixture: import('@angular/core/testing').ComponentFixture<SedeDetallePage>;
    element: HTMLElement;
  }> {
    const fixture = TestBed.createComponent(SedeDetallePage);
    fixture.detectChanges();

    http
      .expectOne('http://localhost:8080/api/public/hoteles/lima-centro')
      .flush({ success: true, message: 'OK', data: HOTEL, meta: { correlationId: 'c' } });
    fixture.detectChanges();

    http
      .expectOne(
        'http://localhost:8080/api/public/hoteles/lima-centro/categorias?page=0&size=20',
      )
      .flush({
        success: true,
        message: 'OK',
        data: CATEGORIAS,
        meta: { page: 0, size: 20, totalElements: 2 },
      });
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('muestra la sede y solo las categorías publicadas del contrato', async () => {
    const { element } = await crearPagina();

    expect(element.querySelector('h1')?.textContent).toContain('Hotel Demo Lima Centro');
    expect(element.textContent).toContain('Dirección de demostración');
    expect(element.querySelectorAll('.tarjeta-categoria').length).toBe(2);
    expect(element.textContent).toContain('Hasta 2 personas');
    expect(element.textContent).toContain('Consulta disponibilidad con recepción.');
  });

  it('acepta el filtro de capacidad 1..10 y lo envía al backend', async () => {
    const { fixture, element } = await crearPagina();
    inputCapacidad(element, '2');
    fixtureSubmit(element);
    fixture.detectChanges();

    const request = http.expectOne(
      'http://localhost:8080/api/public/hoteles/lima-centro/categorias?page=0&size=20&capacidad=2',
    );
    request.flush({
      success: true,
      message: 'OK',
      data: [CATEGORIAS[1]],
      meta: { page: 0, size: 20, totalElements: 1 },
    });
    fixture.detectChanges();
    await fixture.whenStable();
    expect(element.querySelectorAll('.tarjeta-categoria').length).toBe(1);
  });

  it('rechaza capacidad fuera de rango como validación sin llamar al backend', async () => {
    const { fixture, element } = await crearPagina();
    inputCapacidad(element, '11');
    fixtureSubmit(element);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(element.querySelector('#capacidad-error')?.textContent).toContain(
      'La capacidad debe estar entre 1 y 10.',
    );
    http.expectNone(
      'http://localhost:8080/api/public/hoteles/lima-centro/categorias?page=0&size=20&capacidad=11',
    );
  });

  it('muestra el 404 del catálogo con enlace de vuelta', async () => {
    const fixture = TestBed.createComponent(SedeDetallePage);
    fixture.detectChanges();

    http
      .expectOne('http://localhost:8080/api/public/hoteles/lima-centro')
      .flush(null, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain('No encontramos lo que buscas.');
    expect(element.querySelector('a[href="/sedes"]')).toBeTruthy();
  });
});

function inputCapacidad(element: HTMLElement, value: string): void {
  const input = element.querySelector('#capacidad') as HTMLInputElement;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function fixtureSubmit(element: HTMLElement): void {
  const form = element.querySelector('form.filtro') as HTMLFormElement;
  form.dispatchEvent(new Event('submit'));
}
