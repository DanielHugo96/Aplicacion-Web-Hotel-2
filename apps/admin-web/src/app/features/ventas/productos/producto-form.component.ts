import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { ProductoAlta } from '../../../core/models/ventas.models';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { VentasService } from '../../../core/services/ventas.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';

const URL_PERMITIDA = /^(https:\/\/|\/assets\/)/;

/**
 * `/app/productos/nuevo` (A): alta de producto con su asignación inicial a la
 * sede indicada. Validaciones y mensajes de `08` §3; un 409
 * `DUPLICATE_RESOURCE` (SKU repetido) y un 400 `VALIDATION_ERROR` resaltan
 * `error.fields` con el mensaje del catálogo. El envío se bloquea con spinner y
 * no manda `cantidad`, `version` ni ningún campo calculado.
 */
@Component({
  selector: 'app-producto-form',
  standalone: true,
  imports: [FormsModule, RouterLink, ErrorBannerComponent],
  templateUrl: './producto-form.component.html',
})
export class ProductoFormComponent {
  private readonly ventas = inject(VentasService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  readonly sedesContext = inject(SedeContextService);

  readonly esAdmin = computed(() => this.session.rol() === 'ADMIN');
  readonly sedes = computed(() => this.sedesContext.sedes());
  readonly guardando = signal(false);
  readonly error = signal<ApiUiError | null>(null);
  readonly errores = signal<Record<string, string>>({});

  sku = '';
  nombre = '';
  detalle = '';
  imagenUrl = '';
  idHotel: number | null = null;
  precio: number | null = null;
  cantidadInicial: number | null = 0;
  umbralBajo: number | null = 0;

  constructor() {
    this.idHotel = this.sedesContext.selectedId();
  }

  readonly actionLabel = computed(() => {
    const err = this.error();
    if (!err) return null;
    if (err.relogin) return 'Iniciar sesión de nuevo';
    if (err.reloadable) return 'Recargar';
    if (err.retryable) return 'Reintentar';
    return null;
  });

  campoInvalido(campo: string): boolean {
    return !!this.errores()[campo];
  }

  mensajeCampo(campo: string): string {
    return this.errores()[campo] ?? '';
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    this.error.set(null);
  }

  validar(): boolean {
    const e: Record<string, string> = {};
    const sku = this.sku.trim();
    const nombre = this.nombre.trim();
    const detalle = this.detalle.trim();
    const imagenUrl = this.imagenUrl.trim();

    if (!sku) e['sku'] = 'Campo obligatorio';
    else if (sku.length > 40) e['sku'] = 'Máximo 40 caracteres';

    if (!nombre) e['nombre'] = 'Campo obligatorio';
    else if (nombre.length > 120) e['nombre'] = 'Máximo 120 caracteres';

    if (detalle.length > 500) e['detalle'] = 'Máximo 500 caracteres';
    if (imagenUrl && (!URL_PERMITIDA.test(imagenUrl) || imagenUrl.length > 500)) {
      e['imagenUrl'] = 'URL no permitida';
    }
    if (!this.idHotel) e['idHotel'] = 'Selecciona la sede';
    if (this.precio === null || !Number.isFinite(Number(this.precio)) || Number(this.precio) <= 0) {
      e['precio'] = 'El precio debe ser mayor que 0';
    } else if (Math.round(Number(this.precio) * 100) !== Number(this.precio) * 100) {
      e['precio'] = 'El precio debe tener 2 decimales';
    }
    if (
      this.cantidadInicial === null ||
      !Number.isInteger(Number(this.cantidadInicial)) ||
      Number(this.cantidadInicial) < 0
    ) {
      e['cantidadInicial'] = 'La cantidad no puede ser negativa';
    }
    if (
      this.umbralBajo === null ||
      !Number.isInteger(Number(this.umbralBajo)) ||
      Number(this.umbralBajo) < 0
    ) {
      e['umbralBajo'] = 'El umbral no puede ser negativo';
    }
    this.errores.set(e);
    return Object.keys(e).length === 0;
  }

  async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    if (!this.esAdmin() || this.guardando()) return;
    this.error.set(null);
    if (!this.validar()) return;

    const body: ProductoAlta = {
      sku: this.sku.trim(),
      nombre: this.nombre.trim(),
      ...(this.detalle.trim() ? { detalle: this.detalle.trim() } : {}),
      ...(this.imagenUrl.trim() ? { imagenUrl: this.imagenUrl.trim() } : {}),
      idHotel: this.idHotel!,
      precio: Number(this.precio),
      cantidadInicial: Number(this.cantidadInicial),
      umbralBajo: Number(this.umbralBajo),
    };

    this.guardando.set(true);
    try {
      await this.ventas.registrarProducto(body);
      this.toast.show('Producto registrado');
      await this.router.navigateByUrl('/app/productos');
    } catch (err) {
      const api = parseApiError(err);
      this.error.set(api);
      if (Object.keys(api.fields).length > 0) this.errores.set({ ...api.fields });
    } finally {
      this.guardando.set(false);
    }
  }
}
