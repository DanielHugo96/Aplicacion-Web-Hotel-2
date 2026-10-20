import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { EstadoOperacionVenta, EstadoVenta, Venta } from '../../../core/models/cliente.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { PeTimestampPipe } from '../../../core/pipes/timestamp.pipe';
import { ClienteContextService } from '../../../core/services/cliente-context.service';
import { ClienteService } from '../../../core/services/cliente.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PaginationComponent } from '../../../ui/pagination/pagination.component';

/**
 * `/mis-ventas`: el contrato no publica un listado global para C, así que se
 * arman las ventas de las estadías propias con
 * `GET /api/venta/recepcion/{idRecepcion}` (paginado por `meta`) y se pagina
 * el conjunto ya unido. Solo se conservan las ventas del propio `idCliente`.
 */
@Component({
  selector: 'app-mis-ventas',
  standalone: true,
  imports: [
    RouterLink,
    ErrorBannerComponent,
    MoneyPipe,
    PeTimestampPipe,
    PaginationComponent,
  ],
  templateUrl: './mis-ventas.component.html',
  styleUrl: './mis-ventas.component.css',
})
export class MisVentasComponent implements OnInit {
  private readonly contexto = inject(ClienteContextService);
  private readonly cliente = inject(ClienteService);

  readonly cargando = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly ventas = signal<Venta[]>([]);
  readonly pagina = signal(0);
  readonly total = computed(() => this.ventas().length);
  readonly size = 20;

  readonly visibles = computed(() => {
    const desde = this.pagina() * this.size;
    return this.ventas().slice(desde, desde + this.size);
  });

  async ngOnInit(): Promise<void> {
    await this.cargar();
  }

  async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    this.pagina.set(0);
    await this.contexto.cargar(true);
    const errorContexto = this.contexto.error();
    if (errorContexto) {
      this.error.set(errorContexto);
      this.cargando.set(false);
      return;
    }

    const propias = this.contexto.estadias();
    const idCliente = this.contexto.idCliente();
    if (propias.length === 0 || !idCliente) {
      this.ventas.set([]);
      this.cargando.set(false);
      return;
    }

    const idsPropios = new Set(propias.map((e) => e.idRecepcion));
    const acumuladas: Venta[] = [];

    try {
      for (const estadia of propias) {
        let page = 0;
        let totalElementos = Number.POSITIVE_INFINITY;
        let recibidas = 0;
        while (recibidas < totalElementos && page < 100) {
          const res = await this.cliente.listarVentasRecepcion(estadia.idRecepcion, page, 100);
          for (const venta of res.data) {
            if (venta.idCliente === idCliente && idsPropios.has(venta.idRecepcion)) {
              acumuladas.push(venta);
            }
          }
          recibidas += res.data.length;
          totalElementos = res.totalElements;
          page += 1;
          if (res.data.length === 0) break;
        }
      }
      acumuladas.sort((a, b) =>
        (b.fechaCreacion ?? '').localeCompare(a.fechaCreacion ?? '')
      );
      this.ventas.set(acumuladas);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.ventas.set([]);
    } finally {
      this.cargando.set(false);
    }
  }

  cambiarPagina(page: number): void {
    this.pagina.set(page);
  }

  pagoEtiqueta(estado: EstadoVenta): string {
    return estado === 'PAGADO' ? 'Pagado' : 'Pago pendiente';
  }

  pagoClase(estado: EstadoVenta): string {
    return estado === 'PAGADO' ? 'pill-ok' : 'pill-atencion';
  }

  operacionEtiqueta(estado: EstadoOperacionVenta): string {
    switch (estado) {
      case 'CONFIRMADA':
        return 'Confirmada';
      case 'RECHAZADA':
        return 'Rechazada';
      case 'CANCELACION_PENDIENTE':
        return 'Anulación en proceso';
      case 'CANCELADA':
        return 'Anulada';
      case 'PENDIENTE':
        return 'En proceso';
    }
  }

  operacionClase(estado: EstadoOperacionVenta): string {
    switch (estado) {
      case 'CONFIRMADA':
        return 'pill-ok';
      case 'RECHAZADA':
        return 'pill-error';
      case 'CANCELACION_PENDIENTE':
        return 'pill-atencion';
      case 'CANCELADA':
        return 'pill-archivado';
      case 'PENDIENTE':
        return 'pill-proceso';
    }
  }
}
