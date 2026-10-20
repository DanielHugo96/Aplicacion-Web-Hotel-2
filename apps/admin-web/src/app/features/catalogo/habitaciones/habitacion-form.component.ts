import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import {
  Categoria,
  Habitacion,
  HabitacionInput,
  HabitacionUpdate,
  ImagenHabitacion,
  Piso,
} from '../../../core/models/catalogo.models';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';

const URL_PERMITIDA = /^(https:\/\/|\/assets\/)/;

@Component({
  selector: 'app-habitacion-form',
  standalone: true,
  imports: [FormsModule, RouterLink, ErrorBannerComponent],
  templateUrl: './habitacion-form.component.html',
  styleUrl: './habitacion-form.component.css',
})
export class HabitacionFormComponent implements OnInit {
  private readonly catalogo = inject(CatalogoService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);
  readonly sedesContext = inject(SedeContextService);

  readonly esAdmin = computed(() => this.session.rol() === 'ADMIN');

  readonly id = signal<number | null>(null);
  readonly esEdicion = computed(() => this.id() !== null);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<ApiUiError | null>(null);
  readonly pisos = signal<Piso[]>([]);
  readonly categorias = signal<Categoria[]>([]);
  readonly habitacion = signal<Habitacion | null>(null);

  idHotel: number | null = null;
  numero = '';
  idPiso: number | null = null;
  idCategoria: number | null = null;
  detalle = '';
  precio: number | null = null;
  publicada = false;
  imagenes: ImagenHabitacion[] = [];
  errores: Record<string, string> = {};

  readonly actionLabel = computed(() => {
    const err = this.error();
    if (!err) return null;
    if (err.relogin) return 'Iniciar sesión de nuevo';
    if (err.reloadable) return 'Recargar';
    if (err.retryable) return 'Reintentar';
    return null;
  });

  async ngOnInit(): Promise<void> {
    await this.cargarCategorias();
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) {
      this.idHotel = this.sedesContext.selectedId();
      if (this.idHotel) await this.cargarPisos(this.idHotel);
      this.cargando.set(false);
      return;
    }
    this.id.set(Number(idParam));
    try {
      const res = await this.catalogo.buscarHabitacion(this.id()!);
      const h = res.data;
      this.habitacion.set(h);
      this.idHotel = h.idHotel;
      this.numero = h.numero;
      this.idPiso = h.idPiso;
      this.idCategoria = h.idCategoria;
      this.detalle = h.detalle;
      this.precio = h.precio;
      this.publicada = h.publicada;
      this.imagenes = (h.urlsImagenes ?? []).map((i) => ({ ...i }));
      await this.cargarPisos(h.idHotel);
    } catch (err) {
      this.error.set(parseApiError(err));
    } finally {
      this.cargando.set(false);
    }
  }

  async cargarCategorias(): Promise<void> {
    try {
      const res = await this.catalogo.listarCategorias(0, 100);
      this.categorias.set(res.data);
    } catch (err) {
      this.error.set(parseApiError(err));
    }
  }

  async cargarPisos(idHotel: number): Promise<void> {
    try {
      const res = await this.catalogo.listarPisos(0, 100, idHotel);
      this.pisos.set(res.data);
    } catch (err) {
      this.error.set(parseApiError(err));
    }
  }

  sedeNombre(): string {
    if (!this.idHotel) return 'Sin sede';
    return this.sedesContext.sedes().find((s) => s.idHotel === this.idHotel)?.nombre ?? `Sede ${this.idHotel}`;
  }

  agregarImagen(): void {
    if (this.imagenes.length >= 10) return;
    const orden = this.siguienteOrden();
    this.imagenes.push({ url: '', alt: '', orden });
  }

  quitarImagen(index: number): void {
    this.imagenes.splice(index, 1);
  }

  private siguienteOrden(): number {
    const usados = new Set(this.imagenes.map((i) => Number(i.orden)));
    let orden = 0;
    while (usados.has(orden)) orden++;
    return orden;
  }

  campoInvalido(campo: string): boolean {
    return !!this.errores[campo];
  }

  mensajeCampo(campo: string): string {
    return this.errores[campo] ?? '';
  }

  private validar(): boolean {
    const e: Record<string, string> = {};
    if (!this.idPiso || !this.idCategoria) e['idPiso'] = 'Selecciona piso y categoría de la sede';
    if (!this.esEdicion() && (!this.numero.trim() || this.numero.length > 10)) {
      e['numero'] = !this.numero.trim() ? 'Campo obligatorio' : 'Máximo 10 caracteres';
    }
    if (!this.detalle.trim() || this.detalle.length > 500) {
      e['detalle'] = !this.detalle.trim() ? 'Campo obligatorio' : 'Máximo 500 caracteres';
    }
    if (this.precio === null || Number(this.precio) <= 0) {
      e['precio'] = 'El precio debe ser mayor que 0';
    }
    if (this.imagenes.length > 10) e['urlsImagenes'] = 'Máximo 10 imágenes';
    const ordenes = new Set<number>();
    for (const imagen of this.imagenes) {
      if (!URL_PERMITIDA.test(imagen.url) || imagen.url.length > 500) {
        e['urlsImagenes'] = 'URL no permitida';
      }
      if (!imagen.alt.trim() || imagen.alt.length > 150) {
        e['urlsImagenes'] = e['urlsImagenes'] ?? 'Campo obligatorio';
      }
      if (ordenes.has(Number(imagen.orden))) {
        e['urlsImagenes'] = e['urlsImagenes'] ?? 'El orden debe ser único';
      }
      ordenes.add(Number(imagen.orden));
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
      const imagenes = this.imagenes.map((i) => ({
        url: i.url.trim(),
        alt: i.alt.trim(),
        orden: Number(i.orden),
      }));
      if (this.esEdicion()) {
        const body: HabitacionUpdate = {
          idPiso: this.idPiso!,
          idCategoria: this.idCategoria!,
          detalle: this.detalle.trim(),
          precio: Number(this.precio),
          publicada: this.publicada,
          urlsImagenes: imagenes,
        };
        await this.catalogo.actualizarHabitacion(this.id()!, body);
      } else {
        if (!this.idHotel) {
          this.errores = { ...this.errores, idHotel: 'Selecciona la sede' };
          return;
        }
        const body: HabitacionInput = {
          idHotel: this.idHotel,
          numero: this.numero.trim(),
          idPiso: this.idPiso!,
          idCategoria: this.idCategoria!,
          detalle: this.detalle.trim(),
          precio: Number(this.precio),
          publicada: this.publicada,
          urlsImagenes: imagenes,
        };
        await this.catalogo.registrarHabitacion(body);
      }
      this.toast.show('Cambios guardados');
      await this.router.navigateByUrl('/app/habitaciones');
    } catch (err) {
      const apiErr = parseApiError(err);
      this.error.set(apiErr);
      const e: Record<string, string> = { ...this.errores };
      for (const [campo, motivo] of Object.entries(apiErr.fields)) {
        if (campo.startsWith('urlsImagenes')) e['urlsImagenes'] = motivo;
        else e[campo] = motivo;
      }
      if (apiErr.code === 'DUPLICATE_RESOURCE') {
        e['numero'] = 'Ya existe una habitación activa con ese número';
      }
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
