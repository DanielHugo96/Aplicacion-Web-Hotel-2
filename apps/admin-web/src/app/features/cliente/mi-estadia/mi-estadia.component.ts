import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { Recepcion } from '../../../core/models/cliente.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { PeDatePipe } from '../../../core/pipes/date.pipe';
import { ClienteContextService } from '../../../core/services/cliente-context.service';
import { ClienteService } from '../../../core/services/cliente.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';

/**
 * `/mi-estadia`: estadía propia del cliente. Lee primero
 * `GET /api/recepcion/cliente/{idCliente}` con el `sub` del JWT y luego el
 * detalle `GET /api/recepcion/{id}` propio. Nunca muestra datos de terceros.
 */
@Component({
  selector: 'app-mi-estadia',
  standalone: true,
  imports: [RouterLink, ErrorBannerComponent, MoneyPipe, PeDatePipe],
  templateUrl: './mi-estadia.component.html',
  styleUrl: './mi-estadia.component.css',
})
export class MiEstadiaComponent implements OnInit {
  private readonly contexto = inject(ClienteContextService);
  private readonly cliente = inject(ClienteService);

  readonly cargando = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly estadia = signal<Recepcion | null>(null);
  /** ETag leído del detalle; en el área C solo se guarda, no se usa para mutar. */
  readonly etag = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    await this.cargar();
  }

  async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    this.estadia.set(null);
    this.etag.set(null);

    await this.contexto.cargar(true);
    const errorContexto = this.contexto.error();
    if (errorContexto) {
      this.error.set(errorContexto);
      this.cargando.set(false);
      return;
    }

    const activa = this.contexto.activa();
    if (!activa) {
      this.cargando.set(false);
      return;
    }

    try {
      const res = await this.cliente.buscarEstadia(activa.idRecepcion);
      this.estadia.set(res.data);
      this.etag.set(res.etag);
    } catch (err) {
      this.error.set(parseApiError(err));
    } finally {
      this.cargando.set(false);
    }
  }

  estadoEtiqueta(estado: Recepcion['estadoEstadia']): string {
    switch (estado) {
      case 'ACTIVA':
        return 'Activa';
      case 'CERRANDO':
        return 'Cierre en curso';
      case 'CERRADA':
        return 'Cerrada';
    }
  }

  estadoClase(estado: Recepcion['estadoEstadia']): string {
    switch (estado) {
      case 'ACTIVA':
        return 'pill-ok';
      case 'CERRANDO':
        return 'pill-atencion';
      case 'CERRADA':
        return 'pill-archivado';
    }
  }

  medioPagoEtiqueta(medio: Recepcion['medioPagoAdelanto']): string {
    return medio ?? 'No registrado';
  }
}
