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

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);

  correo = '';
  clave = '';
  loading = false;
  error: ApiError | null = null;

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
    const req = { correo: this.correo.trim().toLowerCase(), clave: this.clave };
    try {
      this.loading = true;
      const data = await this.auth.login(req);
      const auth = {
        idPersona: data.idPersona,
        nombre: data.nombre,
        apellido: data.apellido,
        correo: data.correo,
        tipoPersona: data.tipoPersona,
        token: data.token,
        expiresIn: data.expiresIn,
        sedes: data.sedes,
      };
      this.session.setSession(auth, {
        idPersona: data.idPersona,
        tipoDocumento: '',
        documento: '',
        nombre: data.nombre,
        apellido: data.apellido,
        correo: data.correo,
        fotoUrl: null,
        idTipoPersona: 0,
        tipoPersona: data.tipoPersona,
        sedes: data.sedes,
        estado: true,
        fechaCreacion: '',
        version: 0,
      });
      await this.session.me(); // carga la persona completa del JWT
      this.router.navigateByUrl(this.session.homeUrl(), { replaceUrl: true });
    } catch (e: any) {
      this.setError(e?.error ?? e);
    } finally {
      this.loading = false;
    }
  }

  claveInvalida(): boolean {
    return this.error ? this.error.code === 'INVALID_CREDENTIALS' : false;
  }
  rateLimitado(): boolean {
    return this.error ? this.error.code === 'RATE_LIMITED' : false;
  }
}
