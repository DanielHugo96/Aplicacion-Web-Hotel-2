import { Component, input, signal } from '@angular/core';

const FALLBACK = '/assets/demo/placeholder.svg';

/**
 * Imagen de demostración con relación fija, alt obligatorio y fallback neutro.
 * Un fallo de imagen no altera el layout ni la tarifa.
 */
@Component({
  selector: 'app-demo-image',
  template: `
    <img
      class="imagen"
      [class.imagen--fallback]="fallo()"
      [src]="actual()"
      [alt]="alt()"
      [attr.loading]="prioritaria() ? 'eager' : 'lazy'"
      [attr.fetchpriority]="prioritaria() ? 'high' : 'auto'"
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
        aspect-ratio: 4 / 3;
        object-fit: cover;
        display: block;
      }

      .imagen--fallback {
        object-fit: contain;
        padding: var(--space-6);
      }
    `,
  ],
})
export class DemoImageComponent {
  readonly src = input.required<string>();
  readonly alt = input.required<string>();
  readonly prioritaria = input(false);

  protected readonly fallo = signal(false);

  protected actual(): string {
    return this.fallo() ? FALLBACK : this.src();
  }

  protected alFallar(): void {
    this.fallo.set(true);
  }
}
