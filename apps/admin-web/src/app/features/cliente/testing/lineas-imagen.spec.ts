import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { gatewayInterceptor } from '../../../core/gateway.interceptor';
import { ClienteContextService } from '../../../core/services/cliente-context.service';
import { ClienteService } from '../../../core/services/cliente.service';
import { ETagCacheService } from '../../../core/services/etag-cache.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { IMAGEN_FALLBACK } from '../../../shared/imagen-panel.component';
import {
  carritoItem,
  envelopePage,
  envelopeSingle,
  PERSONA_CLIENTE,
  producto,
  recepcion,
  tokenConSub,
  venta,
} from '../../cliente/testing/cliente.fixtures';
import { CarritoComponent } from '../../cliente/carrito/carrito.component';
import { CatalogoClienteComponent } from '../../cliente/catalogo/catalogo-cliente.component';
import { VentaDetalleComponent } from '../../cliente/mis-ventas/venta-detalle.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

function sesionCliente(): void {
  TestBed.inject(SessionService).setSession(
    {
      idPersona: 100,
      nombre: 'Cliente',
      apellido: 'Uno',
      correo: 'cliente.uno@hotel.test',
      tipoPersona: 'Cliente',
      token: tokenConSub(100),
      expiresIn: 900,
      sedes: [],
    },
    PERSONA_CLIENTE
  );
}

function base(): void {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      SessionService,
      SedeContextService,
      ClienteService,
      ClienteContextService,
      ETagCacheService,
      ToastService,
      provideHttpClient(withInterceptors([gatewayInterceptor])),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: {
          snapshot: { paramMap: convertToParamMap({ id: '5001' }) },
          parent: { snapshot: { paramMap: convertToParamMap({}) } },
        },
      },
    ],
  });
  sesionCliente();
}

describe('Catálogo del cliente — miniatura del producto', () => {
  let fixture: ComponentFixture<CatalogoClienteComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    base();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CatalogoClienteComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('pinta la miniatura con el nombre como alt y el fallback si es nula', async () => {
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/cliente/100`)
      .flush(envelopePage([recepcion()], 0, 100, 1));
    await microtasks();
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta/catalogo`)
      .flush(
        envelopePage([
          producto({ idProducto: 1, nombre: 'Agua 500ml', imagenUrl: '/assets/productos/agua-500ml.png' }),
          producto({ idProducto: 2, nombre: 'Snack', imagenUrl: null }),
        ])
      );
    await microtasks();
    fixture.detectChanges();

    const imagenes = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('app-imagen-panel img')
    ) as HTMLImageElement[];

    expect(imagenes.map((i) => i.getAttribute('src'))).toEqual([
      '/assets/productos/agua-500ml.png',
      IMAGEN_FALLBACK,
    ]);
    expect(imagenes.map((i) => i.getAttribute('alt'))).toEqual([
      'Producto Agua 500ml',
      'Producto Snack',
    ]);
  });
});

describe('Carrito del cliente — miniatura por línea', () => {
  let fixture: ComponentFixture<CarritoComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    base();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CarritoComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('pinta la miniatura de la línea con imagen y el fallback con imagenUrl nula', async () => {
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/cliente/100`)
      .flush(envelopePage([recepcion()], 0, 100, 1));
    await microtasks();
    fixture.detectChanges();
    http
      .expectOne(`${API_BASE_URL}/api/carrito/listar/1001`)
      .flush(
        envelopeSingle([
          carritoItem({
            idProducto: 1,
            nombreProducto: 'Agua 500ml',
            imagenUrl: '/assets/productos/agua-500ml.png',
          }),
          carritoItem({
            idProducto: 2,
            idCarrito: 9002,
            nombreProducto: 'Snack',
            imagenUrl: null,
          }),
        ])
      );
    await microtasks();
    fixture.detectChanges();

    const imagenes = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('app-imagen-panel img')
    ) as HTMLImageElement[];

    expect(imagenes.map((i) => i.getAttribute('src'))).toEqual([
      '/assets/productos/agua-500ml.png',
      IMAGEN_FALLBACK,
    ]);
    expect(imagenes[1].getAttribute('alt')).toBe('Producto Snack');
  });
});

describe('Detalle de venta del cliente — miniatura por línea', () => {
  let fixture: ComponentFixture<VentaDetalleComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    base();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(VentaDetalleComponent);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('usa imagenUrl de la línea cuando llega y el fallback cuando no', async () => {
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/venta/buscar/5001`)
      .flush(
        envelopeSingle(
          venta({
              detalles: [
                {
                  idDetalleVenta: 1,
                  idProducto: 1,
                  nombreProducto: 'Agua 500ml',
                  imagenUrl: '/assets/productos/agua-500ml.png',
                  cantidad: 2,
                  precioUnitario: 3.5,
                  subTotal: 7,
                },
                {
                  idDetalleVenta: 2,
                  idProducto: 2,
                  nombreProducto: 'Snack',
                  imagenUrl: null,
                  cantidad: 1,
                  precioUnitario: 8,
                  subTotal: 8,
                },
              ],
          })
        )
      );
    await microtasks();
    fixture.detectChanges();

    const imagenes = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('app-imagen-panel img')
    ) as HTMLImageElement[];

    expect(imagenes.map((i) => i.getAttribute('src'))).toEqual([
      '/assets/productos/agua-500ml.png',
      IMAGEN_FALLBACK,
    ]);
  });
});
