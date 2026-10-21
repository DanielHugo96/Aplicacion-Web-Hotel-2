import { Component, computed, effect, inject, OnDestroy, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { EstadoOperacionVenta, EstadoVenta, Venta } from '../../../core/models/ventas.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { PeTimestampPipe } from '../../../core/pipes/timestamp.pipe';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { VentasService } from '../../../core/services/ventas.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PaginationComponent } from '../../../ui/pagination/pagination.component';

const ESPERA_SONDEO_MS = 2000;
const OPERACION_TERMINAL: EstadoOperacionVenta[] = ['CONFIRMADA', 'RECHAZADA', 'CANCELADA'];

function esperar(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * `/app/ventas` (A/E): ventas de la sede con los filtros de pago y operación
 * del contrato. «Pago» y «Operación» son píldoras separadas; los importes van
 * a la derecha con cifras tabulares y no se recalculan. Las filas no
 * terminales (`PENDIENTE`, `CANCELACION_PENDIENTE`) sondean
 * `GET /api/venta/operaciones/{operationId}` y refrescan al llegar al estado
 * terminal; nunca se pinta un éxito optimista.
 */
@Component({
  selector: 'app-ventas-list',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    ErrorBannerComponent,
    PaginationComponent,
    MoneyPipe,
    PeTimestampPipe,
  ],
  templateUrl: './ventas-list.component.html',
})
export class VentasListComponent implements OnDestroy {
  private readonly ventasService = inject(VentasService);
  private readonly session = inject(SessionService);
  readonly sedesContext = inject(SedeContextService);

  readonly sedeSeleccionada = computed(() => this.sedesContext.selectedId());
  readonly ventas = signal<Venta[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly total = signal(0);

  estado: EstadoVenta | '' = '';
  estadoOperacion: EstadoOperacionVenta | '' = '';

  private destruido = false;
  private readonly sondeos = new Set<string>();

  constructor() {
    effect(() => {
      const idHotel = this.sedeSeleccionada();
      untracked(() => {
        if (!idHotel) {
          this.ventas.set([]);
          this.total.set(0);
          this.loading.set(false);
          return;
        }
        void this.cargar(0, idHotel);
      });
    });
  }

  ngOnDestroy(): void {
    this.destruido = true;
  }

  async cargar(page = this.page(), idHotel = this.sedeSeleccionada()): Promise<void> {
    if (!idHotel) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await this.ventasService.listarVentas(
        idHotel,
        page,
        this.size(),
        this.estado || null,
        this.estadoOperacion || null
      );
      this.ventas.set(res.data);
      this.page.set(res.page);
      this.size.set(res.size);
      this.total.set(res.totalElements);
      this.sondearNoTerminales(res.data);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.ventas.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  filtrar(): void {
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

  esNoTerminal(v: Venta): boolean {
    return v.estadoOperacion === 'PENDIENTE' || v.estadoOperacion === 'CANCELACION_PENDIENTE';
  }

  clasePago(v: Venta): string {
    return v.estado === 'PAGADO' ? 'pill pill-ok' : 'pill pill-atencion';
  }

  claseOperacion(v: Venta): string {
    switch (v.estadoOperacion) {
      case 'CONFIRMADA':
        return 'pill pill-ok';
      case 'RECHAZADA':
        return 'pill pill-error';
      case 'CANCELACION_PENDIENTE':
      case 'PENDIENTE':
        return 'pill pill-proceso';
      default:
        return 'pill pill-archivado';
    }
  }

  private sondearNoTerminales(lista: Venta[]): void {
    for (const v of lista) {
      if (this.esNoTerminal(v)) void this.seguirFila(v);
    }
  }

  private async seguirFila(v: Venta): Promise<void> {
    if (this.sondeos.has(v.operationId)) return;
    this.sondeos.add(v.operationId);
    try {
      while (!this.destruido) {
        const operacion = await this.ventasService.consultarOperacion(v.operationId);
        if (OPERACION_TERMINAL.includes(operacion.estadoOperacion)) {
          await this.cargar();
          return;
        }
        await esperar(ESPERA_SONDEO_MS);
        if (this.destruido) return;
      }
    } catch (err) {
      if (!this.destruido) this.error.set(parseApiError(err));
    } finally {
      this.sondeos.delete(v.operationId);
    }
  }
}
