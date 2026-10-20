import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { FiltroEstadoProducto, Producto } from '../../../core/models/ventas.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { VentasService, etagDeVersion } from '../../../core/services/ventas.service';
import { ConfirmDialogComponent } from '../../../ui/confirm-dialog/confirm-dialog.component';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PaginationComponent } from '../../../ui/pagination/pagination.component';

/**
 * `/app/productos`: productos de la sede seleccionada en el topbar. A muta
 * (alta, edición, baja/reactivación, asignación y ajuste); E solo lee. La
 * píldora «Bajo stock» aparece cuando `cantidad ≤ umbralBajo`, nunca por
 * cálculo del front sobre precios. Un 503 muestra el mensaje del catálogo y
 * reintenta sin inventar precios ni datos de respaldo.
 *
 * Baja, reactivación, asignación y ajuste releen el producto para enviar el
 * `If-Match` real de la versión de la sede; ningún cuerpo incluye `cantidad`
 * salvo el ajuste, que la cambia solo por `delta` y motivo.
 */
@Component({
  selector: 'app-productos-list',
  standalone: true,
  imports: [
    FormsModule,
    ConfirmDialogComponent,
    ErrorBannerComponent,
    PaginationComponent,
    MoneyPipe,
  ],
  templateUrl: './productos-list.component.html',
})
export class ProductosListComponent {
  private readonly ventas = inject(VentasService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  readonly sedesContext = inject(SedeContextService);

  readonly esAdmin = computed(() => this.session.rol() === 'ADMIN');
  readonly sedes = computed(() => this.sedesContext.sedes());
  readonly sedeSeleccionada = computed(() => this.sedesContext.selectedId());
  readonly productos = signal<Producto[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly total = signal(0);
  readonly filtroEstado = signal<FiltroEstadoProducto>('activos');

  readonly baja = signal<Producto | null>(null);
  readonly procesando = signal(false);
  readonly ajuste = signal<Producto | null>(null);
  readonly ajusteErrores = signal<Record<string, string>>({});
  readonly asignacion = signal<Producto | null>(null);
  readonly asignacionErrores = signal<Record<string, string>>({});

  ajusteDelta: number | null = null;
  ajusteMotivo = '';
  asignacionIdHotel: number | null = null;
  asignacionPrecio: number | null = null;
  asignacionCantidad: number | null = 0;
  asignacionUmbral: number | null = 0;

  constructor() {
    effect(() => {
      const idHotel = this.sedeSeleccionada();
      untracked(() => {
        if (!idHotel) {
          this.productos.set([]);
          this.total.set(0);
          this.loading.set(false);
          return;
        }
        void this.cargar(0, idHotel);
      });
    });
  }

  async cargar(page = this.page(), idHotel = this.sedeSeleccionada()): Promise<void> {
    if (!idHotel) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await this.ventas.listarProductos(
        idHotel,
        page,
        this.size(),
        this.filtroEstado()
      );
      this.productos.set(res.data);
      this.page.set(res.page);
      this.size.set(res.size);
      this.total.set(res.totalElements);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.productos.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  cambiarSede(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.sedesContext.seleccionar(value ? Number(value) : null);
  }

  cambiarFiltro(value: FiltroEstadoProducto): void {
    this.filtroEstado.set(value);
    void this.cargar(0);
  }

  cambiarPagina(page: number): void {
    void this.cargar(page);
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    void this.cargar();
  }

  bajoStock(p: Producto): boolean {
    return p.cantidad <= p.umbralBajo;
  }

  nuevo(): void {
    void this.router.navigateByUrl('/app/productos/nuevo');
  }

  editar(p: Producto): void {
    void this.router.navigateByUrl(`/app/productos/${p.idProducto}/editar`);
  }

  movimientos(p: Producto): void {
    void this.router.navigateByUrl(`/app/productos/${p.idProducto}/movimientos`);
  }

  // --------------------------------------------------------- baja/reactivar

  pedirBaja(p: Producto): void {
    this.error.set(null);
    this.baja.set(p);
  }

  cancelarBaja(): void {
    if (this.procesando()) return;
    this.baja.set(null);
  }

  async confirmarBaja(): Promise<void> {
    const p = this.baja();
    const idHotel = this.sedeSeleccionada();
    if (!p || !idHotel || this.procesando()) return;
    this.procesando.set(true);
    this.error.set(null);
    try {
      const actual = await this.ventas.buscarProducto(p.idProducto, idHotel);
      const ifMatch = actual.etag ?? etagDeVersion(actual.data.version);
      await this.ventas.eliminarProducto(p.idProducto, idHotel, ifMatch);
      this.toast.show('Producto dado de baja');
      this.baja.set(null);
      await this.cargar();
    } catch (err) {
      this.error.set(parseApiError(err));
      this.baja.set(null);
    } finally {
      this.procesando.set(false);
    }
  }

  async reactivar(p: Producto): Promise<void> {
    const idHotel = this.sedeSeleccionada();
    if (!idHotel || this.procesando()) return;
    this.procesando.set(true);
    this.error.set(null);
    try {
      const actual = await this.ventas.buscarProducto(p.idProducto, idHotel);
      const ifMatch = actual.etag ?? etagDeVersion(actual.data.version);
      await this.ventas.actualizarProducto(
        p.idProducto,
        idHotel,
        { estado: true, productoVersion: actual.data.productoVersion },
        ifMatch
      );
      this.toast.show('Producto reactivado');
      await this.cargar();
    } catch (err) {
      this.error.set(parseApiError(err));
    } finally {
      this.procesando.set(false);
    }
  }

  // ---------------------------------------------------------------- ajuste

  abrirAjuste(p: Producto): void {
    this.error.set(null);
    this.ajusteErrores.set({});
    this.ajusteDelta = null;
    this.ajusteMotivo = '';
    this.ajuste.set(p);
  }

  cancelarAjuste(): void {
    if (this.procesando()) return;
    this.ajuste.set(null);
  }

  campoAjusteInvalido(campo: string): boolean {
    return !!this.ajusteErrores()[campo];
  }

  mensajeAjuste(campo: string): string {
    return this.ajusteErrores()[campo] ?? '';
  }

  async guardarAjuste(event: Event): Promise<void> {
    event.preventDefault();
    const p = this.ajuste();
    const idHotel = this.sedeSeleccionada();
    if (!p || !idHotel || this.procesando()) return;
    const errores: Record<string, string> = {};
    const delta = Number(this.ajusteDelta);
    const motivo = this.ajusteMotivo.trim();
    if (!Number.isInteger(delta) || delta === 0) {
      errores['delta'] = 'El delta no puede ser 0';
    }
    if (!motivo) errores['motivo'] = 'Indica el motivo del ajuste';
    else if (motivo.length > 300) errores['motivo'] = 'Máximo 300 caracteres';
    this.ajusteErrores.set(errores);
    if (Object.keys(errores).length > 0) return;

    this.procesando.set(true);
    this.error.set(null);
    try {
      const actual = await this.ventas.buscarProducto(p.idProducto, idHotel);
      const ifMatch = actual.etag ?? etagDeVersion(actual.data.version);
      const res = await this.ventas.ajustarStock(
        p.idProducto,
        { idHotel, delta, motivo },
        ifMatch
      );
      this.productos.update((lista) =>
        lista.map((item) =>
          item.idProducto === p.idProducto
            ? { ...item, cantidad: res.cantidad, version: res.version }
            : item
        )
      );
      this.ajuste.set(null);
      this.toast.show('Stock ajustado');
    } catch (err) {
      const api = parseApiError(err);
      this.error.set(api);
      if (Object.keys(api.fields).length > 0) this.ajusteErrores.set({ ...api.fields });
    } finally {
      this.procesando.set(false);
    }
  }

  // ------------------------------------------------------------ asignación

  abrirAsignacion(p: Producto): void {
    this.error.set(null);
    this.asignacionErrores.set({});
    const otraSede = this.sedes().find((s) => s.idHotel !== p.idHotel)?.idHotel ?? null;
    this.asignacionIdHotel = otraSede;
    this.asignacionPrecio = p.precio;
    this.asignacionCantidad = 0;
    this.asignacionUmbral = p.umbralBajo;
    this.asignacion.set(p);
  }

  cancelarAsignacion(): void {
    if (this.procesando()) return;
    this.asignacion.set(null);
  }

  campoAsignacionInvalido(campo: string): boolean {
    return !!this.asignacionErrores()[campo];
  }

  mensajeAsignacion(campo: string): string {
    return this.asignacionErrores()[campo] ?? '';
  }

  sedesAsignables(p: Producto) {
    return this.sedes().filter((s) => s.idHotel !== p.idHotel);
  }

  async guardarAsignacion(event: Event): Promise<void> {
    event.preventDefault();
    const p = this.asignacion();
    if (!p || this.procesando()) return;
    const errores: Record<string, string> = {};
    if (!this.asignacionIdHotel) errores['idHotel'] = 'Selecciona la sede';
    if (this.asignacionPrecio === null || Number(this.asignacionPrecio) <= 0) {
      errores['precio'] = 'El precio debe ser mayor que 0';
    } else if (Math.round(Number(this.asignacionPrecio) * 100) !== Number(this.asignacionPrecio) * 100) {
      errores['precio'] = 'El precio debe tener 2 decimales';
    }
    if (
      this.asignacionCantidad === null ||
      !Number.isInteger(Number(this.asignacionCantidad)) ||
      Number(this.asignacionCantidad) < 0
    ) {
      errores['cantidadInicial'] = 'La cantidad no puede ser negativa';
    }
    if (
      this.asignacionUmbral === null ||
      !Number.isInteger(Number(this.asignacionUmbral)) ||
      Number(this.asignacionUmbral) < 0
    ) {
      errores['umbralBajo'] = 'El umbral no puede ser negativo';
    }
    this.asignacionErrores.set(errores);
    if (Object.keys(errores).length > 0) return;

    this.procesando.set(true);
    this.error.set(null);
    try {
      await this.ventas.asignarProductoSede(p.idProducto, {
        idHotel: Number(this.asignacionIdHotel),
        precio: Number(this.asignacionPrecio),
        cantidadInicial: Number(this.asignacionCantidad),
        umbralBajo: Number(this.asignacionUmbral),
      });
      this.asignacion.set(null);
      this.toast.show('Producto asignado a la sede');
      await this.cargar();
    } catch (err) {
      const api = parseApiError(err);
      this.error.set(api);
      if (Object.keys(api.fields).length > 0) this.asignacionErrores.set({ ...api.fields });
    } finally {
      this.procesando.set(false);
    }
  }
}
