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
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { correlationInterceptor } from '../core/correlation.interceptor';
import { GATEWAY_BASE_URL } from '../core/config';
import { CategoriaPublica, HotelPublico } from '../core/catalog.models';
import { SedeDetallePage } from './sede-detalle.page';
import { SedesPage } from './sedes.page';

registerLocaleData(localeEsPe);

const FOTO_SEDE = '/assets/sedes/lima-centro-fachada.jpg';
const ALT_FOTO_SEDE =
  'Fachada de piedra de la sede de Lima Centro al atardecer';

const HOTEL: HotelPublico = {
  idHotel: 1,
  slug: 'lima-centro',
  nombre: 'Casa Sillar Lima Centro',
  descripcion:
    'Casona restaurada a dos cuadras de la Plaza Mayor, con patio de sillar y recepción permanente.',
  direccion: 'Jr. de la Unión 540, Cercado de Lima',
  ciudad: 'Lima',
  telefono: '+51 1 428 6400',
  correoContacto: 'contacto@casasillar.pe',
  imagenUrl: FOTO_SEDE,
  imagenAlt: ALT_FOTO_SEDE,
};

const HOTEL_SIN_IMAGEN: HotelPublico = {
  ...HOTEL,
  imagenUrl: null,
  imagenAlt: null,
};

const CATEGORIAS: CategoriaPublica[] = [
  {
    idCategoria: 1,
    descripcion: 'Individual',
    detalle: 'Habitación individual con cama de una plaza, escritorio y baño privado.',
    capacidad: 1,
    precioDesde: 70,
    moneda: 'PEN',
    imagenes: [
      {
        url: '/assets/habitaciones/lima-centro-individual-1.jpg',
        alt: 'Habitación individual con cama de una plaza y cabecera de madera',
      },
      {
        url: '/assets/habitaciones/lima-centro-individual-2.jpg',
        alt: 'Escritorio y lámpara de lectura de la habitación individual',
      },
    ],
  },
  {
    idCategoria: 2,
    descripcion: 'Doble',
    detalle: 'Habitación doble con cama de dos plazas o dos camas.',
    capacidad: 2,
    precioDesde: 100,
    moneda: 'PEN',
    imagenes: [
      {
        url: '/assets/habitaciones/lima-centro-doble-1.jpg',
        alt: 'Habitación doble con dos camas y ventana a la calle',
      },
    ],
  },
];

describe('SedeDetallePage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SedeDetallePage, SedesPage],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([correlationInterceptor])),
        provideHttpClientTesting(),
        { provide: GATEWAY_BASE_URL, useValue: 'http://localhost:8080' },
        { provide: LOCALE_ID, useValue: 'es-PE' },
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ slug: 'lima-centro' })) },
        },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function crearPagina(hotel: HotelPublico = HOTEL): Promise<{
    fixture: import('@angular/core/testing').ComponentFixture<SedeDetallePage>;
    element: HTMLElement;
  }> {
    const fixture = TestBed.createComponent(SedeDetallePage);
    fixture.detectChanges();

    http
      .expectOne('http://localhost:8080/api/public/hoteles/lima-centro')
      .flush({ success: true, message: 'OK', data: hotel, meta: { correlationId: 'c' } });
    fixture.detectChanges();

    responderCategorias(CATEGORIAS);
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  function responderCategorias(data: CategoriaPublica[]) {
    http
      .expectOne(
        'http://localhost:8080/api/public/hoteles/lima-centro/categorias?page=0&size=20',
      )
      .flush({
        success: true,
        message: 'OK',
        data,
        meta: { page: 0, size: 20, totalElements: data.length },
      });
  }

  it('muestra la sede y solo las categorías publicadas del contrato', async () => {
    const { element } = await crearPagina();

    expect(element.querySelector('h1')?.textContent).toContain('Casa Sillar Lima Centro');
    expect(element.textContent).toContain('Jr. de la Unión 540');
    expect(element.querySelectorAll('.tarjeta-categoria').length).toBe(2);
    expect(element.textContent).toContain('Hasta 2 personas');
    expect(element.textContent).toContain('S/ 70.00');
    expect(element.textContent).toContain('S/ 100.00');
    expect(element.textContent).toContain('Consulta disponibilidad con recepción.');
    expect(element.textContent).not.toContain('demostración');
  });

  it('pinta la foto de la sede en 21:9 bajo el nombre y antes de los datos', async () => {
    const { element } = await crearPagina();
    const foto = element.querySelector('.cabecera-sede__foto img') as HTMLImageElement;

    expect(foto.getAttribute('src')).toBe(FOTO_SEDE);
    expect(foto.getAttribute('alt')).toBe(ALT_FOTO_SEDE);

    const cabecera = element.querySelector('.cabecera-sede') as HTMLElement;
    const orden = Array.from(cabecera.children).map((nodo) => nodo.className);
    expect(orden[0]).toContain('cabecera-sede__ciudad');
    expect(orden[1]).toBe(''); // h1
    expect(orden[2]).toContain('cabecera-sede__foto');
    expect(orden[3]).toContain('entradilla');
    expect(orden[4]).toContain('datos-sede');
  });

  it('usa la misma URL de foto que la ficha del listado de sedes', async () => {
    const { element: detalle } = await crearPagina();

    const listado = TestBed.createComponent(SedesPage);
    listado.detectChanges();
    http
      .expectOne('http://localhost:8080/api/public/hoteles?page=0&size=20')
      .flush({
        success: true,
        message: 'OK',
        data: [HOTEL],
        meta: { page: 0, size: 20, totalElements: 1 },
      });
    listado.detectChanges();
    await listado.whenStable();

    const fotoDetalle = detalle.querySelector(
      '.cabecera-sede__foto img',
    ) as HTMLImageElement;
    const fotoFicha = (listado.nativeElement as HTMLElement).querySelector(
      '.tarjeta-sede__imagen img',
    ) as HTMLImageElement;

    expect(fotoDetalle.getAttribute('src')).toBe(FOTO_SEDE);
    expect(fotoFicha.getAttribute('src')).toBe(FOTO_SEDE);
    expect(fotoDetalle.getAttribute('src')).toBe(fotoFicha.getAttribute('src'));
  });

  it('cae al fallback en la cabecera cuando la sede no tiene imagen', async () => {
    const { element } = await crearPagina(HOTEL_SIN_IMAGEN);
    const foto = element.querySelector('.cabecera-sede__foto img') as HTMLImageElement;

    expect(foto.getAttribute('src')).toBe('/assets/fallback.svg');
    expect(foto.getAttribute('alt')).toBe('Fachada de la sede Casa Sillar Lima Centro');
    expect(element.querySelector('.cabecera-sede__foto')).toBeTruthy();
  });

  it('usa la primera imagen de la categoría en la ficha, en 4:3', async () => {
    const { element } = await crearPagina();
    const imagenes = element.querySelectorAll(
      '.tarjeta-categoria__imagen img',
    ) as NodeListOf<HTMLImageElement>;

    expect(imagenes[0].getAttribute('src')).toBe(
      '/assets/habitaciones/lima-centro-individual-1.jpg',
    );
    expect(imagenes[0].getAttribute('alt')).toBe(
      'Habitación individual con cama de una plaza y cabecera de madera',
    );
    expect(element.querySelector('.tarjeta-categoria__imagen')).toBeTruthy();
  });

  it('usa el fallback en la ficha de categoría cuando la lista viene vacía', async () => {
    const fixture = TestBed.createComponent(SedeDetallePage);
    fixture.detectChanges();

    http
      .expectOne('http://localhost:8080/api/public/hoteles/lima-centro')
      .flush({ success: true, message: 'OK', data: HOTEL, meta: {} });
    fixture.detectChanges();

    responderCategorias([{ ...CATEGORIAS[0], imagenes: [] }]);
    fixture.detectChanges();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    const imagen = element.querySelector(
      '.tarjeta-categoria__imagen img',
    ) as HTMLImageElement;
    expect(imagen.getAttribute('src')).toBe('/assets/fallback.svg');
    expect(imagen.getAttribute('alt')).toContain('Imagen no disponible');
    expect(element.querySelector('.tarjeta-categoria__imagen')).toBeTruthy();
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
