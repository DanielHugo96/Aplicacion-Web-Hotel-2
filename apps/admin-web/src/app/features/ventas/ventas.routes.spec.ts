import { ComponentFixture, TestBed } from '@angular/core/testing';
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
import { ventasRoutes } from './ventas.routes';
import { ShellComponent } from '../shell/shell.component';
import {
  PERSONA_EMPLEADO,
  authData,
  personaAdmin,
  personaCliente,
} from './testing/ventas.fixtures';

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

describe('rutas y menú del módulo de productos y ventas', () => {
  beforeEach(() => stubMatchMedia());
  afterEach(() => TestBed.resetTestingModule());

  it('declara las siete rutas lazy dentro del shell del panel', () => {
    expect(ventasRoutes.map((r) => r.path)).toEqual([
      'productos',
      'productos/nuevo',
      'productos/:id/editar',
      'productos/:id/movimientos',
      'ventas',
      'ventas/nueva',
      'ventas/:id',
    ]);
    expect(ventasRoutes.every((r) => typeof r.loadComponent === 'function')).toBe(true);
    expect(ventasRoutes.every((r) => (r.canActivate ?? []).length === 1)).toBe(true);

    const shell = routes.find((r) => r.path === 'app');
    const paths = (shell?.children ?? []).map((r) => r.path);
    expect(paths).toContain('productos');
    expect(paths).toContain('productos/nuevo');
    expect(paths).toContain('productos/:id/editar');
    expect(paths).toContain('productos/:id/movimientos');
    expect(paths).toContain('ventas');
    expect(paths).toContain('ventas/nueva');
    expect(paths).toContain('ventas/:id');
    // Los literales se declaran antes que sus parámetros.
    expect(paths.indexOf('productos/nuevo')).toBeLessThan(paths.indexOf('productos/:id/editar'));
    expect(paths.indexOf('ventas/nueva')).toBeLessThan(paths.indexOf('ventas/:id'));
  });

  it('A y E ven Productos y Ventas en el menú; C no las ve', () => {
    const http = TestBed.configureTestingModule({
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

    const session = TestBed.inject(SessionService);
    session.setSession(authData('Administrador'), personaAdmin);
    let fixture: ComponentFixture<ShellComponent> = TestBed.createComponent(ShellComponent);
    vaciarSedes(http);
    fixture.detectChanges();
    let shell = fixture.componentInstance;
    expect(
      shell.estaVisible({ label: 'Productos', path: '/app/productos', roles: ['ADMIN', 'EMPLEADO'] })
    ).toBe(true);
    expect(
      shell.estaVisible({ label: 'Ventas', path: '/app/ventas', roles: ['ADMIN', 'EMPLEADO'] })
    ).toBe(true);

    TestBed.resetTestingModule();
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
    });
    const httpEmpleado = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).setSession(
      { ...authData('Empleado'), sedes: [1] },
      PERSONA_EMPLEADO
    );
    fixture = TestBed.createComponent(ShellComponent);
    vaciarSedes(httpEmpleado);
    fixture.detectChanges();
    shell = fixture.componentInstance;
    expect(
      shell.estaVisible({ label: 'Productos', path: '/app/productos', roles: ['ADMIN', 'EMPLEADO'] })
    ).toBe(true);
    expect(
      shell.estaVisible({ label: 'Ventas', path: '/app/ventas', roles: ['ADMIN', 'EMPLEADO'] })
    ).toBe(true);

    TestBed.resetTestingModule();
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
    });
    TestBed.inject(SessionService).setSession(authData('Cliente'), personaCliente);
    fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    shell = fixture.componentInstance;
    expect(
      shell.estaVisible({ label: 'Productos', path: '/app/productos', roles: ['ADMIN', 'EMPLEADO'] })
    ).toBe(false);
    expect(
      shell.estaVisible({ label: 'Ventas', path: '/app/ventas', roles: ['ADMIN', 'EMPLEADO'] })
    ).toBe(false);
  });
});
