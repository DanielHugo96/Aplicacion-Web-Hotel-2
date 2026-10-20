import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL, API_PATHS } from '../../core/api.config';
import { AuthService } from '../../core/services/auth.service';
import { SessionService } from '../../core/services/session.service';
import { envelopeSingle } from '../cliente/testing/cliente.fixtures';
import { PasswordChangeComponent } from './password-change.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

describe('PasswordChangeComponent', () => {
  let fixture: ComponentFixture<PasswordChangeComponent>;
  let http: HttpTestingController;
  let componente: PasswordChangeComponent;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PasswordChangeComponent],
      providers: [
        AuthService,
        SessionService,
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Router, useValue: { navigateByUrl: vi.fn() } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PasswordChangeComponent);
    componente = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('cuenta bytes UTF-8 y exige 12–72 y distinta de la actual', () => {
    componente.claveActual = 'actual-segura';
    componente.claveNueva = 'ñ'.repeat(5);
    expect(componente.claveNuevaBytes()).toBe(10);
    expect(componente.claveNuevaValida()).toBe(false);

    componente.claveNueva = 'ñ'.repeat(6);
    expect(componente.claveNuevaBytes()).toBe(12);
    expect(componente.claveNuevaValida()).toBe(true);
    expect(componente.diferenteDeActual()).toBe(true);

    componente.claveNueva = 'actual-segura';
    expect(componente.diferenteDeActual()).toBe(false);
    expect(componente.formularioValido()).toBe(false);
  });

  it('envía solo las dos claves, bloquea el doble envío y no expone la clave', async () => {
    componente.claveActual = 'actual-segura';
    componente.claveNueva = 'nueva-clave-segura';
    const primera = componente.guardar();
    const segunda = componente.guardar();

    const coincidencias = http.match(
      (r) => r.url === `${API_BASE_URL}${API_PATHS.authPassword}`
    );
    expect(coincidencias.length).toBe(1);
    const req = coincidencias[0];
    expect(req.request.method).toBe('PUT');
    expect(Object.keys(req.request.body).sort()).toEqual(['claveActual', 'claveNueva'].sort());
    expect(req.request.body.claveNueva).toBe('nueva-clave-segura');

    req.flush(envelopeSingle(null), { status: 200, statusText: 'OK' });
    await Promise.all([primera, segunda]);
    await microtasks();
    fixture.detectChanges();

    expect(componente.ok).toBe(true);
    const texto = fixture.nativeElement.textContent as string;
    expect(texto).toContain('Contraseña actualizada');
    expect(texto).not.toContain('nueva-clave-segura');
  });

  it('un 400 PASSWORD_POLICY resalta claveNueva con el mensaje del catálogo', async () => {
    componente.claveActual = 'actual-segura';
    componente.claveNueva = 'nueva-clave-segura';
    const promesa = componente.guardar();
    http.expectOne(`${API_BASE_URL}${API_PATHS.authPassword}`).flush(
      {
        success: false,
        message: 'La contraseña debe tener entre 12 y 72 bytes (caracteres UTF-8).',
        data: null,
        error: { code: 'PASSWORD_POLICY', fields: { claveNueva: 'muy corta' } },
        meta: { correlationId: 'cid-policy' },
      },
      { status: 400, statusText: 'Bad Request' }
    );
    await promesa;
    fixture.detectChanges();

    expect(componente.campoInvalido('claveNueva')).toBe(true);
    expect(componente.mensajeCampo('claveNueva')).toBe('muy corta');
    expect(fixture.nativeElement.textContent).toContain(
      'La contraseña debe tener entre 12 y 72 bytes (caracteres UTF-8).'
    );
  });

  it('un 400 CURRENT_PASSWORD_MISMATCH resalta claveActual', async () => {
    componente.claveActual = 'actual-segura';
    componente.claveNueva = 'nueva-clave-segura';
    const promesa = componente.guardar();
    http.expectOne(`${API_BASE_URL}${API_PATHS.authPassword}`).flush(
      {
        success: false,
        message: 'La contraseña actual no es correcta.',
        data: null,
        error: { code: 'CURRENT_PASSWORD_MISMATCH', fields: {} },
        meta: { correlationId: 'cid-mismatch' },
      },
      { status: 400, statusText: 'Bad Request' }
    );
    await promesa;
    fixture.detectChanges();

    expect(componente.campoInvalido('claveActual')).toBe(true);
    expect(componente.mensajeCampo('claveActual')).toBe('La contraseña actual no es correcta.');
  });

  it('un 400 VALIDATION_ERROR pinta error.fields', async () => {
    componente.claveActual = 'actual-segura';
    componente.claveNueva = 'nueva-clave-segura';
    const promesa = componente.guardar();
    http.expectOne(`${API_BASE_URL}${API_PATHS.authPassword}`).flush(
      {
        success: false,
        message: 'Revisa los datos marcados.',
        data: null,
        error: { code: 'VALIDATION_ERROR', fields: { claveNueva: 'valor inválido' } },
        meta: { correlationId: 'cid-validation' },
      },
      { status: 400, statusText: 'Bad Request' }
    );
    await promesa;
    fixture.detectChanges();

    expect(componente.campoInvalido('claveNueva')).toBe(true);
    expect(componente.mensajeCampo('claveNueva')).toBe('valor inválido');
    expect(fixture.nativeElement.textContent).toContain('Revisa los datos marcados.');
  });
});
