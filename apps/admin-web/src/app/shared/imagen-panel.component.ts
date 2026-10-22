import { Component, computed, effect, input, output, signal, untracked } from '@angular/core';

/** Fallback neutro propio del panel: fondo Sillar piedra con la marca de imagen. */
export const IMAGEN_FALLBACK = '/assets/fallback.svg';

/** Texto exacto de [`08` §6](../../../../docs/especificacion/08-admin-web.md#6-imágenes-en-el-panel). */
export const TEXTO_IMAGEN_ROTA = 'No se pudo cargar la imagen';

/**
 * Imagen del panel con relación reservada, `alt` obligatorio y fallback neutro.
 *
 * - La relación la declara quien la usa con `relacion` (`'3 / 2'`, `'4 / 3'`,
 *   `'1 / 1'`…); el contenedor la reserva con `aspect-ratio`, de modo que no
 *   hay salto de layout ni al cargar ni al fallar.
 * - Mientras carga pinta un esqueleto con la misma relación.
 * - `loading="lazy"` salvo en la imagen principal (`prioritaria`), que va con
 *   `fetchpriority="high"`.
 * - `decorativa` marca la imagen `aria-hidden`: el hueco de adorno no se
 *   anuncia. Toda miniatura con significado lleva `alt` propio.
 * - Un fallo de carga —o un `src` vacío— cae al fallback neutro y emite
 *   `fallo`, para que el formulario muestre el texto junto al campo. Nunca
 *   borra el valor escrito: el componente no toca el modelo.
 */
@Component({
  selector: 'app-imagen-panel',
  standalone: true,
  template: `
    <div class="imagen-panel" [style.aspect-ratio]="relacion()">
      @if (cargando()) {
        <div class="imagen-panel__esqueleto skeleton" aria-hidden="true"></div>
      }
      <img
        class="imagen-panel__imagen"
        [class.imagen-panel__imagen--fallback]="haFallado()"
        [class.imagen-panel__imagen--oculta]="cargando()"
        [src]="actual()"
        [attr.alt]="decorativa() ? '' : alt()"
        [attr.aria-hidden]="decorativa() ? 'true' : null"
        [attr.loading]="prioritaria() ? 'eager' : 'lazy'"
        [attr.fetchpriority]="prioritaria() ? 'high' : 'auto'"
        [attr.decoding]="prioritaria() ? 'sync' : 'async'"
        (load)="alCargar()"
        (error)="alFallar()"
      />
    </div>
  `,
  styles: [
    `
      :host {
        display: block;
        min-width: 0;
      }

      .imagen-panel {
        position: relative;
        width: 100%;
        overflow: hidden;
        border-radius: var(--radius-control);
        background: var(--color-sillar-piedra);
      }

      .imagen-panel__esqueleto {
        position: absolute;
        inset: 0;
        margin: 0;
        border-radius: 0;
      }

      .imagen-panel__imagen {
        display: block;
        width: 100%;
        height: 100%;
        object-fit: cover;
      }

      .imagen-panel__imagen--oculta {
        opacity: 0;
      }

      .imagen-panel__imagen--fallback {
        object-fit: contain;
        padding: var(--space-2);
      }
    `,
  ],
})
export class ImagenPanelComponent {
  /** URL a mostrar. `null` o vacía pinta directamente el fallback neutro. */
  readonly src = input<string | null | undefined>(null);
  /** Texto alternativo: obligatorio salvo en las imágenes de adorno. */
  readonly alt = input<string>('');
  /** Relación reservada en el contenedor, con el formato de `aspect-ratio`. */
  readonly relacion = input<string>('4 / 3');
  /** Imagen principal: carga con prioridad alta en lugar de diferida. */
  readonly prioritaria = input(false);
  /** Imagen de adorno: `aria-hidden` y sin `alt` anunciado. */
  readonly decorativa = input(false);

  /** `true` cuando no hay URL o la imagen del contrato no cargó. */
  readonly fallo = output<boolean>();

  private readonly fallida = signal(false);
  private readonly enCurso = signal(true);

  protected readonly haFallado = computed(() => this.sinFuente() || this.fallida());
  protected readonly actual = computed(() =>
    this.haFallado() ? IMAGEN_FALLBACK : (this.src() as string)
  );
  protected readonly cargando = computed(() => !this.haFallado() && this.enCurso());

  private sinFuente(): boolean {
    return !(this.src() ?? '').trim();
  }

  constructor() {
    // Una URL nueva vuelve a empezar: sin esto, una imagen que falló una vez
    // se quedaría en el fallback aunque el usuario corrigiese el campo.
    effect(() => {
      this.src();
      untracked(() => {
        this.fallida.set(false);
        this.enCurso.set(!this.sinFuente());
      });
    });
  }

  protected alCargar(): void {
    this.enCurso.set(false);
  }

  protected alFallar(): void {
    if (this.sinFuente() || this.fallida()) {
      this.enCurso.set(false);
      return;
    }
    this.fallida.set(true);
    this.enCurso.set(false);
    this.fallo.emit(true);
  }
}
