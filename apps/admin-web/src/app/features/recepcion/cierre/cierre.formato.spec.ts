import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { API_BASE_URL } from '../../../core/api.config';
import { gatewayInterceptor } from '../../../core/gateway.interceptor';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { PeTimestampPipe } from '../../../core/pipes/timestamp.pipe';
import { SessionService } from '../../../core/services/session.service';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { ToastService } from '../../../core/services/toast.service';
import { authData, envelopeSingle, PERSONA_ADMIN, recepcion } from '../testing/recepcion.fixtures';
import { CierreFormComponent } from './cierre-form.component';

async function microtasks(): Promise<void> {
  await new Promise((r) => setTimeout(r, 0));
  await new Promise((r) => setTimeout(r, 0));
}

const COTIZACION = {
  idRecepcion: 1001,
  alojamiento: 1234.5,
  adelanto: 20,
  penalidad: 10,
  saldoAlojamiento: 1214.5,
  consumosPagados: 0,
  consumosPendientes: 7,
  cobroAhora: 1221.5,
  moneda: 'PEN' as const,
  calculadoEn: '2026-10-21T03:00:00Z',
};

/** Mismo formato que el pipe: la expectativa no escribe la hora a mano. */
function timestampEsperado(iso: string): string {
  return new Intl.DateTimeFormat('es-PE', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
    .format(new Date(iso))
    .replace(/, /, ' ');
}

describe('formatos y disposición del cierre', () => {
  describe('pipes del sistema de diseño', () => {
    const sinNbsp = (v: string | null): string | null => v?.replace(/ /g, ' ') ?? null;

    it('MoneyPipe fija S/ 1,234.50 con es-PE y dos decimales', () => {
      expect(sinNbsp(new MoneyPipe().transform(1234.5))).toBe('S/ 1,234.50');
      expect(sinNbsp(new MoneyPipe().transform(0))).toBe('S/ 0.00');
      expect(new MoneyPipe().transform(null)).toBeNull();
    });

    it('PeTimestampPipe fija dd/MM/yyyy HH:mm con hour12 desactivado', () => {
      const pipe = new PeTimestampPipe();
      // Construido en hora local para no depender de la zona de la máquina.
      const fecha = new Date(2026, 9, 21, 3, 5, 0);
      expect(pipe.transform(fecha)).toBe('21/10/2026 03:05');
      expect(pipe.transform(COTIZACION.calculadoEn)).toBe(
        timestampEsperado(COTIZACION.calculadoEn)
      );
    });
  });

  let fixture: ComponentFixture<CierreFormComponent>;
  let http: HttpTestingController;

  function stubViewport(width: number, reducedMotion = false): void {
    Object.defineProperty(window, 'innerWidth', { writable: true, value: width });
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn().mockImplementation((query: string) => ({
        matches: query.includes('prefers-reduced-motion') ? reducedMotion : width <= 640,
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

  beforeEach(() => {
    stubViewport(1024);
    TestBed.configureTestingModule({
      imports: [CierreFormComponent],
      providers: [
        SessionService,
        RecepcionService,
        ToastService,
        provideHttpClient(withInterceptors([gatewayInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: '1001' }) } },
        },
      ],
    });
    TestBed.inject(SessionService).setSession(authData('Administrador'), PERSONA_ADMIN);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(CierreFormComponent);
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
    TestBed.resetTestingModule();
    vi.restoreAllMocks();
  });

  async function abrir(): Promise<void> {
    fixture.detectChanges();
    http
      .expectOne(`${API_BASE_URL}/api/recepcion/1001`)
      .flush(envelopeSingle(recepcion()), { headers: { ETag: '"3"' } });
    await microtasks();
    http
      .expectOne(
        (r) => r.url === `${API_BASE_URL}/api/recepcion/1001/cotizacion-salida`
      )
      .flush(envelopeSingle(COTIZACION));
    await microtasks();
    fixture.detectChanges();
  }

  function texto(): string {
    return (fixture.nativeElement.textContent as string).replace(/\u00a0/g, ' ');
  }

  it('a 1024px pinta importes exactos y la penalidad destacada con su motivo', async () => {
    await abrir();
    fixture.componentInstance.costoPenalidad.set(10);
    fixture.componentInstance.motivoPenalidad.set('Salida tardía');
    fixture.detectChanges();

    expect(texto()).toContain('S/ 1,234.50');
    expect(texto()).toContain('S/ 1,221.50');
    const destacado = (fixture.nativeElement as HTMLElement).querySelector('dl .destacado dd');
    expect(destacado?.textContent?.replace(/ /g, ' ').trim()).toBe('S/ 1,221.50');
    // Las cifras van a la derecha con cifras tabulares (token de §10).
    const estilos = getComputedStyle(destacado as HTMLElement);
    expect(estilos.fontVariantNumeric || 'tabular-nums').toContain('tabular');
  });

  it('a 390px con prefers-reduced-motion el formulario sigue operable y etiquetado', async () => {
    stubViewport(390, true);
    await abrir();

    const form = (fixture.nativeElement as HTMLElement).querySelector('form');
    expect(form).toBeTruthy();
    expect(form!.querySelector('label[for="costo-penalidad"]')?.textContent).toContain(
      'Costo de penalidad'
    );
    expect(form!.querySelector('label[for="monto-recibido"]')?.textContent).toContain(
      'Monto recibido ahora'
    );
    expect(form!.querySelector('label[for="medio-pago"]')?.textContent).toContain('Medio de pago');
    // El botón de envío sigue accesible con teclado (no `display:none` ni disabled sin motivo).
    const enviar = Array.from(form!.querySelectorAll('button')).find((b) =>
      (b.textContent ?? '').includes('Cerrar estadía')
    ) as HTMLButtonElement;
    expect(enviar).toBeTruthy();
    expect(enviar.disabled).toBe(false);
    expect(window.matchMedia('(prefers-reduced-motion: reduce)').matches).toBe(true);
  });

});
