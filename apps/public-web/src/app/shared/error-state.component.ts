import { Component, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { UiError } from '../core/api-error';

/** Estado de error con mensaje del catálogo, acción esperada y correlación copiable. */
@Component({
  selector: 'app-error-state',
  imports: [RouterLink],
  template: `
    <div class="error" role="alert">
      <p class="error__message">{{ error().message }}</p>

      @if (campos().length > 0) {
        <ul class="error__campos">
          @for (campo of campos(); track campo) {
            <li><strong>{{ campo }}</strong>: {{ error().fields[campo] }}</li>
          }
        </ul>
      }

      @if (error().correlationId) {
        <p class="error__correlacion">
          Identificador: <code>{{ error().correlationId }}</code>
          <button type="button" class="error__copiar" (click)="copiar()">
            {{ copiado() ? 'Copiado' : 'Copiar' }}
          </button>
        </p>
      }

      <div class="error__acciones">
        @if (error().retryable) {
          <button type="button" class="boton" (click)="reintentar.emit()">
            Reintentar
          </button>
        }
        @if (backLink()) {
          <a class="boton boton--secundario" [routerLink]="backLink()">
            {{ backLinkLabel() }}
          </a>
        }
      </div>
    </div>
  `,
  styleUrl: './error-state.component.css',
})
export class ErrorStateComponent {
  readonly error = input.required<UiError>();
  readonly backLink = input<string | null>(null);
  readonly backLinkLabel = input('Volver al listado');

  readonly reintentar = output<void>();

  protected readonly copiado = signal(false);

  protected campos(): string[] {
    return Object.keys(this.error().fields ?? {});
  }

  protected async copiar(): Promise<void> {
    const correlationId = this.error().correlationId;
    if (!correlationId) {
      return;
    }
    try {
      await navigator.clipboard.writeText(correlationId);
      this.copiado.set(true);
    } catch {
      this.copiado.set(false);
    }
  }
}
