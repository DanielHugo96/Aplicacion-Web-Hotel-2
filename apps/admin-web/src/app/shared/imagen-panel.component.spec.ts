import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import {
  IMAGEN_FALLBACK,
  ImagenPanelComponent,
  TEXTO_IMAGEN_ROTA,
} from './imagen-panel.component';

describe('ImagenPanelComponent — imagen del panel con fallback neutro', () => {
  let fixture: ComponentFixture<ImagenPanelComponent>;

  function img(): HTMLImageElement {
    return (fixture.nativeElement as HTMLElement).querySelector('img') as HTMLImageElement;
  }

  function contenedor(): HTMLElement {
    return (fixture.nativeElement as HTMLElement).querySelector('.imagen-panel') as HTMLElement;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ImagenPanelComponent] });
    fixture = TestBed.createComponent(ImagenPanelComponent);
  });

  it('reserva la relación declarada y la conserva cuando la imagen falla', () => {
    fixture.componentRef.setInput('src', '/assets/sedes/lima-centro-fachada.jpg');
    fixture.componentRef.setInput('alt', 'Fachada de la sede de Lima Centro');
    fixture.componentRef.setInput('relacion', '3 / 2');
    fixture.detectChanges();

    expect(contenedor().style.aspectRatio).toBe('3 / 2');

    img().dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(contenedor().style.aspectRatio).toBe('3 / 2');
    expect(img().getAttribute('src')).toBe(IMAGEN_FALLBACK);
    expect(img().classList.contains('imagen-panel__imagen--fallback')).toBe(true);
  });

  it('cae al fallback neutro cuando la imagen falla y lo anuncia', () => {
    const fallos: boolean[] = [];
    fixture.componentInstance.fallo.subscribe((v) => fallos.push(v));
    fixture.componentRef.setInput('src', '/assets/productos/rota.png');
    fixture.componentRef.setInput('alt', 'Producto Agua 500ml');
    fixture.detectChanges();

    expect(img().getAttribute('src')).toBe('/assets/productos/rota.png');
    img().dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(img().getAttribute('src')).toBe(IMAGEN_FALLBACK);
    expect(fallos).toEqual([true]);
    expect(TEXTO_IMAGEN_ROTA).toBe('No se pudo cargar la imagen');
  });

  it('sin URL pinta el fallback sin pedir ninguna imagen', () => {
    fixture.componentRef.setInput('src', null);
    fixture.componentRef.setInput('alt', 'Sede sin foto');
    fixture.detectChanges();

    expect(img().getAttribute('src')).toBe(IMAGEN_FALLBACK);
    expect(fixture.nativeElement.querySelector('.imagen-panel__esqueleto')).toBeNull();
  });

  it('mantiene el alt y no lo anuncia cuando la imagen es de adorno', () => {
    fixture.componentRef.setInput('src', '/assets/productos/snack.png');
    fixture.componentRef.setInput('alt', 'Producto Snack');
    fixture.detectChanges();

    expect(img().getAttribute('alt')).toBe('Producto Snack');
    expect(img().getAttribute('aria-hidden')).toBeNull();

    fixture.componentRef.setInput('decorativa', true);
    fixture.detectChanges();

    expect(img().getAttribute('alt')).toBe('');
    expect(img().getAttribute('aria-hidden')).toBe('true');
  });

  it('difiere la carga salvo en la imagen principal', () => {
    fixture.componentRef.setInput('src', '/assets/habitaciones/lima-centro-doble-1.jpg');
    fixture.componentRef.setInput('alt', 'Habitación doble');
    fixture.detectChanges();

    expect(img().getAttribute('loading')).toBe('lazy');
    expect(img().getAttribute('fetchpriority')).toBe('auto');

    fixture.componentRef.setInput('prioritaria', true);
    fixture.detectChanges();

    expect(img().getAttribute('loading')).toBe('eager');
    expect(img().getAttribute('fetchpriority')).toBe('high');
  });

  it('muestra el esqueleto con la misma relación hasta que la imagen carga', () => {
    fixture.componentRef.setInput('src', '/assets/sedes/arequipa-fachada.jpg');
    fixture.componentRef.setInput('alt', 'Fachada de la sede de Arequipa');
    fixture.componentRef.setInput('relacion', '21 / 9');
    fixture.detectChanges();

    const esqueleto = (fixture.nativeElement as HTMLElement).querySelector(
      '.imagen-panel__esqueleto'
    ) as HTMLElement;
    expect(esqueleto).not.toBeNull();
    expect(contenedor().style.aspectRatio).toBe('21 / 9');

    img().dispatchEvent(new Event('load'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.imagen-panel__esqueleto')).toBeNull();
  });

  it('vuelve a intentar cuando cambia la URL tras un fallo', () => {
    fixture.componentRef.setInput('src', '/assets/productos/rota.png');
    fixture.componentRef.setInput('alt', 'Producto roto');
    fixture.detectChanges();
    img().dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(img().getAttribute('src')).toBe(IMAGEN_FALLBACK);

    fixture.componentRef.setInput('src', '/assets/productos/snack.png');
    fixture.detectChanges();

    expect(img().getAttribute('src')).toBe('/assets/productos/snack.png');
  });
});
