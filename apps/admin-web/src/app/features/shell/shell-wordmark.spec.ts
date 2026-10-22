import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../../core/api.config';
import { SedeContextService } from '../../core/services/sede-context.service';
import { SessionService } from '../../core/services/session.service';
import { authData, envelopePage, PERSONA_ADMIN } from '../recepcion/testing/recepcion.fixtures';
import { hotel } from '../catalogo/testing/catalogo.fixtures';
import { ShellComponent } from './shell.component';

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

describe('ShellComponent — wordmark Casa Sillar', () => {
  let fixture: ComponentFixture<ShellComponent>;
  let http: HttpTestingController;

  beforeEach(() => {
    stubMatchMedia();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [
        SessionService,
        SedeContextService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), PERSONA_ADMIN);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ShellComponent);
    fixture.detectChanges();
    // El shell carga las sedes del selector; se resuelven para no dejar la app colgada.
    http
      .match((r) => r.url === `${API_BASE_URL}/api/hotel/listar`)
      .forEach((req) => req.flush(envelopePage([hotel()])));
  });

  afterEach(() => {
    http.match(() => true).forEach((req) => req.flush(envelopePage([], 0, 20, 0)));
    http.verify();
  });

  function raiz(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  it('escribe Casa en Marcellus y Sillar en Plex 600, con nombre accesible', () => {
    const word = raiz().querySelector('.brand-word') as HTMLElement;
    expect(word.getAttribute('aria-label')).toBe('Casa Sillar, panel');
    expect(word.querySelector('.brand-word__casa')?.textContent).toBe('Casa');
    expect(word.querySelector('.brand-word__sillar')?.textContent).toBe('Sillar');
    expect(raiz().textContent).not.toContain('Hotel Demo');
  });

  it('al colapsar se reduce a la inicial de la marca con nombre accesible', () => {
    fixture.componentInstance.toggle();
    fixture.detectChanges();

    const word = raiz().querySelector('.brand-word') as HTMLElement;
    expect(word.textContent?.trim()).toBe('C');
    // La inicial es de adorno: el nombre accesible lo da el aria-label del contenedor.
    expect(word.getAttribute('aria-label')).toBe('Casa Sillar, panel');
    expect(word.querySelector('span[aria-hidden="true"]')?.textContent).toBe('C');
    expect(raiz().querySelector('.brand-word__casa')).toBeNull();
  });

  it('no deja el wordmark anterior en el DOM del panel', () => {
    expect(raiz().textContent).not.toContain('Hotel Demo');
    expect(raiz().querySelector('.brand-word')?.getAttribute('aria-label')).toContain(
      'Casa Sillar'
    );
  });
});
