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

  it('declara las cuatro rutas lazy dentro del shell del panel', () => {
    expect(recepcionRoutes.map((r) => r.path)).toEqual([
      'recepcion',
      'recepcion/check-in',
      'recepcion/:id',
      'limpieza',
    ]);
    expect(recepcionRoutes.every((r) => typeof r.loadComponent === 'function')).toBe(true);
    expect(recepcionRoutes.every((r) => (r.canActivate ?? []).length === 1)).toBe(true);

    const shell = routes.find((r) => r.path === 'app');
    const paths = (shell?.children ?? []).map((r) => r.path);
    expect(paths).toContain('recepcion');
    expect(paths).toContain('recepcion/check-in');
    expect(paths).toContain('recepcion/:id');
    expect(paths).toContain('limpieza');
    // El literal check-in se declara antes que el parámetro :id.
    expect(paths.indexOf('recepcion/check-in')).toBeLessThan(paths.indexOf('recepcion/:id'));
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
});
