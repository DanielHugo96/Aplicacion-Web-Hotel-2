import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { CATALOGO_ERRORS, ERROR_CATALOG, parseApiError } from '../../core/api-error';
import { ErrorBannerComponent } from './error-banner.component';

describe('ErrorBannerComponent', () => {
  it('muestra mensaje, acción, código y correlationId de cada código del catálogo', async () => {
    await TestBed.configureTestingModule({ imports: [ErrorBannerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ErrorBannerComponent);
    fixture.componentRef.setInput('actionLabel', 'Reintentar');
    fixture.detectChanges();

    for (const code of CATALOGO_ERRORS) {
      fixture.componentRef.setInput(
        'error',
        parseApiError({
          error: { error: { code, fields: {} }, meta: { correlationId: `cid-${code}` } },
        })
      );
      fixture.detectChanges();
      const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
      expect(text).toContain(ERROR_CATALOG[code].message);
      expect(text).toContain(ERROR_CATALOG[code].action);
      expect(text).toContain(code);
      expect(text).toContain(`cid-${code}`);
      expect(text).toContain('Reintentar');
    }
  });

  it('no renderiza nada sin error', async () => {
    await TestBed.configureTestingModule({ imports: [ErrorBannerComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ErrorBannerComponent);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.banner-error')).toBeNull();
  });
});
