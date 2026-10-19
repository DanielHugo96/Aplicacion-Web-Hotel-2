import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { SessionService } from '../../core/services/session.service';

interface ApiError {
  code: string;
  message: string;
  fields: Record<string, string>;
  correlationId: string;
}

const DOCUMENTO_RE_DNI = /^\d{8}$/;
const DOCUMENTO_RE_OTROS = /^[A-Za-z0-9]{6,20}$/;

@Component({
  selector: 'app-registro',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './registro.component.html',
  styleUrl: './registro.component.css',
})
export class RegistroComponent {
  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  tipoDocumento = 'DNI';
  documento = '';
  nombre = '';
  apellido = '';
  correo = '';
  clave = '';
  loading = false;
  error: ApiError | null = null;

  readonly tiposDocumento = ['DNI', 'PASAPORTE', 'CE'];

  campoInvalido(campo: string): boolean {
    return !!this.error?.fields[campo];
  }

  mensajeCampo(campo: string): string {
    return this.error?.fields[campo] ?? '';
  }

  claveBytes(): number {
    return new TextEncoder().encode(this.clave).length;
  }

  claveValida(): boolean {
    const n = this.claveBytes();
    return n >= 12 && n <= 72;
  }

  documentoValido(): boolean {
    if (this.tipoDocumento === 'DNI') return DOCUMENTO_RE_DNI.test(this.documento);
    return DOCUMENTO_RE_OTROS.test(this.documento);
  }

  formularioValido(): boolean {
    return !!(
      this.tipoDocumento &&
      this.documentoValido() &&
      this.nombre.trim() &&
      this.apellido.trim() &&
      this.correo.trim() &&
      this.claveValida()
    );
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
    if (!this.formularioValido()) return;
    try {
      this.loading = true;
      const persona = await this.auth.registro({
        tipoDocumento: this.tipoDocumento,
        documento: this.documento.trim(),
        nombre: this.nombre.trim(),
        apellido: this.apellido.trim(),
        correo: this.correo.trim().toLowerCase(),
        clave: this.clave,
      });
      // Registro exitoso: vuelve a login (el cuerpo enviado nunca lleva campos de rol/sedes/estado)
      this.router.navigate(['/login'], {
        queryParams: { registrado: persona.correo },
      });
    } catch (e: any) {
      this.setError(e?.error ?? e);
    } finally {
      this.loading = false;
    }
  }
}
