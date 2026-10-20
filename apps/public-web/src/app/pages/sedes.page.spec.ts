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
    nombre: 'Hotel Demo Lima Centro',
    descripcion: 'Sede de demostración',
    direccion: 'Dirección de demostración',
    ciudad: 'Lima',
    telefono: '+51 900 000 001',
    correoContacto: 'contacto@hotel.test',
  },
  {
    idHotel: 3,
    slug: 'arequipa',
    nombre: 'Hotel Demo Arequipa',
    descripcion: 'Sede de demostración',
    direccion: 'Dirección de demostración',
    ciudad: 'Arequipa',
    telefono: '+51 900 000 001',
    correoContacto: 'contacto@hotel.test',
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

  it('pinta solo las sedes que devuelve el catálogo paginado', async () => {
    const fixture = TestBed.createComponent(SedesPage);
    fixture.detectChanges();

    const request = http.expectOne(
      (req) => req.url === 'http://localhost:8080/api/public/hoteles',
    );
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    request.flush({
      success: true,
      message: 'OK',
      data: HOTELES,
      meta: { page: 0, size: 20, totalElements: 2, correlationId: 'c-1' },
    });
    fixture.detectChanges();
    await fixture.whenStable();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelectorAll('.tarjeta-sede').length).toBe(2);
    expect(element.textContent).toContain('Hotel Demo Lima Centro');
    expect(element.textContent).toContain('Hotel Demo Arequipa');
    expect(element.querySelector('.paginacion__estado')?.textContent).toContain(
      'Página 1 de 1 · 2 registros',
    );
  });

  it('muestra el estado vacío del catálogo', async () => {
    const fixture = TestBed.createComponent(SedesPage);
    fixture.detectChanges();

    http
      .expectOne('http://localhost:8080/api/public/hoteles?page=0&size=20')
      .flush({
        success: true,
        message: 'OK',
        data: [],
        meta: { page: 0, size: 20, totalElements: 0 },
      });
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
