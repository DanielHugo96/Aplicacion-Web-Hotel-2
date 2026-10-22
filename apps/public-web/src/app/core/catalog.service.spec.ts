import {
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { CatalogService } from './catalog.service';
import { correlationInterceptor } from './correlation.interceptor';
import { GATEWAY_BASE_URL } from './config';
import { ApiList, CategoriaPublica, HotelPublico } from './catalog.models';

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

const CATEGORIA: CategoriaPublica = {
  idCategoria: 2,
  descripcion: 'Doble',
  detalle: 'Habitación doble de demostración',
  capacidad: 2,
  precioDesde: 100,
  moneda: 'PEN',
  imagenes: [{ url: '/assets/habitaciones/lima-centro-doble-1.jpg', alt: 'Habitación de demostración' }],
};

describe('CatalogService', () => {
  let service: CatalogService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([correlationInterceptor])),
        provideHttpClientTesting(),
        { provide: GATEWAY_BASE_URL, useValue: 'http://localhost:8080' },
      ],
    });
    service = TestBed.inject(CatalogService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista sedes con page y size dentro del máximo', async () => {
    const response: ApiList<HotelPublico> = {
      success: true,
      message: 'OK',
      data: [HOTEL],
      meta: { page: 0, size: 20, totalElements: 1, correlationId: 'c-1' },
    };
    const promise = firstValueFrom(service.listarHoteles(0, 20));

    const request = http.expectOne(
      (req) => req.url === 'http://localhost:8080/api/public/hoteles',
    );
    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('20');
    expect(request.request.headers.get('Accept')).toBe('application/json');
    request.flush(response);

    await expect(promise).resolves.toEqual(response);
  });

  it('transporta imagenUrl e imagenAlt sin transformarlos', async () => {
    const conImagen: HotelPublico = {
      ...HOTEL,
      imagenUrl: '/assets/sedes/lima-centro-fachada.jpg',
      imagenAlt: 'Fachada de piedra de la sede de Lima Centro al atardecer',
    };
    const promise = firstValueFrom(service.obtenerHotel('lima-centro'));
    const request = http.expectOne(
      'http://localhost:8080/api/public/hoteles/lima-centro',
    );
    request.flush({ success: true, message: 'OK', data: conImagen, meta: {} });

    await expect(promise).resolves.toMatchObject({
      data: {
        imagenUrl: '/assets/sedes/lima-centro-fachada.jpg',
        imagenAlt: 'Fachada de piedra de la sede de Lima Centro al atardecer',
      },
    });
  });

  it('consulta el detalle por slug sin ids literales', async () => {
    const promise = firstValueFrom(service.obtenerHotel('lima-centro'));
    const request = http.expectOne(
      'http://localhost:8080/api/public/hoteles/lima-centro',
    );
    request.flush({ success: true, message: 'OK', data: HOTEL, meta: {} });
    await expect(promise).resolves.toMatchObject({ data: HOTEL });
  });

  it('lista categorías con capacidad opcional válida', async () => {
    const promise = firstValueFrom(
      service.listarCategorias('lima-centro', 0, 20, 2),
    );
    const request = http.expectOne(
      (req) =>
        req.url ===
        'http://localhost:8080/api/public/hoteles/lima-centro/categorias',
    );
    expect(request.request.params.get('capacidad')).toBe('2');
    request.flush({
      success: true,
      message: 'OK',
      data: [CATEGORIA],
      meta: { page: 0, size: 20, totalElements: 1 },
    });
    await expect(promise).resolves.toMatchObject({ data: [CATEGORIA] });
  });

  it('rechaza capacidad fuera de 1..10 sin llamar al backend', async () => {
    await expect(
      firstValueFrom(service.listarCategorias('lima-centro', 0, 20, 11)),
    ).rejects.toMatchObject({
      code: 'VALIDATION_ERROR',
      fields: { capacidad: expect.any(String) },
    });
    http.expectNone(
      'http://localhost:8080/api/public/hoteles/lima-centro/categorias',
    );
  });

  it('consulta el detalle de categoría con id entero', async () => {
    const promise = firstValueFrom(
      service.obtenerCategoria('lima-centro', 2),
    );
    const request = http.expectOne(
      'http://localhost:8080/api/public/hoteles/lima-centro/categorias/2',
    );
    request.flush({ success: true, message: 'OK', data: CATEGORIA, meta: {} });
    await expect(promise).resolves.toMatchObject({ data: CATEGORIA });
  });

  it('traduce un 404 del catálogo con mensaje y correlationId', async () => {
    const promise = firstValueFrom(service.obtenerHotel('no-existe'));
    const request = http.expectOne(
      'http://localhost:8080/api/public/hoteles/no-existe',
    );
    request.flush(
      {
        success: false,
        message: 'No encontrado',
        data: null,
        error: { code: 'RESOURCE_NOT_FOUND', fields: {} },
        meta: { correlationId: 'corr-404' },
      },
      { status: 404, statusText: 'Not Found' },
    );

    await expect(promise).rejects.toMatchObject({
      code: 'RESOURCE_NOT_FOUND',
      message: 'No encontramos lo que buscas.',
      correlationId: 'corr-404',
      retryable: false,
    });
  });
});
