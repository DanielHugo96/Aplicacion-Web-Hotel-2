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

  it('muestra los textos obligatorios del hero y la llamada a contacto', () => {
    const fixture = TestBed.createComponent(InicioPage);
    fixture.detectChanges();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('Hoteles de demostración en Lima y Arequipa');
    expect(text).toContain(
      'Catálogo, sedes y contacto; sin reservas ni pagos en línea',
    );
    expect(text).toContain('Contactar');
    expect(text).toContain(
      'Desde S/ 70.00 por noche. Consulta disponibilidad con recepción. No constituye una reserva.',
    );
  });

  it('no ofrece acciones de reserva, pago ni disponibilidad en vivo', () => {
    const fixture = TestBed.createComponent(InicioPage);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    const actions = Array.from(
      element.querySelectorAll('a, button'),
    ).map((node) => (node.textContent ?? '').toLowerCase());

    expect(actions.some((label) => label.includes('reservar'))).toBe(false);
    expect(actions.some((label) => label.includes('pagar'))).toBe(false);
    expect((element.textContent ?? '').toLowerCase()).not.toContain(
      'disponible ahora',
    );
  });
});
