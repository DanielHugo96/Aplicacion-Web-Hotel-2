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
  dashboardReporte,
  envelopeReporte,
} from '../reportes/testing/reportes.fixtures';
import {
  PERSONA_ADMIN,
  PERSONA_EMPLEADO,
  authData,
} from '../ventas/testing/ventas.fixtures';
import { InicioComponent } from './inicio.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

function texto(fixture: ComponentFixture<unknown>): string {
  return ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\u00a0/g, ' ');
}

describe('InicioComponent (dashboard)', () => {
  let fixture: ComponentFixture<InicioComponent>;
  let http: HttpTestingController;

  function crear(rol = 'Administrador', sede: number | null = 1): void {
    TestBed.configureTestingModule({
      imports: [InicioComponent],
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
      authData(rol, rol === 'Empleado' ? [1] : []),
      rol === 'Empleado' ? PERSONA_EMPLEADO : PERSONA_ADMIN
    );
    TestBed.inject(SedeContextService).selectedId.set(sede);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InicioComponent);
  }

  beforeEach(() => crear());

  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });

  it('muestra el skeleton de KPIs y luego las cifras del backend con pipe es-PE', async () => {
    fixture.detectChanges();
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.skeleton')
    ).toBeTruthy();

    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/dashboard`)
      .flush(
        envelopeReporte(
          dashboardReporte({
            habitacionesOcupadas: 1,
            habitacionesDisponibles: 3,
            productosBajoStock: 19,
            ingresosHoy: 1234.5,
          })
        )
      );
    await microtasks();
    fixture.detectChanges();

    const textoRender = texto(fixture);
    expect(textoRender).toContain('Habitaciones ocupadas');
    expect(textoRender).toContain('Habitaciones libres');
    expect(textoRender).toContain('Productos bajo stock');
    expect(textoRender).toContain('S/ 1,234.50');
    expect(textoRender).toContain('Moneda PEN');
    // El importe se muestra tal cual llega: no se recalcula nada.
    expect(textoRender).not.toContain('1,234.499');
  });

  it('el administrador pide el consolidado sin idHotel', async () => {
    TestBed.resetTestingModule();
    crear('Administrador', null);
    fixture.detectChanges();
    const req = http.expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/dashboard`);
    expect(req.request.params.has('idHotel')).toBe(false);
    req.flush(envelopeReporte(dashboardReporte()));
    await microtasks();
  });

  it('el empleado envía su sede asignada como idHotel', async () => {
    TestBed.resetTestingModule();
    crear('Empleado', 1);
    fixture.detectChanges();
    const req = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/reportes/dashboard` &&
        r.params.get('idHotel') === '1'
    );
    req.flush(envelopeReporte(dashboardReporte()));
    await microtasks();
    expect(fixture.componentInstance.esAdmin()).toBe(false);
  });

  it('sin datos del backend muestra el vacío exacto de la spec', async () => {
    TestBed.resetTestingModule();
    crear('Administrador', null);
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/dashboard`)
      .flush(envelopeReporte(null));
    await microtasks();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('Sin datos para tu sede todavía');
  });

  it('un 503 REPORTING_NOT_READY muestra el mensaje del catálogo y reintenta', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/dashboard`)
      .flush(apiErrorReporte('REPORTING_NOT_READY', 'cid-dash'), {
        status: 503,
        statusText: 'Service Unavailable',
      });
    await microtasks();
    fixture.detectChanges();

    const html = fixture.nativeElement as HTMLElement;
    expect(texto(fixture)).toContain('Los reportes aún se están preparando.');
    expect(texto(fixture)).toContain('cid-dash');
    const reintentar = Array.from(html.querySelectorAll('button')).find(
      (b) => (b.textContent ?? '').trim() === 'Reintentar'
    ) as HTMLButtonElement;
    expect(reintentar).toBeTruthy();
    reintentar.click();
    await microtasks();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/dashboard`)
      .flush(envelopeReporte(dashboardReporte()));
    await microtasks();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('Habitaciones ocupadas');
  });

  it('la meta degradada enseña el aviso de consistencia eventual sin bloquear los KPIs', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/reportes/dashboard`)
      .flush(
        envelopeReporte(dashboardReporte(), {
          degraded: true,
          bootstrapComplete: true,
          updatedAt: '2026-10-04T15:00:00Z',
        })
      );
    await microtasks();
    fixture.detectChanges();
    expect(texto(fixture)).toContain('Datos eventualmente consistentes');
    expect(texto(fixture)).toContain('Proyecciones listas:');
    // Los KPIs siguen visibles: el aviso no bloquea la vista.
    expect(texto(fixture)).toContain('Habitaciones ocupadas');
  });
});
