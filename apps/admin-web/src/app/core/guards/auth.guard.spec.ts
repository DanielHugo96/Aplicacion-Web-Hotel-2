import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { authGuard } from './auth.guard';
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

describe('authGuard', () => {
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

  const runGuard = () =>
    TestBed.runInInjectionContext(() => authGuard({} as any, {} as any));

  it('sin token → redirige a /login y devuelve false', () => {
    const spy = vi.spyOn(router, 'navigateByUrl');
    expect(runGuard()).toBe(false);
    expect(spy).toHaveBeenCalledWith('/login', { replaceUrl: true });
  });

  it('con token válido → devuelve true sin redirigir', () => {
    const spy = vi.spyOn(router, 'navigateByUrl');
    session.setSession(authData, persona);
    expect(runGuard()).toBe(true);
    expect(spy).not.toHaveBeenCalled();
  });
});
