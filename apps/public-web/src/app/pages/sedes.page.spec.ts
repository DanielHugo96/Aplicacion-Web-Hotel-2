import {
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { correlationInterceptor } from '../core/correlation.interceptor';
import { GATEWAY_BASE_URL } from '../core/config';
import { HotelPublico } from '../core/catalog.models';
import { SedesPage } from './sedes.page';

const HOTELES: HotelPublico[] = [
  {
    idHotel: 1,
    slug: 'lima-centro',
    nombre: 'Casa Sillar Lima Centro',
    descripcion:
      'Casona restaurada a dos cuadras de la Plaza Mayor, con patio de sillar.',
    direccion: 'Jr. de la Unión 540, Cercado de Lima',
    ciudad: 'Lima',
    telefono: '+51 1 428 6400',
    correoContacto: 'contacto@casasillar.pe',
    imagenUrl: '/assets/sedes/lima-centro-fachada.jpg',
    imagenAlt: 'Fachada de piedra de la sede de Lima Centro al atardecer',
  },
  {
    idHotel: 3,
    slug: 'arequipa',
    nombre: 'Casa Sillar Arequipa',
    descripcion: 'Casona de sillar en el centro histórico de Arequipa.',
    direccion: 'Calle Mercaderes 218, Cercado de Arequipa',
    ciudad: 'Arequipa',
    telefono: '+51 54 216 800',
    correoContacto: 'contacto@casasillar.pe',
    imagenUrl: null,
    imagenAlt: null,
  },
];

describe('SedesPage', () => {
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SedesPage],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([correlationInterceptor])),
        provideHttpClientTesting(),
        { provide: GATEWAY_BASE_URL, useValue: 'http://localhost:8080' },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function responder(data: HotelPublico[], total = data.length) {
    http
      .expectOne('http://localhost:8080/api/public/hoteles?page=0&size=20')
      .flush({
        success: true,
        message: 'OK',
        data,
        meta: { page: 0, size: 20, totalElements: total, correlationId: 'c-1' },
      });
  }

  it('pinta solo las sedes que devuelve el catálogo paginado', async () => {
    const fixture = TestBed.createComponent(SedesPage);
    fixture.detectChanges();

    responder(HOTELES);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('.tarjeta-sede').length).toBe(2);
    expect(element.textContent).toContain('Casa Sillar Lima Centro');
    expect(element.textContent).toContain('Casa Sillar Arequipa');
    expect(element.querySelector('.paginacion__estado')?.textContent).toContain(
      'Página 1 de 1 · 2 registros',
    );
    expect(element.textContent).not.toContain('demostración');
  });

  it('muestra imagenUrl y su alt en la ficha de la sede', async () => {
    const fixture = TestBed.createComponent(SedesPage);
    fixture.detectChanges();

    responder(HOTELES);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    const imagenes = Array.from(
      element.querySelectorAll('.tarjeta-sede__imagen img'),
    ) as HTMLImageElement[];

    expect(imagenes[0].getAttribute('src')).toBe(
      '/assets/sedes/lima-centro-fachada.jpg',
    );
    expect(imagenes[0].getAttribute('alt')).toBe(
      'Fachada de piedra de la sede de Lima Centro al atardecer',
    );
    expect(imagenes[0].getAttribute('loading')).toBe('lazy');
  });

  it('cae al fallback con la misma relación cuando la sede no tiene imagen', async () => {
    const fixture = TestBed.createComponent(SedesPage);
    fixture.detectChanges();

    responder(HOTELES);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    const fichas = element.querySelectorAll('.tarjeta-sede__imagen');
    const imagenes = element.querySelectorAll(
      '.tarjeta-sede__imagen img',
    ) as NodeListOf<HTMLImageElement>;

    expect(imagenes.length).toBe(2);
    expect(imagenes[1].getAttribute('src')).toBe('/assets/fallback.svg');
    expect(imagenes[1].getAttribute('alt')).toBe(
      'Fachada de la sede Casa Sillar Arequipa',
    );
    // La relación la declara el contenedor, no el fallo de la imagen.
    expect(fichas.length).toBe(2);
    expect(element.querySelector('.tarjeta-sede__imagen')).toBeTruthy();
  });

  it('cae al fallback cuando la imagen de la sede falla al cargar', async () => {
    const fixture = TestBed.createComponent(SedesPage);
    fixture.detectChanges();

    responder(HOTELES);
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    const imagen = element.querySelector(
      '.tarjeta-sede__imagen img',
    ) as HTMLImageElement;

    imagen.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(imagen.getAttribute('src')).toBe('/assets/fallback.svg');
    expect(imagen.getAttribute('alt')).toBe(
      'Fachada de piedra de la sede de Lima Centro al atardecer',
    );
    expect(element.querySelectorAll('.tarjeta-sede').length).toBe(2);
  });

  it('muestra el estado vacío del catálogo', async () => {
    const fixture = TestBed.createComponent(SedesPage);
    fixture.detectChanges();

    responder([], 0);
    fixture.detectChanges();
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'El catálogo está vacío.',
    );
  });

  it('muestra el 429 del catálogo con su acción y correlationId', async () => {
    const fixture = TestBed.createComponent(SedesPage);
    fixture.detectChanges();

    http
      .expectOne('http://localhost:8080/api/public/hoteles?page=0&size=20')
      .flush(
        {
          success: false,
          message: 'Demasiados intentos',
          data: null,
          error: { code: 'RATE_LIMITED', fields: {} },
          meta: { correlationId: 'corr-429' },
        },
        { status: 429, statusText: 'Too Many Requests' },
      );
    fixture.detectChanges();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain(
      'Demasiados intentos. Espera un momento.',
    );
    expect(element.querySelector('.error__acciones')?.textContent).toContain(
      'Reintentar',
    );
    expect(element.querySelector('.error__correlacion')?.textContent).toContain(
      'corr-429',
    );
    expect(element.querySelectorAll('.tarjeta-sede').length).toBe(0);
  });

  it('muestra un fallo de red con reintento y sin precios inventados', async () => {
    const fixture = TestBed.createComponent(SedesPage);
    fixture.detectChanges();

    http
      .expectOne('http://localhost:8080/api/public/hoteles?page=0&size=20')
      .error(new ProgressEvent('error'));
    fixture.detectChanges();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain(
      'No se pudo conectar con el servicio. Reintenta en unos segundos.',
    );
    expect(element.querySelector('.error__acciones')?.textContent).toContain(
      'Reintentar',
    );
    expect(element.textContent).not.toContain('S/');
  });
});
