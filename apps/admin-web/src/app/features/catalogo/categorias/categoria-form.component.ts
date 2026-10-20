import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { Categoria, CategoriaInput } from '../../../core/models/catalogo.models';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';

@Component({
  selector: 'app-categoria-form',
  standalone: true,
  imports: [FormsModule, RouterLink, ErrorBannerComponent],
  templateUrl: './categoria-form.component.html',
  styleUrl: './categoria-form.component.css',
})
export class CategoriaFormComponent implements OnInit {
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

  descripcion = '';
  detalle = '';
  capacidad = 1;
  estado = true;
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
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) {
      this.cargando.set(false);
      return;
    }
    this.id.set(Number(idParam));
    try {
      const res = await this.catalogo.buscarCategoria(this.id()!);
      const c: Categoria = res.data;
      this.descripcion = c.descripcion;
      this.detalle = c.detalle;
      this.capacidad = c.capacidad;
      this.estado = c.estado;
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

  private validar(): boolean {
    const e: Record<string, string> = {};
    if (!this.descripcion.trim() || this.descripcion.length > 80) {
      e['descripcion'] = this.descripcion.length > 80 ? 'Máximo 80 caracteres' : 'Campo obligatorio';
    }
    if (!this.detalle.trim() || this.detalle.length > 500) {
      e['detalle'] = this.detalle.length > 500 ? 'Máximo 500 caracteres' : 'Campo obligatorio';
    }
    if (!Number.isInteger(this.capacidad) || this.capacidad < 1 || this.capacidad > 10) {
      e['capacidad'] = 'Capacidad entre 1 y 10';
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
      const body: CategoriaInput = {
        descripcion: this.descripcion.trim(),
        detalle: this.detalle.trim(),
        capacidad: Number(this.capacidad),
        estado: this.estado,
      };
      if (this.esEdicion()) {
        await this.catalogo.actualizarCategoria(this.id()!, body);
      } else {
        await this.catalogo.registrarCategoria(body);
      }
      this.toast.show('Cambios guardados');
      await this.router.navigateByUrl('/app/categorias');
    } catch (err) {
      const apiErr = parseApiError(err);
      this.error.set(apiErr);
      const e: Record<string, string> = { ...this.errores };
      for (const [campo, motivo] of Object.entries(apiErr.fields)) e[campo] = motivo;
      if (apiErr.code === 'DUPLICATE_RESOURCE') e['descripcion'] = 'Ya existe esa categoría';
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
