import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../../core/api.config';
import { SessionService } from '../../core/services/session.service';
import { SedeContextService } from '../../core/services/sede-context.service';
import { CatalogoService } from '../../core/services/catalogo.service';
import { ToastService } from '../../core/services/toast.service';
import { routes } from '../../app.routes';
import { reportesRoutes } from './reportes.routes';
import { ShellComponent } from '../shell/shell.component';
import {
  PERSONA_ADMIN,
  PERSONA_CLIENTE,
  PERSONA_EMPLEADO,
  authData,
} from '../ventas/testing/ventas.fixtures';

function stubMatchMedia(): void {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    })),
  });
}

function vaciarSedes(http: HttpTestingController): void {
  http
    .match((r) => r.url === `${API_BASE_URL}/api/hotel/listar`)
    .forEach((req) =>
      req.flush({
        success: true,
        message: 'OK',
        data: [],
        meta: { page: 0, size: 100, totalElements: 0, correlationId: 'cid' },
      })
    );
}

describe('rutas y menú de reportes (dashboard y /app/reportes)', () => {
  beforeEach(() => stubMatchMedia());
  afterEach(() => TestBed.resetTestingModule());

  it('declara las dos rutas lazy colgadas del shell con su rolGuard', () => {
    expect(reportesRoutes.map((r) => r.path)).toEqual(['inicio', 'reportes']);
    expect(reportesRoutes.every((r) => typeof r.loadComponent === 'function')).toBe(true);
    expect(reportesRoutes.every((r) => (r.canActivate ?? []).length === 1)).toBe(true);

    const shell = routes.find((r) => r.path === 'app');
    // El shell exige authGuard; las rutas hijas añaden el guard de rol A/E.
    expect(shell?.canActivate?.length).toBe(1);
    const paths = (shell?.children ?? []).map((r) => r.path);
    expect(paths).toContain('inicio');
    expect(paths).toContain('reportes');
  });

  it('A y E ven Reportes en el menú; C no lo ve', () => {
    const configurar = (): HttpTestingController =>
      TestBed.configureTestingModule({
        imports: [ShellComponent],
        providers: [
          SessionService,
          SedeContextService,
          CatalogoService,
          ToastService,
          provideHttpClient(),
          provideHttpClientTesting(),
          provideRouter([]),
        ],
      }).inject(HttpTestingController);

    let http = configurar();
    TestBed.inject(SessionService).setSession(authData('Administrador'), PERSONA_ADMIN);
    let fixture = TestBed.createComponent(ShellComponent);
    vaciarSedes(http);
    fixture.detectChanges();
    const item = {
      label: 'Reportes',
      path: '/app/reportes',
      roles: ['ADMIN', 'EMPLEADO'] as const,
    };
    expect(fixture.componentInstance.estaVisible({ ...item, roles: [...item.roles] })).toBe(true);

    TestBed.resetTestingModule();
    http = configurar();
    TestBed.inject(SessionService).setSession(
      authData('Empleado', [1]),
      PERSONA_EMPLEADO
    );
    fixture = TestBed.createComponent(ShellComponent);
    vaciarSedes(http);
    fixture.detectChanges();
    expect(fixture.componentInstance.estaVisible({ ...item, roles: [...item.roles] })).toBe(true);

    TestBed.resetTestingModule();
    configurar();
    TestBed.inject(SessionService).setSession(authData('Cliente'), PERSONA_CLIENTE);
    fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.estaVisible({ ...item, roles: [...item.roles] })).toBe(false);
  });
});
