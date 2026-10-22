import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { InicioPage } from './inicio.page';

describe('InicioPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [InicioPage],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  function crear(): HTMLElement {
    const fixture = TestBed.createComponent(InicioPage);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('muestra el h1 y la propuesta de valor exactos, sin etiqueta encima', () => {
    const element = crear();

    expect(element.querySelector('h1')?.textContent?.trim()).toBe(
      'Casa Sillar en Lima y Arequipa',
    );
    expect(element.querySelector('.hero__valor')?.textContent?.trim()).toBe(
      'Catálogo, sedes y contacto; sin reservas ni pagos en línea',
    );
    expect(element.querySelector('.hero h1')?.previousElementSibling).toBeNull();
    expect(element.querySelector('.hero__etiqueta')).toBeNull();
  });

  it('ofrece las dos acciones del hero y el aviso de tarifa completo', () => {
    const element = crear();
    const acciones = Array.from(element.querySelectorAll('.hero__acciones a')).map(
      (node) => (node.textContent ?? '').trim(),
    );

    expect(acciones).toEqual(['Contactar', 'Ver sedes']);
    expect(element.textContent).toContain(
      'Desde S/ 70.00 por noche. Consulta disponibilidad con recepción. No constituye una reserva.',
    );
  });

  it('usa la fotografía del hero con prioridad alta y su alt propio', () => {
    const element = crear();
    const imagen = element.querySelector('.hero__imagen') as HTMLImageElement;

    expect(imagen.getAttribute('src')).toBe('/assets/inicio/hero.jpg');
    expect(imagen.getAttribute('fetchpriority')).toBe('high');
    expect(imagen.getAttribute('alt')).toBeTruthy();
    expect(imagen.getAttribute('alt')).not.toContain('demostración');
  });

  it('no deja textos de demostración ni ofrece reserva, pago o disponibilidad en vivo', () => {
    const element = crear();
    const texto = (element.textContent ?? '').toLowerCase();
    const acciones = Array.from(element.querySelectorAll('a, button')).map(
      (node) => (node.textContent ?? '').toLowerCase(),
    );

    expect(texto).not.toContain('demostración');
    expect(texto).not.toContain('hotel demo');
    expect(acciones.some((label) => label.includes('reservar'))).toBe(false);
    expect(acciones.some((label) => label.includes('pagar'))).toBe(false);
    expect(texto).not.toContain('disponible ahora');
  });
});
