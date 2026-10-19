import { Component, inject, signal, Output, EventEmitter, Input } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { Persona } from '../../core/services/session.service';
import { PasswordChangeComponent } from '../password-change/password-change.component';

interface ApiError {
  code: string;
  message: string;
  fields: Record<string, string>;
  correlationId: string;
}

const FOTO_URL_RE = /^(https:\/\/|\/assets\/)/;

@Component({
  selector: 'app-perfil-form',
  standalone: true,
  imports: [FormsModule, PasswordChangeComponent],
  templateUrl: './perfil-form.component.html',
  styleUrl: './perfil-form.component.css',
})
export class PerfilFormComponent {
  @Input() persona: Persona | null = null;
  @Input() titulo = 'Mi perfil';
  @Output() saved = new EventEmitter<void>();

  private readonly auth = inject(AuthService);

  nombre = '';
  apellido = '';
  fotoUrl = '';
  loading = false;
  error: ApiError | null = null;
  etag: string | null = null;
  ok = false;

  ngOnInit(): void {
    if (this.persona) {
      this.nombre = this.persona.nombre ?? '';
      this.apellido = this.persona.apellido ?? '';
      this.fotoUrl = this.persona.fotoUrl ?? '';
    }
  }

  fotoValida(): boolean {
    if (!this.fotoUrl) return true;
    return FOTO_URL_RE.test(this.fotoUrl) && this.fotoUrl.length <= 500;
  }

  formularioValido(): boolean {
    return !!(
      this.nombre.trim() &&
      this.apellido.trim() &&
      this.fotoValida()
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
      const res = await this.auth.updateMe({
        nombre: this.nombre.trim(),
        apellido: this.apellido.trim(),
        fotoUrl: this.fotoUrl.trim() || null,
      });
      this.etag = res.etag;
      this.ok = true;
      this.saved.emit();
    } catch (e: any) {
      // 412 → recarga forzada; 400 → errores por campo
      this.setError(e?.error ?? e);
      const err = this.error as ApiError | null;
      if (err?.code === 'PRECONDITION_FAILED') {
        this.etag = null;
      }
    } finally {
      this.loading = false;
    }
  }

  async recargar(): Promise<void> {
    this.error = null;
    this.ok = false;
    this.loading = true;
    try {
      const res = await this.auth.me();
      this.persona = res.persona;
      this.nombre = res.persona.nombre ?? '';
      this.apellido = res.persona.apellido ?? '';
      this.fotoUrl = res.persona.fotoUrl ?? '';
      this.etag = res.etag;
    } finally {
      this.loading = false;
    }
  }
}
