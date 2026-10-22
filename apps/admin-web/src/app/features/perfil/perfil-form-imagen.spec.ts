import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../core/services/auth.service';
import { ETagCacheService } from '../../core/services/etag-cache.service';
import { SessionService } from '../../core/services/session.service';
import { IMAGEN_FALLBACK } from '../../shared/imagen-panel.component';
import { envelopeSingle, PERSONA_CLIENTE, tokenConSub } from '../cliente/testing/cliente.fixtures';
import { PerfilFormComponent } from './perfil-form.component';

describe('PerfilFormComponent — foto actual y vista previa', () => {
  let fixture: ComponentFixture<PerfilFormComponent>;

  async function microtasks(): Promise<void> {
    await new Promise((r) => setTimeout(r, 0));
    await new Promise((r) => setTimeout(r, 0));
  }

  async function escribirFoto(valor: string): Promise<void> {
    const input = (fixture.nativeElement as HTMLElement).querySelector(
      '#pfFotoUrl'
    ) as HTMLInputElement;
    input.value = valor;
    input.dispatchEvent(new Event('input'));
    await microtasks();
    fixture.detectChanges();
  }

  async function crear(persona = PERSONA_CLIENTE): Promise<void> {
    TestBed.resetTestingModule();
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
    TestBed.inject(SessionService).setSession(
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
      persona
    );
    fixture = TestBed.createComponent(PerfilFormComponent);
    fixture.componentRef.setInput('persona', persona);
    fixture.detectChanges();
    return new Promise<void>((resolve) => setTimeout(resolve, 0));
  }

  function img(): HTMLImageElement {
    return (fixture.nativeElement as HTMLElement).querySelector(
      'app-imagen-panel img'
    ) as HTMLImageElement;
  }

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('pinta la foto actual del perfil con el nombre como nombre accesible', async () => {
    await crear({ ...PERSONA_CLIENTE, fotoUrl: '/assets/perfiles/cliente-uno.jpg' });

    expect(img().getAttribute('src')).toBe('/assets/perfiles/cliente-uno.jpg');
    expect(img().getAttribute('alt')).toBe('Foto de Cliente Uno');
  });

  it('sin fotoUrl deja el hueco «Sin foto» y no pide ninguna imagen', async () => {
    await crear({ ...PERSONA_CLIENTE, fotoUrl: null });

    expect(fixture.nativeElement.querySelector('app-imagen-panel')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Sin foto');
  });

  it('previsualiza la nueva foto en cuanto la URL es válida', async () => {
    await crear({ ...PERSONA_CLIENTE, fotoUrl: null });
    await escribirFoto('/assets/perfiles/cliente-uno.jpg');

    expect(img().getAttribute('src')).toBe('/assets/perfiles/cliente-uno.jpg');
  });

  it('con una foto rota muestra el texto junto al campo y conserva el valor', async () => {
    await crear({ ...PERSONA_CLIENTE, fotoUrl: '/assets/perfiles/inexistente.jpg' });
    await microtasks();
    fixture.detectChanges();

    img().dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(img().getAttribute('src')).toBe(IMAGEN_FALLBACK);
    expect(fixture.nativeElement.textContent).toContain('No se pudo cargar la imagen');
    const input = (fixture.nativeElement as HTMLElement).querySelector(
      '#pfFotoUrl'
    ) as HTMLInputElement;
    expect(input.value).toBe('/assets/perfiles/inexistente.jpg');
    expect(input.classList.contains('error')).toBe(false);
  });

  it('una URL no permitida no pinta vista previa', async () => {
    await crear({ ...PERSONA_CLIENTE, fotoUrl: null });
    await escribirFoto('http://ejemplo.test/foto.jpg');

    expect(fixture.nativeElement.querySelector('app-imagen-panel')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('URL no permitida');
  });
});
