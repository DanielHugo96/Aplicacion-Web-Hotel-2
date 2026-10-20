import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import {
  FiltroEstadoProducto,
  Producto,
} from '../../../core/models/ventas.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { VentasService } from '../../../core/services/ventas.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PaginationComponent } from '../../../ui/pagination/pagination.component';

/**
 * `/app/productos`: productos de la sede seleccionada en el topbar. A muta
 * (alta, edición, baja/reactivación, asignación y ajuste); E solo lee. La
 * píldora «Bajo stock» aparece cuando `cantidad ≤ umbralBajo`, nunca por
 * cálculo del front sobre precios. Un 503 muestra el mensaje del catálogo y
 * reintenta sin inventar precios ni datos de respaldo.
 */
@Component({
  selector: 'app-productos-list',
  standalone: true,
  imports: [FormsModule, ErrorBannerComponent, PaginationComponent, MoneyPipe],
  templateUrl: './productos-list.component.html',
})
export class ProductosListComponent {
  private readonly ventas = inject(VentasService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
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
}
