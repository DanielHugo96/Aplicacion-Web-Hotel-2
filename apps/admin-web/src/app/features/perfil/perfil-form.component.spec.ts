import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL, API_PATHS } from '../../core/api.config';
import { AuthService } from '../../core/services/auth.service';
import { ETagCacheService } from '../../core/services/etag-cache.service';
import { SessionService } from '../../core/services/session.service';
import { envelopeSingle, PERSONA_CLIENTE, tokenConSub } from '../cliente/testing/cliente.fixtures';
import { PerfilFormComponent } from './perfil-form.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

describe('PerfilFormComponent (área de cliente)', () => {
  let fixture: ComponentFixture<PerfilFormComponent>;
  let http: HttpTestingController;
  let etags: ETagCacheService;
  let session: SessionService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PerfilFormComponent],
      providers: [
        SessionService,
        AuthService,
        ETagCacheService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigateByUrl: vi.fn() } },
      ],
    });
    session = TestBed.inject(SessionService);
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
    etags = TestBed.inject(ETagCacheService);
    etags.set(API_PATHS.authMe, '"1"');
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PerfilFormComponent);
    fixture.componentRef.setInput('persona', PERSONA_CLIENTE);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('envía solo nombre, apellido y fotoUrl y refresca con el ETag nuevo', async () => {
    const actualizada = { ...PERSONA_CLIENTE, nombre: 'Cliente Editado', version: 2 };
    const promesa = fixture.componentInstance.guardar();
    const req = http.expectOne(`${API_BASE_URL}${API_PATHS.authMe}`);
    expect(req.request.method).toBe('PUT');
    expect(Object.keys(req.request.body).sort()).toEqual(
      ['apellido', 'fotoUrl', 'nombre'].sort()
    );
    expect(req.request.headers.get('If-Match')).toBe('"1"');
    req.flush(envelopeSingle(actualizada), { headers: { ETag: '"2"' } });
    await promesa;
    await microtasks();
    fixture.detectChanges();

    expect(fixture.componentInstance.ok).toBe(true);
    expect(fixture.componentInstance.etag).toBe('"2"');
    expect(session.persona()?.nombre).toBe('Cliente Editado');
  });

  it('un 412 no sobrescribe y ofrece recargar', async () => {
    const promesa = fixture.componentInstance.guardar();
    http.expectOne(`${API_BASE_URL}${API_PATHS.authMe}`).flush(
      {
        success: false,
        message: 'Otra persona modificó este registro. Recarga para ver los cambios.',
        data: null,
        error: { code: 'PRECONDITION_FAILED', fields: {} },
        meta: { correlationId: 'cid-412' },
      },
      { status: 412, statusText: 'Precondition Failed' }
    );
    await promesa;
    await microtasks();
    fixture.detectChanges();

    expect(fixture.componentInstance.error?.code).toBe('PRECONDITION_FAILED');
    expect(fixture.componentInstance.accionEtiqueta()).toBe('Recargar');
    expect(fixture.nativeElement.textContent).toContain(
      'Otra persona modificó este registro. Recarga para ver los cambios.'
    );
    expect(fixture.nativeElement.textContent).toContain('cid-412');
  });

  it('un 428 se resuelve recargando el recurso antes de reenviar', async () => {
    const promesa = fixture.componentInstance.guardar();
    http.expectOne(`${API_BASE_URL}${API_PATHS.authMe}`).flush(
      {
        success: false,
        message: 'Falta la versión del registro.',
        data: null,
        error: { code: 'PRECONDITION_REQUIRED', fields: {} },
        meta: { correlationId: 'cid-428' },
      },
      { status: 428, statusText: 'Precondition Required' }
    );
    await microtasks();

    const recarga = http.expectOne(`${API_BASE_URL}${API_PATHS.authMe}`);
    expect(recarga.request.method).toBe('GET');
    recarga.flush(envelopeSingle(PERSONA_CLIENTE), { headers: { ETag: '"2"' } });
    await promesa;
    await microtasks();
    fixture.detectChanges();

    expect(fixture.componentInstance.error?.code).toBe('PRECONDITION_REQUIRED');
    expect(fixture.componentInstance.accionEtiqueta()).toBe('Guardar cambios');
    expect(fixture.componentInstance.etag).toBe('"2"');
  });
});
