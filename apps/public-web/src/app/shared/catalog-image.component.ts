import { Component, input, signal } from '@angular/core';

/** Fallback neutro propio de la app: fondo Sillar piedra con la marca de imagen. */
export const IMAGEN_FALLBACK = '/assets/fallback.svg';

/**
 * Imagen del catálogo público con relación fija, `alt` obligatorio y fallback
 * neutro. La relación la declara el contenedor (`aspect-ratio` en el `:host`) y
 * la imagen la ocupa con `object-fit: cover`; un fallo de imagen no altera el
 * layout, el texto ni la tarifa.
 */
@Component({
  selector: 'app-catalog-image',
  template: `
    <img
      class="imagen"
      [class.imagen--fallback]="fallo()"
      [src]="actual()"
      [alt]="alt()"
      [attr.loading]="prioritaria() ? 'eager' : 'lazy'"
      [attr.fetchpriority]="prioritaria() ? 'high' : 'auto'"
      [attr.decoding]="prioritaria() ? 'sync' : 'async'"
      (error)="alFallar()"
    />
  `,
  styles: [
    `
      :host {
        display: block;
        overflow: hidden;
        background: var(--color-sillar-piedra);
      }

      .imagen {
        width: 100%;
        height: 100%;
        object-fit: cover;
        display: block;
      }

      .imagen--fallback {
        object-fit: contain;
        padding: var(--space-8);
      }
    `,
  ],
})
export class CatalogImageComponent {
  readonly src = input.required<string>();
  readonly alt = input.required<string>();
  readonly prioritaria = input(false);

  protected readonly fallo = signal(false);

  protected actual(): string {
    return this.fallo() ? IMAGEN_FALLBACK : this.src();
  }

  protected alFallar(): void {
    this.fallo.set(true);
  }
}
