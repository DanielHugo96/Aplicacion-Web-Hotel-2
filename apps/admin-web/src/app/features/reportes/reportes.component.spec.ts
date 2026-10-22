import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../../core/api.config';
import { gatewayInterceptor } from '../../core/gateway.interceptor';
import { SessionService } from '../../core/services/session.service';
import { SedeContextService } from '../../core/services/sede-context.service';
import { ReportesService } from '../../core/services/reportes.service';
import {
  apiErrorReporte,
  envelopeReporte,
  reporteBajoStock,
  reporteCobros,
  reporteOcupacion,
  reporteVentas,
} from './testing/reportes.fixtures';
import {
  PERSONA_ADMIN,
  PERSONA_CLIENTE,
  PERSONA_EMPLEADO,
  authData,
} from '../ventas/testing/ventas.fixtures';
import { ReportesComponent } from './reportes.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

const INICIO = '2026-09-20T00:00:00-05:00';
const FIN = '2026-10-21T00:00:00-05:00';

function texto(fixture: ComponentFixture<unknown>): string {
  return ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\u00a0/g, ' ');
}

describe('ReportesComponent (/app/reportes)', () => {
  let fixture: ComponentFixture<ReportesComponent>;
  let http: HttpTestingController;

  function crear(rol = 'Administrador', sedes: number[] = [], sede: number | null = 1): void {
    TestBed.configureTestingModule({
      imports: [ReportesComponent],
      providers: [
        SessionService,
        SedeContextService,
        ReportesService,
        provideHttpClient(withInterceptors([gatewayInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    TestBed.inject(SessionService).setSession(
      authData(rol, sedes),
      rol === 'Empleado' ? PERSONA_EMPLEADO : rol === 'Cliente' ? PERSONA_CLIENTE : PERSONA_ADMIN
    );
    TestBed.inject(SedeContextService).selectedId.set(sede);
    // El topbar ya cargó las sedes en la app real; aquí se simula el mismo estado.
    TestBed.inject(SedeContextService).sedes.set([
      {
        idHotel: 1,
        slug: 'lima-centro',
        nombre: 'Casa Sillar Lima Centro',
        descripcion: 'Casona restaurada a dos cuadras de la Plaza Mayor.',
        direccion: 'Jr. de la Unión 540, Cercado de Lima',
        ciudad: 'Lima',
        telefono: '+51 1 428 6400',
        correoContacto: 'contacto@casasillar.pe',
        estado: true,
        publicado: true,
        version: 1,
      },
    ]);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ReportesComponent);
  }

  beforeEach(() => crear());

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  function flushVentas(
    data = [reporteVentas()],
    page = 0,
    total = data.length,
    size = 20
  ): void {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`)
      .flush(envelopeReporte(data, { page, size, totalElements: total, degraded: false }));
  }

  it('carga las ventas de la sede con el rango ISO y las muestra formateadas', async () => {
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`);
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('idHotel')).toBe('1');
    expect(req.request.params.get('page')).toBe('0');
    expect(req.request.params.get('size')).toBe('20');
    // El rango sale del formulario (30 días atrás → hoy) con offset de Lima.
    expect(req.request.params.get('inicio')).toMatch(/T00:00:00-05:00$/);
    expect(req.request.params.get('fin')).toMatch(/T00:00:00-05:00$/);
    req.flush(
      envelopeReporte(
        [reporteVentas({ nombreProducto: 'Agua 500ml', cantidadTotal: 12, totalIngresado: 1234.5 })],
        { page: 0, size: 20, totalElements: 1 }
      )
    );
    await microtasks();
    fixture.detectChanges();

    expect(texto(fixture)).toContain('Agua 500ml');
    expect(texto(fixture)).toContain('S/ 1,234.50');
    // Cifras tabulares y cabecera de la tabla.
    expect(fixture.nativeElement.querySelectorAll('th.num').length).toBe(2);
  });

  it('sin filas muestra «Sin resultados» y no pide nada más', async () => {
    flushVentas([], 0, 0);
    await microtasks();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('Sin resultados');
    http.expectNone(`${API_BASE_URL}/api/reportes/ventas?page=1`);
  });

  it('pagina con meta.page/size/totalElements y pide la siguiente página', async () => {
    flushVentas([reporteVentas()], 0, 21);
    await microtasks();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('Página 1 de 2 · 21 registros');

    const siguiente = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button')
    ).find((b) => (b.textContent ?? '').trim() === 'Siguiente') as HTMLButtonElement;
    siguiente.click();
    await microtasks();
    http
      .expectOne(
        (r) =>
          r.url === `${API_BASE_URL}/api/reportes/ventas` && r.params.get('page') === '1'
      )
      .flush(envelopeReporte([reporteVentas({ nombreProducto: 'Snack' })], {
        page: 1,
        size: 20,
        totalElements: 21,
      }));
    await microtasks();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('Página 2 de 2 · 21 registros');
    expect(texto(fixture)).toContain('Snack');
  });

  it('la meta degradada muestra el banner de consistencia eventual con updatedAt y bootstrap', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`)
      .flush(
        envelopeReporte([reporteVentas()], {
          degraded: true,
          bootstrapComplete: true,
          updatedAt: '2026-10-04T15:00:00Z',
        })
      );
    await microtasks();
    fixture.detectChanges();
    const html = fixture.nativeElement as HTMLElement;
    expect(texto(fixture)).toContain('Datos eventualmente consistentes');
    expect(html.querySelector('.banner-aviso')).toBeTruthy();
    expect(texto(fixture)).toContain('Consistencia EVENTUAL');
    expect(texto(fixture)).toContain('Proyecciones listas:');
  });

  it('sin degradación no aparece el banner', async () => {
    flushVentas([reporteVentas()], 0, 1);
    await microtasks();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.banner-aviso')).toBeFalsy();
  });

  it('rango de más de 366 días se rechaza con el mensaje exacto y sin llamar al Gateway', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`)
      .flush(envelopeReporte([], { totalElements: 0 }));
    await microtasks();

    const componente = fixture.componentInstance;
    componente.inicio = '2026-01-01';
    componente.hasta = '2027-01-02';
    componente.aplicar();
    await microtasks();
    fixture.detectChanges();

    expect(texto(fixture)).toContain('El rango no puede superar 366 días');
    http.expectNone((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`);
  });

  it('ocupación envía el rango a medianoche de Lima y etiqueta Libre/Ocupada', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`)
      .flush(envelopeReporte([], { totalElements: 0 }));
    await microtasks();

    const componente = fixture.componentInstance;
    componente.cambiarTab('ocupacion');
    await microtasks();
    const req = http.expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ocupacion`);
    // El contrato de ocupación exige medianoche de America/Lima en ambos extremos.
    expect(req.request.params.get('inicio')).toMatch(/T00:00:00-05:00$/);
    expect(req.request.params.get('fin')).toMatch(/T00:00:00-05:00$/);
    req.flush(
      envelopeReporte(
        [
          reporteOcupacion({ numeroHabitacion: '101', vecesAlquilada: 2 }),
          reporteOcupacion({ idHabitacion: 102, numeroHabitacion: '102', vecesAlquilada: 0 }),
        ],
        { totalElements: 2 }
      )
    );
    await microtasks();
    fixture.detectChanges();

    const pills = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('span.pill')
    ).map((p) => (p.textContent ?? '').trim());
    expect(pills).toContain('Ocupada');
    expect(pills).toContain('Libre');
    // El vocabulario es Libre/Ocupada/Sin datos, nunca «Disponible».
    expect(texto(fixture)).not.toContain('Disponible');
  });

  it('un 503 REPORTING_NOT_READY muestra el mensaje, el correlationId y permite reintentar', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`)
      .flush(apiErrorReporte('REPORTING_NOT_READY', 'cid-503'), {
        status: 503,
        statusText: 'Service Unavailable',
      });
    await microtasks();
    fixture.detectChanges();

    const html = fixture.nativeElement as HTMLElement;
    expect(texto(fixture)).toContain('Los reportes aún se están preparando.');
    expect(texto(fixture)).toContain('cid-503');
    expect(html.querySelector('.correlation')?.textContent).toContain('cid-503');

    const reintentar = Array.from(html.querySelectorAll('button')).find(
      (b) => (b.textContent ?? '').trim() === 'Reintentar'
    ) as HTMLButtonElement;
    expect(reintentar).toBeTruthy();
    reintentar.click();
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`)
      .flush(envelopeReporte([reporteVentas()], { totalElements: 1 }));
    await microtasks();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('Agua 500ml');
  });

  it('un 503 REPORTING_UNAVAILABLE usa su propio mensaje del catálogo', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`)
      .flush(apiErrorReporte('REPORTING_UNAVAILABLE'), {
        status: 503,
        statusText: 'Service Unavailable',
      });
    await microtasks();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('Los reportes no están disponibles en este momento.');
  });

  it('un 400 VALIDATION_ERROR resalta el campo devuelto por el backend', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`)
      .flush(
        {
          success: false,
          message: 'Revisa los datos marcados.',
          data: null,
          error: { code: 'VALIDATION_ERROR', fields: { inicio: 'el intervalo supera 366 días' } },
          meta: { correlationId: 'cid-400', updatedAt: null },
        },
        { status: 400, statusText: 'Bad Request' }
      );
    await microtasks();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('Revisa los datos marcados.');
    expect(fixture.componentInstance.error()?.fields['inicio']).toBe('el intervalo supera 366 días');
  });

  it('las pestañas cobros y bajo stock usan su propia ruta y columnas', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`)
      .flush(envelopeReporte([], { totalElements: 0 }));
    await microtasks();

    const componente = fixture.componentInstance;
    componente.cambiarTab('cobros');
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/cobros`)
      .flush(
        envelopeReporte(
          [reporteCobros({ medioPagoCierre: null, fechaCierre: '2026-10-20T16:03:23Z' })],
          { totalElements: 1 }
        )
      );
    await microtasks();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('No registrado');

    componente.cambiarTab('bajoStock');
    await microtasks();
    const reqBajoStock = http.expectOne(
      (r) => r.url === `${API_BASE_URL}/api/reportes/productos-bajo-stock`
    );
    expect(reqBajoStock.request.params.get('limite')).toBe('5');
    reqBajoStock.flush(
      envelopeReporte([reporteBajoStock({ cantidad: 3, precio: 3.5 })], { totalElements: 1 })
    );
    await microtasks();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('S/ 3.50');
    expect(texto(fixture)).toContain('Activo');
  });

  it('el administrador puede pedir el consolidado (sin idHotel)', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`)
      .flush(envelopeReporte([], { totalElements: 0 }));
    await microtasks();

    // «Ninguna sede» no vacía la tabla: bajo stock no exige idHotel el backend
    // lo sí, así que la pantalla avisa y no dispara ninguna GET.
    fixture.componentInstance.cambiarSedeValor('');
    await microtasks();
    fixture.detectChanges();
    http.expectNone((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`);
    // A puede elegir el consolidado en el dashboard, pero estos cuatro reportes
    // exigen sede: el aviso lo dice en vez de «Sin sede asignada».
    expect(texto(fixture)).toContain('Elige una sede para consultar este reporte');
  });

  it('el selector de sede muestra la sede con la que se consultó, no la primera opción', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`)
      .flush(envelopeReporte([reporteVentas()], { totalElements: 1 }));
    await microtasks();
    fixture.detectChanges();
    const select = (fixture.nativeElement as HTMLElement).querySelector(
      '#reporte-sede'
    ) as HTMLSelectElement;
    expect(select.value).toBe('1');
    expect(select.selectedOptions[0].textContent?.trim()).toBe('Casa Sillar Lima Centro');
  });

  it('el empleado no ve el consolidado ni la opción de todas las sedes', () => {
    TestBed.resetTestingModule();
    crear('Empleado', [1]);
    fixture.detectChanges();
    http
      .match((r) => r.url === `${API_BASE_URL}/api/reportes/ventas`)
      .forEach((req) => req.flush(envelopeReporte([], { totalElements: 0 })));
    fixture.detectChanges();
    expect(fixture.componentInstance.esAdmin()).toBe(false);
    const opciones = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('#reporte-sede option')
    ).map((o) => (o.textContent ?? '').trim());
    expect(opciones).not.toContain('Consolidado de todas las sedes');
  });
});
