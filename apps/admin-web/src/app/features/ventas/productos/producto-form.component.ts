import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { Producto, ProductoAlta, ProductoUpdate } from '../../../core/models/ventas.models';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { VentasService, etagDeVersion } from '../../../core/services/ventas.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';

const URL_PERMITIDA = /^(https:\/\/|\/assets\/)/;

/**
 * Alta (`/app/productos/nuevo`, A) y edición
 * (`/app/productos/:id/editar`, A) de producto.
 *
 * Alta: `POST /api/producto/registrar` con JSON cerrado.
 * Edición: `GET /api/producto/buscar/{id}?idHotel=` y
 * `PUT /api/producto/actualizar/{id}?idHotel=` con `If-Match` del ETag,
 * `productoVersion` en el cuerpo y sin `cantidad` (la cantidad solo cambia por
 * venta o ajuste). 412 y 428 se resuelven recargando, nunca sobrescribiendo.
 */
@Component({
  selector: 'app-producto-form',
  standalone: true,
  imports: [FormsModule, RouterLink, ErrorBannerComponent],
  templateUrl: './producto-form.component.html',
})
export class ProductoFormComponent implements OnInit {
  private readonly ventas = inject(VentasService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);
  readonly sedesContext = inject(SedeContextService);

  readonly esAdmin = computed(() => this.session.rol() === 'ADMIN');
  readonly sedes = computed(() => this.sedesContext.sedes());
  readonly id = signal<number | null>(null);
  readonly esEdicion = computed(() => this.id() !== null);
  readonly cargando = signal(false);
  readonly guardando = signal(false);
  readonly error = signal<ApiUiError | null>(null);
  readonly errores = signal<Record<string, string>>({});
  readonly producto = signal<Producto | null>(null);
  readonly etag = signal<string | null>(null);

  sku = '';
  nombre = '';
  detalle = '';
  imagenUrl = '';
  idHotel: number | null = null;
  precio: number | null = null;
  cantidadInicial: number | null = 0;
  umbralBajo: number | null = 0;
  estado = true;

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
      this.idHotel = this.sedesContext.selectedId();
      return;
    }
    this.id.set(Number(idParam));
    await this.cargarProducto();
  }

  async cargarProducto(): Promise<void> {
    const id = this.id();
    if (id === null) return;
    const idHotel = this.sedesContext.selectedId() ?? this.producto()?.idHotel ?? null;
    if (!idHotel) {
      this.error.set({
        code: 'VALIDATION_ERROR',
        message: 'Selecciona la sede en el topbar para editar este producto.',
        action: 'Elegir una sede y recargar.',
        fields: {},
        correlationId: '',
        retryable: false,
        reloadable: false,
        relogin: false,
      });
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    try {
      const res = await this.ventas.buscarProducto(id, idHotel);
      const p = res.data;
      this.producto.set(p);
      this.etag.set(res.etag);
      this.idHotel = p.idHotel;
      this.sku = p.sku;
      this.nombre = p.nombre;
      this.detalle = p.detalle ?? '';
      this.imagenUrl = p.imagenUrl ?? '';
      this.precio = p.precio;
      this.umbralBajo = p.umbralBajo;
      this.estado = p.estado;
    } catch (err) {
      this.error.set(parseApiError(err));
    } finally {
      this.cargando.set(false);
    }
  }

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
    if (err?.reloadable) {
      void this.cargarProducto();
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

    if (!this.esEdicion()) {
      if (!sku) e['sku'] = 'Campo obligatorio';
      else if (sku.length > 40) e['sku'] = 'Máximo 40 caracteres';
    }

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
    if (!this.esEdicion()) {
      if (
        this.cantidadInicial === null ||
        !Number.isInteger(Number(this.cantidadInicial)) ||
        Number(this.cantidadInicial) < 0
      ) {
        e['cantidadInicial'] = 'La cantidad no puede ser negativa';
      }
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

    this.guardando.set(true);
    try {
      if (this.esEdicion()) {
        const actual = this.producto();
        const body: ProductoUpdate = {
          nombre: this.nombre.trim(),
          ...(this.detalle.trim() ? { detalle: this.detalle.trim() } : {}),
          ...(this.imagenUrl.trim() ? { imagenUrl: this.imagenUrl.trim() } : {}),
          precio: Number(this.precio),
          umbralBajo: Number(this.umbralBajo),
          estado: this.estado,
          productoVersion: actual?.productoVersion ?? 1,
        };
        const ifMatch = this.etag() ?? etagDeVersion(actual?.version ?? 1);
        const res = await this.ventas.actualizarProducto(this.id()!, this.idHotel!, body, ifMatch);
        this.producto.set(res.data);
        this.etag.set(res.etag);
        this.precio = res.data.precio;
        this.umbralBajo = res.data.umbralBajo;
        this.estado = res.data.estado;
        this.toast.show('Cambios guardados');
        return;
      }

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
