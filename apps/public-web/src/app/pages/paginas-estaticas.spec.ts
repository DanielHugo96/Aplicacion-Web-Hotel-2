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

  it('contacto no tiene formulario, no usa tel: y muestra datos demo como texto', () => {
    const fixture = TestBed.createComponent(ContactoPage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('form')).toBeNull();
    expect(element.querySelector('input, textarea, select')).toBeNull();
    expect(element.querySelector('a[href^="tel:"]')).toBeNull();
    expect(element.querySelector('a[href^="mailto:"]')?.textContent).toContain(
      'contacto@hotel.test',
    );
    expect(element.textContent).toContain('+51 900 000 001');
    expect(element.textContent).toContain('texto no clicable');
  });

  it('privacidad es un borrador enlazado a contacto', () => {
    const fixture = TestBed.createComponent(PrivacidadPage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.textContent).toContain('Borrador informativo');
    expect(element.textContent).toContain('No es una política legal definitiva');
    expect(element.querySelector('a[href="/contacto"]')).toBeTruthy();
  });
});
