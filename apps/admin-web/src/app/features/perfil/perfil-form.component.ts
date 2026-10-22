import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiUiError, parseApiError } from '../../core/api-error';
import { API_PATHS } from '../../core/api.config';
import { AuthService } from '../../core/services/auth.service';
import { ETagCacheService } from '../../core/services/etag-cache.service';
import { Persona, SessionService } from '../../core/services/session.service';
import { ErrorBannerComponent } from '../../ui/error-banner/error-banner.component';
import { ImagenPanelComponent } from '../../shared/imagen-panel.component';
import { PasswordChangeComponent } from '../password-change/password-change.component';

const FOTO_URL_RE = /^(https:\/\/|\/assets\/)/;

/**
 * Formulario compartido de perfil (`/app/perfil` para A/E y `/mi-perfil` para
 * C). Envía solo `nombre`, `apellido` y `fotoUrl`; el `If-Match` y la
 * `Idempotency-Key` los pone el interceptor. 412 no sobrescribe: recarga.
 * 428 se resuelve recargando el recurso antes de reenviar.
 *
 * La foto actual se pinta aunque no se esté editando el campo y la nueva se
 * previsualiza en cuanto la URL es válida; un fallo de carga deja el valor
 * escrito y muestra el texto junto al campo, sin bloquear el guardado
 * ([`08` §6](../../../../docs/especificacion/08-admin-web.md#6-imágenes-en-el-panel)).
 */
@Component({
  selector: 'app-perfil-form',
  standalone: true,
  imports: [
    FormsModule,
    PasswordChangeComponent,
    ErrorBannerComponent,
    ImagenPanelComponent,
  ],
  templateUrl: './perfil-form.component.html',
  styleUrl: './perfil-form.component.css',
})
export class PerfilFormComponent {
  @Input() persona: Persona | null = null;
  @Input() titulo = 'Mis datos';
  @Output() saved = new EventEmitter<void>();

  private readonly auth = inject(AuthService);
  private readonly session = inject(SessionService);
  private readonly etags = inject(ETagCacheService);

  nombre = '';
  apellido = '';
  fotoUrl = '';
  loading = false;
  error: ApiUiError | null = null;
  etag: string | null = null;
  ok = false;
  /** `true` cuando la vista previa de `fotoUrl` no cargó. */
  readonly fotoRota = signal(false);
  private recargadoPorPrecondicion = false;

  ngOnInit(): void {
    this.rellenarDesdePersona();
  }

  fotoValida(): boolean {
    if (!this.fotoUrl) return true;
    return FOTO_URL_RE.test(this.fotoUrl) && this.fotoUrl.length <= 500;
  }

  /** `true` cuando hay una URL de foto válida que previsualizar. */
  mostrarFoto(): boolean {
    const url = this.fotoUrl.trim();
    return !!url && FOTO_URL_RE.test(url) && url.length <= 500;
  }

  /** Nombre accesible de la foto: el de la persona, nunca la URL. */
  altFoto(): string {
    const nombre = `${this.nombre} ${this.apellido}`.trim();
    return nombre ? `Foto de ${nombre}` : 'Foto de perfil';
  }

  alFallarFoto(): void {
    this.fotoRota.set(true);
  }

  alEscribirFoto(url: string): void {
    this.fotoUrl = url;
    this.fotoRota.set(false);
  }

  formularioValido(): boolean {
    return !!(this.nombre.trim() && this.apellido.trim() && this.fotoValida());
  }

  campoInvalido(campo: string): boolean {
    return !!this.error?.fields[campo];
  }

  mensajeCampo(campo: string): string {
    return this.error?.fields[campo] ?? '';
  }

  accionEtiqueta(): string | null {
    if (!this.error) return null;
    if (this.error.code === 'PRECONDITION_FAILED') return 'Recargar';
    if (this.error.code === 'PRECONDITION_REQUIRED') {
      return this.recargadoPorPrecondicion ? 'Guardar cambios' : 'Recargar';
    }
    return null;
  }

  async accionError(): Promise<void> {
    if (
      this.error?.code === 'PRECONDITION_REQUIRED' &&
      this.recargadoPorPrecondicion
    ) {
      await this.guardar();
      return;
    }
    await this.recargar();
  }

  async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    await this.guardar();
  }

  async guardar(): Promise<void> {
    this.error = null;
    this.ok = false;
    this.recargadoPorPrecondicion = false;
    if (this.loading || !this.formularioValido()) return;
    try {
      this.loading = true;
      const res = await this.auth.updateMe({
        nombre: this.nombre.trim(),
        apellido: this.apellido.trim(),
        fotoUrl: this.fotoUrl.trim() || null,
      });
      this.persona = res.persona;
      this.session.updatePersona(res.persona);
      this.etag = res.etag;
      this.ok = true;
      this.saved.emit();
    } catch (err) {
      const api = parseApiError(err);
      this.error = api;
      if (api.code === 'PRECONDITION_FAILED') {
        this.etag = null;
        this.etags.delete(API_PATHS.authMe);
      }
      if (api.code === 'PRECONDITION_REQUIRED') {
        await this.recargar(false);
        this.error = api;
        this.recargadoPorPrecondicion = true;
      }
    } finally {
      this.loading = false;
    }
  }

  async recargar(limpiarError = true): Promise<void> {
    if (limpiarError) {
      this.error = null;
      this.ok = false;
      this.recargadoPorPrecondicion = false;
    }
    this.loading = true;
    try {
      const res = await this.auth.me();
      this.persona = res.persona;
      this.session.updatePersona(res.persona);
      this.etag = res.etag;
      this.rellenarDesdePersona();
    } finally {
      this.loading = false;
    }
  }

  private rellenarDesdePersona(): void {
    if (!this.persona) return;
    this.nombre = this.persona.nombre ?? '';
    this.apellido = this.persona.apellido ?? '';
    this.fotoUrl = this.persona.fotoUrl ?? '';
    this.fotoRota.set(false);
  }
}
