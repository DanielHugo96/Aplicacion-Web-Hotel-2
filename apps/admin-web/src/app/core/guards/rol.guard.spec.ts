import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { rolGuard } from './rol.guard';
import { SessionService } from '../services/session.service';

const authData = {
  idPersona: 1,
  nombre: 'Juan',
  apellido: 'Pérez',
  correo: 'juan@hotel.test',
  tipoPersona: 'Cliente',
  token: 'jwt-de-prueba',
  expiresIn: 900,
  sedes: [],
};

const persona = {
  idPersona: 1,
  tipoDocumento: 'DNI',
  documento: '44555666',
  nombre: 'Juan',
  apellido: 'Pérez',
  correo: 'juan@hotel.test',
  fotoUrl: null,
  idTipoPersona: 3,
  tipoPersona: 'Cliente',
  sedes: [],
  estado: true,
  fechaCreacion: '2026-10-19T00:00:00Z',
  version: 1,
};

describe('rolGuard', () => {
  let session: SessionService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        SessionService,
        { provide: Router, useValue: { navigateByUrl: vi.fn() } },
      ],
    });
    session = TestBed.inject(SessionService);
    router = TestBed.inject(Router);
  });

  const runGuard = (rol: string) =>
    TestBed.runInInjectionContext(() => rolGuard(rol)({} as any, {} as any));

  it('rol permitido → true sin redirigir', () => {
    session.setSession({ ...authData, tipoPersona: 'Administrador' }, persona);
    const spy = vi.spyOn(router, 'navigateByUrl');
    expect(runGuard('ADMIN')).toBe(true);
    expect(spy).not.toHaveBeenCalled();
  });

  it('rol no permitido → redirige a la pantalla inicial del rol y devuelve false', () => {
    const spy = vi.spyOn(router, 'navigateByUrl');
    session.setSession({ ...authData, tipoPersona: 'Cliente' }, persona);
    expect(runGuard('ADMIN')).toBe(false);
    expect(spy).toHaveBeenCalledWith('/mi-estadia', { replaceUrl: true });

    session.setSession({ ...authData, tipoPersona: 'Administrador' }, persona);
    spy.mockClear();
    expect(runGuard('CLIENTE')).toBe(false);
    expect(spy).toHaveBeenCalledWith('/app/inicio', { replaceUrl: true });
  });

  it('sin rol → redirige a /login', () => {
    const spy = vi.spyOn(router, 'navigateByUrl');
    expect(runGuard('ADMIN')).toBe(false);
    expect(spy).toHaveBeenCalledWith('/login', { replaceUrl: true });
  });
});
