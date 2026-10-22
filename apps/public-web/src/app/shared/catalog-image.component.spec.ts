import { TestBed } from '@angular/core/testing';
import { IMAGEN_FALLBACK, CatalogImageComponent } from './catalog-image.component';

describe('CatalogImageComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CatalogImageComponent],
    }).compileComponents();
  });

  function crear(src: string, alt: string, prioritaria = false) {
    const fixture = TestBed.createComponent(CatalogImageComponent);
    fixture.componentRef.setInput('src', src);
    fixture.componentRef.setInput('alt', alt);
    fixture.componentRef.setInput('prioritaria', prioritaria);
    fixture.detectChanges();
    const img = fixture.nativeElement.querySelector('img') as HTMLImageElement;
    return { fixture, img };
  }

  it('mantiene el alt y cae al fallback neutro si la imagen falla', () => {
    const { fixture, img } = crear(
      '/assets/sedes/no-existe.jpg',
      'Fachada de la sede Lima Centro',
    );
    expect(img.getAttribute('alt')).toBe('Fachada de la sede Lima Centro');
    expect(img.getAttribute('src')).toBe('/assets/sedes/no-existe.jpg');

    img.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(img.getAttribute('src')).toBe(IMAGEN_FALLBACK);
    expect(IMAGEN_FALLBACK).toBe('/assets/fallback.svg');
    expect(img.getAttribute('alt')).toBe('Fachada de la sede Lima Centro');
    expect(img.classList.contains('imagen--fallback')).toBe(true);
  });

  it('no cambia la relación del contenedor cuando la imagen falla', () => {
    const { fixture } = crear(
      '/assets/habitaciones/no-existe.jpg',
      'Habitación doble de la sede Miraflores',
    );
    const host = fixture.nativeElement as HTMLElement;
    host.style.aspectRatio = '3 / 2';
    const antes = getComputedStyle(host).aspectRatio;

    const img = host.querySelector('img') as HTMLImageElement;
    img.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(getComputedStyle(host).aspectRatio).toBe(antes);
    expect(host.style.aspectRatio).toBe('3 / 2');
    expect((host.querySelector('img') as HTMLImageElement).style.objectFit || 'cover').toBe(
      'cover',
    );
  });

  it('carga con prioridad alta solo cuando se pide, y el resto en diferido', () => {
    const prioritaria = crear('/assets/inicio/hero.jpg', 'Patio de la cadena', true);
    expect(prioritaria.img.getAttribute('fetchpriority')).toBe('high');
    expect(prioritaria.img.getAttribute('loading')).toBe('eager');

    const diferida = crear('/assets/sedes/lima-centro-fachada.jpg', 'Fachada');
    expect(diferida.img.getAttribute('fetchpriority')).toBe('auto');
    expect(diferida.img.getAttribute('loading')).toBe('lazy');
  });
});
