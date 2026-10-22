import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ContactoPage } from './contacto.page';
import { PrivacidadPage } from './privacidad.page';

describe('Páginas estáticas', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('contacto no tiene formulario ni enlaces tel:/mailto: hacia el dominio ficticio', () => {
    const fixture = TestBed.createComponent(ContactoPage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('input, textarea, select')).toBeNull();
    expect(element.querySelector('a[href^="tel:"]')).toBeNull();
    expect(element.querySelector('a[href^="mailto:"]')).toBeNull();
    expect(element.innerHTML).not.toContain('mailto:');
    expect(element.textContent).toContain('contacto@casasillar.pe');
    expect(element.textContent).toContain('+51 1 428 6400');
    expect(element.textContent).toContain('texto');
    expect(element.textContent).not.toContain('demostración');
  });

  it('privacidad declara que la cadena y sus datos son de una presentación', () => {
    const fixture = TestBed.createComponent(PrivacidadPage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain('presentación');
    expect(element.textContent).toContain(
      'Los nombres, direcciones, teléfonos y correos son ficticios',
    );
    expect(element.textContent).not.toContain('cumplimiento');
    expect(element.textContent).not.toContain('política legal');
    expect(element.querySelector('a[href="/contacto"]')).toBeTruthy();
  });
});
