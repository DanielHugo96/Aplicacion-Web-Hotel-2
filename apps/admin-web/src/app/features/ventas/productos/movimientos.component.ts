import { Component, computed, effect, inject, OnInit, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { Movimiento, Producto } from '../../../core/models/ventas.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { PeTimestampPipe } from '../../../core/pipes/timestamp.pipe';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { VentasService } from '../../../core/services/ventas.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PaginationComponent } from '../../../ui/pagination/pagination.component';

/**
 * `/app/productos/:id/movimientos` (A/E): movimientos de la sede para un
 * producto. La unión `VENTA`/`AJUSTE` se pinta por su discriminante `tipo`; el
 * estado del movimiento se muestra literal en píldora neutral (el contrato no
 * fija etiqueta) y el delta del ajuste lleva signo. El rango se elige con
 * `input[type=date]` y se envía en ISO-8601. Un 503 no inventa precios.
 */
@Component({
  selector: 'app-movimientos',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    ErrorBannerComponent,
    PaginationComponent,
    MoneyPipe,
    PeTimestampPipe,
  ],
  templateUrl: './movimientos.component.html',
})
export class MovimientosComponent implements OnInit {
  private readonly ventas = inject(VentasService);
  private readonly route = inject(ActivatedRoute);
  private readonly session = inject(SessionService);
  readonly sedesContext = inject(SedeContextService);

  readonly id = signal<number | null>(null);
  readonly producto = signal<Producto | null>(null);
  readonly movimientos = signal<Movimiento[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly total = signal(0);
  readonly rangoAplicado = signal(false);
  readonly sedeSeleccionada = computed(() => this.sedesContext.selectedId());

  inicio = '';
  fin = '';

  constructor() {
    effect(() => {
      const idHotel = this.sedeSeleccionada();
      const id = this.id();
      untracked(() => {
        if (!idHotel || id === null) {
          this.movimientos.set([]);
          this.total.set(0);
          this.loading.set(false);
          return;
        }
        void this.cargar(0, idHotel);
      });
    });
  }

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    this.id.set(idParam ? Number(idParam) : null);
    if (this.id() !== null) void this.cargarProducto();
  }

  async cargarProducto(): Promise<void> {
    const id = this.id();
    const idHotel = this.sedeSeleccionada();
    if (id === null || !idHotel) return;
    try {
      const res = await this.ventas.buscarProducto(id, idHotel);
      this.producto.set(res.data);
    } catch {
      // El título no bloquea la tabla: el error real se muestra al listar.
      this.producto.set(null);
    }
  }

  async cargar(page = this.page(), idHotel = this.sedeSeleccionada()): Promise<void> {
    const id = this.id();
    if (id === null || !idHotel) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await this.ventas.listarMovimientos(
        id,
        idHotel,
        page,
        this.size(),
        this.isoInicio(),
        this.isoFin()
      );
      this.movimientos.set(res.data);
      this.page.set(res.page);
      this.size.set(res.size);
      this.total.set(res.totalElements);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.movimientos.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  filtrar(): void {
    this.rangoAplicado.set(!!this.inicio || !!this.fin);
    void this.cargar(0);
  }

  limpiar(): void {
    this.inicio = '';
    this.fin = '';
    this.rangoAplicado.set(false);
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

  etiquetaTipo(m: Movimiento): string {
    return m.tipo === 'VENTA' ? 'Venta' : 'Ajuste';
  }

  deltaConSigno(delta: number): string {
    return delta > 0 ? `+${delta}` : `\u2212${Math.abs(delta)}`;
  }

  private isoInicio(): string | null {
    return this.inicio ? new Date(`${this.inicio}T00:00:00`).toISOString() : null;
  }

  private isoFin(): string | null {
    return this.fin ? new Date(`${this.fin}T23:59:59`).toISOString() : null;
  }
}
