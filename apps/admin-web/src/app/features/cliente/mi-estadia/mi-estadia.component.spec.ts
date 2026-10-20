import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { ClienteContextService } from '../../../core/services/cliente-context.service';
import { ClienteService } from '../../../core/services/cliente.service';
import { SessionService } from '../../../core/services/session.service';
import {
  envelopePage,
  envelopeSingle,
  error404,
  PERSONA_CLIENTE,
  recepcion,
  tokenConSub,
} from '../testing/cliente.fixtures';
import { MiEstadiaComponent } from './mi-estadia.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

describe('MiEstadiaComponent', () => {
  let fixture: ComponentFixture<MiEstadiaComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      imports: [MiEstadiaComponent],
      providers: [
        SessionService,
        ClienteService,
        ClienteContextService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
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
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MiEstadiaComponent);
  });

  afterEach(() => http.verify());

  it('pide las estadías con el sub del JWT y muestra snapshots y fechas dd/MM/yyyy', async () => {
    fixture.detectChanges();
    const lista = http.expectOne(
      (r) =>
        r.url === `${API_BASE_URL}/api/recepcion/cliente/100` &&
        r.params.get('page') === '0' &&
        r.params.get('size') === '100'
    );
    lista.flush(envelopePage([recepcion()], 0, 100, 1));

    await microtasks();
    fixture.detectChanges();

    const detalle = http.expectOne(`${API_BASE_URL}/api/recepcion/1001`);
    detalle.flush(envelopeSingle(recepcion()), { headers: { ETag: '"3"' } });

    await microtasks();
    fixture.detectChanges();

    const texto = (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
    expect(texto).toContain('Habitación 101');
    expect(texto).toContain('Individual · Primer piso');
    expect(texto).toContain('04/10/2026');
    expect(texto).toContain('06/10/2026');
    expect(texto).toContain('Activa');
    expect(texto).toContain('S/ 70.00');
    expect(texto).toContain('EFECTIVO');
    expect(fixture.componentInstance.etag()).toBe('"3"');
  });

  it('un 404 de una estadía ajena muestra el mensaje del catálogo sin filtrar datos', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/cliente/100`)
      .flush(envelopePage([recepcion()], 0, 100, 1));

    await microtasks();
    fixture.detectChanges();

    http.expectOne(`${API_BASE_URL}/api/recepcion/1001`).flush(error404('cid-ajeno'), {
      status: 404,
      statusText: 'Not Found',
    });
    await microtasks();
    fixture.detectChanges();

    const texto = (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
    expect(texto).toContain('No encontramos lo que buscas.');
    expect(texto).toContain('cid-ajeno');
    expect(texto).not.toContain('Habitación 101');
    expect(texto).not.toContain('cliente.uno@hotel.test');
  });

  it('sin estadía ACTIVA muestra el vacío exacto y no pide el detalle', async () => {
    fixture.detectChanges();
    http
      .expectOne((r) => r.url === `${API_BASE_URL}/api/recepcion/cliente/100`)
      .flush(
        envelopePage(
          [recepcion({ estadoEstadia: 'CERRADA', estado: false, archivada: true })],
          0,
          100,
          1
        )
      );

    await microtasks();
    fixture.detectChanges();

    const texto = (fixture.nativeElement.textContent as string).replace(/ /g, ' ');
    expect(texto).toContain('No tienes una estadía activa; consulta en recepción');
    http.expectNone((r) => r.url === `${API_BASE_URL}/api/recepcion/1001`);
  });
});
