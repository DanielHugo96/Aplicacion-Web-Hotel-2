import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../../core/api.config';
import { SessionService } from '../../core/services/session.service';
import { SedeContextService } from '../../core/services/sede-context.service';
import { CatalogoService } from '../../core/services/catalogo.service';
import { ToastService } from '../../core/services/toast.service';
import { routes } from '../../app.routes';
import { recepcionRoutes } from './recepcion.routes';
import { ShellComponent } from '../shell/shell.component';
import { personaAdmin, personaCliente, PERSONA_EMPLEADO, authData } from './testing/recepcion.fixtures';

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

describe('rutas y menú del módulo de recepción', () => {
  beforeEach(() => stubMatchMedia());
  afterEach(() => TestBed.resetTestingModule());

  it('declara las cinco rutas lazy dentro del shell del panel', () => {
    expect(recepcionRoutes.map((r) => r.path)).toEqual([
      'recepcion',
      'recepcion/check-in',
      'recepcion/:id/cierre',
      'recepcion/:id',
      'limpieza',
    ]);
    expect(recepcionRoutes.every((r) => typeof r.loadComponent === 'function')).toBe(true);
    expect(recepcionRoutes.every((r) => (r.canActivate ?? []).length === 1)).toBe(true);

    const shell = routes.find((r) => r.path === 'app');
    const paths = (shell?.children ?? []).map((r) => r.path);
    expect(paths).toContain('recepcion');
    expect(paths).toContain('recepcion/check-in');
    expect(paths).toContain('recepcion/:id/cierre');
    expect(paths).toContain('recepcion/:id');
    expect(paths).toContain('limpieza');
    // Los literales se declaran antes que los parámetros que los capturarían.
    expect(paths.indexOf('recepcion/check-in')).toBeLessThan(paths.indexOf('recepcion/:id'));
    expect(paths.indexOf('recepcion/:id/cierre')).toBeLessThan(paths.indexOf('recepcion/:id'));
  });

  it('A y E ven Recepción y Limpieza en el menú; C no las ve', async () => {
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
    http
      .match((r) => r.url === `${API_BASE_URL}/api/hotel/listar`)
      .forEach((req) =>
        req.flush({ success: true, message: 'OK', data: [], meta: { page: 0, size: 100, totalElements: 0, correlationId: 'cid' } })
      );
    fixture.detectChanges();
    let shell = fixture.componentInstance;
    expect(shell.estaVisible({ label: 'Recepción', path: '/app/recepcion', roles: ['ADMIN', 'EMPLEADO'] })).toBe(true);
    expect(shell.estaVisible({ label: 'Limpieza', path: '/app/limpieza', roles: ['ADMIN', 'EMPLEADO'] })).toBe(true);

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
    httpEmpleado
      .match((r) => r.url === `${API_BASE_URL}/api/hotel/listar`)
      .forEach((req) =>
        req.flush({ success: true, message: 'OK', data: [], meta: { page: 0, size: 100, totalElements: 0, correlationId: 'cid' } })
      );
    fixture.detectChanges();
    shell = fixture.componentInstance;
    expect(shell.estaVisible({ label: 'Recepción', path: '/app/recepcion', roles: ['ADMIN', 'EMPLEADO'] })).toBe(true);
    expect(shell.estaVisible({ label: 'Limpieza', path: '/app/limpieza', roles: ['ADMIN', 'EMPLEADO'] })).toBe(true);

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
    expect(shell.estaVisible({ label: 'Recepción', path: '/app/recepcion', roles: ['ADMIN', 'EMPLEADO'] })).toBe(false);
    expect(shell.estaVisible({ label: 'Limpieza', path: '/app/limpieza', roles: ['ADMIN', 'EMPLEADO'] })).toBe(false);
  });

  it('la ruta de cierre exige A/E: E puede y C no', () => {
    const ruta = recepcionRoutes.find((r) => r.path === 'recepcion/:id/cierre');
    const guard = ruta?.canActivate?.[0] as (a: unknown, b: unknown) => unknown;
    expect(typeof guard).toBe('function');

    TestBed.configureTestingModule({
      providers: [
        SessionService,
        { provide: Router, useValue: { navigateByUrl: vi.fn() } },
      ],
    });
    const session = TestBed.inject(SessionService);
    const router = TestBed.inject(Router);

    session.setSession(authData('Empleado', [1]), PERSONA_EMPLEADO);
    expect(TestBed.runInInjectionContext(() => guard({} as any, {} as any))).toBe(true);

    session.setSession(authData('Cliente'), personaCliente);
    const spy = vi.spyOn(router, 'navigateByUrl');
    expect(TestBed.runInInjectionContext(() => guard({} as any, {} as any))).toBe(false);
    expect(spy).toHaveBeenCalledWith('/mi-estadia', { replaceUrl: true });
    TestBed.resetTestingModule();
  });
});
