import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { describe, expect, it, vi } from 'vitest';
import { routes } from '../../app.routes';
import { SessionService } from '../../core/services/session.service';
import { PERSONA_CLIENTE, tokenConSub } from './testing/cliente.fixtures';
import { clienteRoutes } from './cliente.routes';

describe('rutas del área de cliente', () => {
  it('declara las seis rutas bajo un único shell con authGuard y rolGuard(CLIENTE)', () => {
    expect(clienteRoutes).toHaveLength(1);
    const shell = clienteRoutes[0];
    expect(shell.path).toBe('');
    expect(shell.canActivate).toHaveLength(2);
    expect(shell.children?.map((r) => r.path)).toEqual([
      'mi-estadia',
      'mi-estadia/catalogo',
      'mi-estadia/carrito',
      'mis-ventas',
      'mis-ventas/:id',
      'mi-perfil',
    ]);
    expect(shell.children?.every((r) => typeof r.loadComponent === 'function')).toBe(true);
    expect(routes.some((r) => r === shell)).toBe(true);
  });

  it('C entra a /mi-estadia y A/E a /app/inicio', () => {
    TestBed.configureTestingModule({
      providers: [
        SessionService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigateByUrl: vi.fn() } },
      ],
    });
    const session = TestBed.inject(SessionService);
    session.setSession(
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
    expect(session.homeUrl()).toBe('/mi-estadia');

    session.setSession(
      {
        idPersona: 1,
        nombre: 'Admin',
        apellido: 'Demo',
        correo: 'admin@hotel.test',
        tipoPersona: 'Administrador',
        token: tokenConSub(1),
        expiresIn: 900,
        sedes: [],
      },
      { ...PERSONA_CLIENTE, idPersona: 1, tipoPersona: 'Administrador' }
    );
    expect(session.homeUrl()).toBe('/app/inicio');
  });
});
