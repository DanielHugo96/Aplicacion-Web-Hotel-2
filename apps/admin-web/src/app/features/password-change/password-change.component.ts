import { Component, inject, signal, Output, EventEmitter } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';

interface ApiError {
  code: string;
  message: string;
  fields: Record<string, string>;
  correlationId: string;
}

@Component({
  selector: 'app-password-change',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './password-change.component.html',
  styleUrl: './password-change.component.css',
})
export class PasswordChangeComponent {
  @Output() saved = new EventEmitter<void>();

  private readonly auth = inject(AuthService);

  claveActual = '';
  claveNueva = '';
  loading = false;
  error: ApiError | null = null;
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
    return !!this.error?.fields[campo];
  }

  mensajeCampo(campo: string): string {
    return this.error?.fields[campo] ?? '';
  }

  private setError(body: any): void {
    const err = body?.error ?? {};
    this.error = {
      code: err.code ?? 'INTERNAL_ERROR',
      message: err.message ?? body?.message ?? 'Ocurrió un error inesperado.',
      fields: err.fields ?? {},
      correlationId: body?.meta?.correlationId ?? '',
    };
  }

  async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.error = null;
    this.ok = false;
    if (!this.formularioValido()) return;
    try {
      this.loading = true;
      await this.auth.changePassword({
        claveActual: this.claveActual,
        claveNueva: this.claveNueva,
      });
      this.ok = true;
      this.saved.emit();
    } catch (e: any) {
      this.setError(e?.error ?? e);
    } finally {
      this.loading = false;
    }
  }
}
