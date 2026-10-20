import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import {
  EstadoOperacionVenta,
  EstadoVenta,
  Venta,
} from '../../../core/models/cliente.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { PeTimestampPipe } from '../../../core/pipes/timestamp.pipe';
import { ClienteContextService } from '../../../core/services/cliente-context.service';
import { ClienteService } from '../../../core/services/cliente.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';

/**
 * `/mis-ventas/:id`: detalle propio con líneas inmutables. `medioPago` nulo se
 * muestra como no registrado; fechas en `dd/MM/yyyy HH:mm`; importes tal como
 * llegan del backend, sin recalcular. Sin acciones de pago ni anulación.
 */
@Component({
  selector: 'app-venta-detalle',
  standalone: true,
  imports: [RouterLink, ErrorBannerComponent, MoneyPipe, PeTimestampPipe],
  templateUrl: './venta-detalle.component.html',
  styleUrl: './venta-detalle.component.css',
})
export class VentaDetalleComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly cliente = inject(ClienteService);
  private readonly contexto = inject(ClienteContextService);

  readonly cargando = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly venta = signal<Venta | null>(null);

  async ngOnInit(): Promise<void> {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isFinite(id) || id <= 0) {
      this.error.set(this.errorNoEncontrado());
      this.cargando.set(false);
      return;
    }
    await this.cargar(id);
  }

  async cargar(id?: number): Promise<void> {
    const objetivo = id ?? this.venta()?.idVenta;
    if (!objetivo) {
      this.error.set(this.errorNoEncontrado());
      this.cargando.set(false);
      return;
    }
    this.cargando.set(true);
    this.error.set(null);
    try {
      const venta = await this.cliente.buscarVenta(objetivo);
      const propio = this.contexto.idCliente();
      if (propio !== null && venta.idCliente !== propio) {
        this.venta.set(null);
        this.error.set(this.errorNoEncontrado());
        return;
      }
      this.venta.set(venta);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.venta.set(null);
    } finally {
      this.cargando.set(false);
    }
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

  private errorNoEncontrado(): ApiUiError {
    return parseApiError({
      status: 404,
      error: {
        message: 'No encontramos lo que buscas.',
        error: { code: 'RESOURCE_NOT_FOUND', fields: {} },
        meta: { correlationId: '' },
      },
    });
  }
}
