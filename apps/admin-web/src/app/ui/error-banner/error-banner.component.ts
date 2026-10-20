import { Component, EventEmitter, Input, Output, signal } from '@angular/core';
import { ApiUiError } from '../../core/api-error';

/**
 * Franja de error del catálogo: mensaje para UI, código, acción esperada y
 * `correlationId` copiable. No redacta mensajes propios.
 */
@Component({
  selector: 'app-error-banner',
  standalone: true,
  templateUrl: './error-banner.component.html',
})
export class ErrorBannerComponent {
  @Input() error: ApiUiError | null = null;
  @Input() actionLabel: string | null = null;
  @Output() action = new EventEmitter<void>();

  readonly copied = signal(false);

  async copyCorrelation(): Promise<void> {
    const id = this.error?.correlationId;
    if (!id) return;
    try {
      await navigator.clipboard.writeText(id);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    } catch {
      this.copied.set(false);
    }
  }
}
