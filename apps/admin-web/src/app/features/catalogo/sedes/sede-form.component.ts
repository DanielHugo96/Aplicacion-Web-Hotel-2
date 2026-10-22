import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { Hotel, HotelInput } from '../../../core/models/catalogo.models';
import { ImagenPanelComponent } from '../../../shared/imagen-panel.component';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';

const CORREO_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const SLUG_RE = /^[a-z0-9-]+$/;
/** URL de imagen del contrato común: https o `/assets/`, ≤ 500. */
const IMAGEN_URL_RE = /^(https:\/\/|\/assets\/)/;

@Component({
  selector: 'app-sede-form',
  standalone: true,
  imports: [FormsModule, RouterLink, ErrorBannerComponent, ImagenPanelComponent],
  templateUrl: './sede-form.component.html',
  styleUrl: './sede-form.component.css',
})
export class SedeFormComponent implements OnInit {
  private readonly catalogo = inject(CatalogoService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);

  readonly esAdmin = computed(() => this.session.rol() === 'ADMIN');

  readonly id = signal<number | null>(null);
  readonly esEdicion = computed(() => this.id() !== null);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<ApiUiError | null>(null);

  slug = '';
  nombre = '';
  descripcion = '';
  direccion = '';
  ciudad = '';
  telefono = '';
  correoContacto = '';
  imagenUrl = '';
  imagenAlt = '';
  publicado = false;
  estado = true;
  errores: Record<string, string> = {};
  /** `true` si la vista previa de `imagenUrl` no cargó. No bloquea guardar. */
  readonly imagenRota = signal(false);

  readonly actionLabel = computed(() => {
    const err = this.error();
    if (!err) return null;
    if (err.relogin) return 'Iniciar sesión de nuevo';
    if (err.reloadable) return 'Recargar';
    if (err.retryable) return 'Reintentar';
    return null;
  });

  async ngOnInit(): Promise<void> {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) {
      this.cargando.set(false);
      return;
    }
    this.id.set(Number(idParam));
    try {
      const res = await this.catalogo.buscarSede(this.id()!);
      const h: Hotel = res.data;
      this.slug = h.slug;
      this.nombre = h.nombre;
      this.descripcion = h.descripcion;
      this.direccion = h.direccion;
      this.ciudad = h.ciudad;
      this.telefono = h.telefono;
      this.correoContacto = h.correoContacto;
      this.imagenUrl = h.imagenUrl ?? '';
      this.imagenAlt = h.imagenAlt ?? '';
      this.publicado = h.publicado;
      this.estado = h.estado;
    } catch (err) {
      this.error.set(parseApiError(err));
    } finally {
      this.cargando.set(false);
    }
  }

  campoInvalido(campo: string): boolean {
    return !!this.errores[campo];
  }

  mensajeCampo(campo: string): string {
    return this.errores[campo] ?? '';
  }

  /** La vista previa solo aparece con una URL válida del contrato. */
  urlImagenValida(): boolean {
    const url = this.imagenUrl.trim();
    if (!url) return true;
    return IMAGEN_URL_RE.test(url) && url.length <= 500;
  }

  /** `true` cuando la vista previa debe pintarse. */
  mostrarPrevia(): boolean {
    return !!this.imagenUrl.trim() && this.urlImagenValida();
  }

  /** Marca la miniatura como rota y conserva el valor escrito. */
  alFallarPrevia(): void {
    this.imagenRota.set(true);
  }

  alEscribirImagen(url: string): void {
    this.imagenUrl = url;
    this.imagenRota.set(false);
  }

  private validar(): boolean {
    const e: Record<string, string> = {};
    if (!SLUG_RE.test(this.slug.trim()) || this.slug.trim().length > 80) {
      e['slug'] = 'Slug inválido o ya usado';
    }
    if (!this.nombre.trim() || this.nombre.length > 120) e['nombre'] = 'Campo obligatorio';
    if (this.descripcion.length > 300) e['descripcion'] = 'Máximo 300 caracteres';
    if (!this.direccion.trim() || this.direccion.length > 200) e['direccion'] = 'Campo obligatorio';
    if (!this.ciudad.trim() || this.ciudad.length > 80) e['ciudad'] = 'Campo obligatorio';
    if (this.telefono.length > 20) e['telefono'] = 'Teléfono demasiado largo';
    if (!this.correoContacto.trim() || !CORREO_RE.test(this.correoContacto.trim()) || this.correoContacto.length > 254) {
      e['correoContacto'] = 'Correo inválido';
    }
    const imagenUrl = this.imagenUrl.trim();
    const imagenAlt = this.imagenAlt.trim();
    if (imagenUrl && (!IMAGEN_URL_RE.test(imagenUrl) || imagenUrl.length > 500)) {
      e['imagenUrl'] = 'URL no permitida';
    }
    if (imagenUrl && (!imagenAlt || imagenAlt.length > 150)) {
      e['imagenAlt'] = 'Describe la imagen para accesibilidad';
    }
    this.errores = e;
    return Object.keys(e).length === 0;
  }

  async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.error.set(null);
    if (!this.validar()) return;
    this.guardando.set(true);
    try {
      const body: HotelInput = {
        slug: this.slug.trim(),
        nombre: this.nombre.trim(),
        descripcion: this.descripcion.trim(),
        direccion: this.direccion.trim(),
        ciudad: this.ciudad.trim(),
        telefono: this.telefono.trim(),
        correoContacto: this.correoContacto.trim(),
        imagenUrl: this.imagenUrl.trim() || null,
        imagenAlt: this.imagenUrl.trim() ? this.imagenAlt.trim() || null : null,
        publicado: this.publicado,
        estado: this.estado,
      };
      if (this.esEdicion()) {
        await this.catalogo.actualizarSede(this.id()!, body);
      } else {
        await this.catalogo.registrarSede(body);
      }
      this.toast.show('Cambios guardados');
      await this.router.navigateByUrl('/app/sedes');
    } catch (err) {
      const apiErr = parseApiError(err);
      this.error.set(apiErr);
      const e: Record<string, string> = { ...this.errores };
      for (const [campo, motivo] of Object.entries(apiErr.fields)) e[campo] = motivo;
      this.errores = e;
    } finally {
      this.guardando.set(false);
    }
  }

  accionError(): void {
    const err = this.error();
    if (!err) return;
    if (err.relogin) {
      this.session.logout();
      return;
    }
    if (err.reloadable) {
      this.ngOnInit();
      return;
    }
    this.onSubmit(new Event('submit'));
  }
}
