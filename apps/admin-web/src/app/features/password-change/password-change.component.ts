import { Component, EventEmitter, Output, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiUiError, parseApiError } from '../../core/api-error';
import { AuthService } from '../../core/services/auth.service';
import { ErrorBannerComponent } from '../../ui/error-banner/error-banner.component';

const MENSAJE_POLITICA =
  'La contraseña debe tener entre 12 y 72 bytes (caracteres UTF-8).';

/**
 * Cambio de clave: `PUT /api/auth/password` con `Idempotency-Key` del
 * interceptor. La clave nunca se guarda; el contador es de bytes UTF-8 y el
 * doble envío se bloquea con spinner. Los 400 se pintan con el catálogo.
 */
@Component({
  selector: 'app-password-change',
  standalone: true,
  imports: [FormsModule, ErrorBannerComponent],
  templateUrl: './password-change.component.html',
  styleUrl: './password-change.component.css',
})
export class PasswordChangeComponent {
  @Output() saved = new EventEmitter<void>();

  private readonly auth = inject(AuthService);

  claveActual = '';
  claveNueva = '';
  loading = false;
  error: ApiUiError | null = null;
  ok = false;

  claveNuevaBytes(): number {
    return new TextEncoder().encode(this.claveNueva).length;
  }

  claveNuevaValida(): boolean {
    const n = this.claveNuevaBytes();
    return n >= 12 && n <= 72;
  }

  diferenteDeActual(): boolean {
    return this.claveNueva.length > 0 && this.claveNueva !== this.claveActual;
  }

  formularioValido(): boolean {
    return !!(
      this.claveActual.trim() &&
      this.claveNuevaValida() &&
      this.diferenteDeActual()
    );
  }

  campoInvalido(campo: string): boolean {
    if (this.error?.fields[campo]) return true;
    if (this.error?.code === 'CURRENT_PASSWORD_MISMATCH' && campo === 'claveActual') {
      return true;
    }
    if (this.error?.code === 'PASSWORD_POLICY' && campo === 'claveNueva') return true;
    return false;
  }

  mensajeCampo(campo: string): string {
    if (this.error?.fields[campo]) return this.error.fields[campo];
    if (this.error?.code === 'CURRENT_PASSWORD_MISMATCH' && campo === 'claveActual') {
      return this.error.message;
    }
    if (this.error?.code === 'PASSWORD_POLICY' && campo === 'claveNueva') {
      return this.error.message;
    }
    return '';
  }

  async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    await this.guardar();
  }

  async guardar(): Promise<void> {
    this.error = null;
    this.ok = false;
    if (this.loading || !this.formularioValido()) return;
    try {
      this.loading = true;
      await this.auth.changePassword({
        claveActual: this.claveActual,
        claveNueva: this.claveNueva,
      });
      this.ok = true;
      this.claveActual = '';
      this.claveNueva = '';
      this.saved.emit();
    } catch (err) {
      this.error = parseApiError(err);
    } finally {
      this.loading = false;
    }
  }

  mensajePolitica(): string {
    return MENSAJE_POLITICA;
  }
}
