import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../api.config';
import { ClienteContextService } from './cliente-context.service';
import { ClienteService } from './cliente.service';
import { SessionService } from './session.service';
import {
  envelopePage,
  PERSONA_CLIENTE,
  recepcion,
  tokenConSub,
} from '../../features/cliente/testing/cliente.fixtures';

describe('ClienteContextService', () => {
  let contexto: ClienteContextService;
  let session: SessionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        SessionService,
        ClienteService,
        ClienteContextService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigateByUrl: vi.fn() } },
      ],
    });
    contexto = TestBed.inject(ClienteContextService);
    session = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
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
  });

  afterEach(() => http.verify());

  it('usa el sub del JWT como idCliente y expone la estadía ACTIVA', async () => {
    const promesa = contexto.cargar();
    const req = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/recepcion/cliente/100` &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '100'
    );
    expect(contexto.idCliente()).toBe(100);
    req.flush(
      envelopePage(
        [recepcion({ idRecepcion: 1002, estadoEstadia: 'CERRADA' }), recepcion()],
        0,
        100,
        2
      )
    );
    await promesa;

    expect(contexto.activa()?.idRecepcion).toBe(1001);
    expect(contexto.estadias()).toHaveLength(2);
  });

  it('sin sub cae en el idPersona de la sesión y no llama con un id inventado', async () => {
    session.setSession(
      {
        idPersona: 100,
        nombre: 'Cliente',
        apellido: 'Uno',
        correo: 'cliente.uno@hotel.test',
        tipoPersona: 'Cliente',
        token: 'token-sin-payload',
        expiresIn: 900,
        sedes: [],
      },
      PERSONA_CLIENTE
    );
    const promesa = contexto.cargar();
    const req = http.expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/cliente/100`);
    req.flush(envelopePage([], 0, 100, 0));
    await promesa;
    expect(contexto.activa()).toBeNull();
  });
});
