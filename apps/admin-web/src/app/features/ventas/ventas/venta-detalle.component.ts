import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { MedioPago, Venta } from '../../../core/models/ventas.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { PeTimestampPipe } from '../../../core/pipes/timestamp.pipe';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { VentasService, etagDeVersion } from '../../../core/services/ventas.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PendingBannerComponent } from '../../../ui/pending-banner/pending-banner.component';

/**
 * `/app/ventas/:id` (A/E): detalle con las líneas inmutables tal como llegan
 * del backend (sin recalcular precios ni totales), pago manual y anulación
 * impagada.
 *
 * Un 202 no es éxito: abre el banner persistente y sondea `Location` según
 * `Retry-After` y `GET /api/venta/operaciones/{operationId}` hasta el estado
 * terminal; mientras dura, las acciones que duplicarían la intención quedan
 * deshabilitadas. 200 pinta el estado real; 409/412 usan el catálogo.
 */
@Component({
  selector: 'app-venta-detalle',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    ErrorBannerComponent,
    PendingBannerComponent,
    MoneyPipe,
    PeTimestampPipe,
  ],
  templateUrl: './venta-detalle.component.html',
})
export class VentaDetalleComponent implements OnInit, OnDestroy {
  private readonly ventasService = inject(VentasService);
  private readonly route = inject(ActivatedRoute);
  private readonly session = inject(SessionService);
  private readonly toast = inject(ToastService);

  readonly id = signal<number | null>(null);
  readonly venta = signal<Venta | null>(null);
  readonly etag = signal<string | null>(null);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly pendiente = signal(false);
  readonly detallePendiente = signal('');
  readonly actualizadoEn = signal<Date | null>(null);
  readonly procesando = signal(false);

  readonly pagoAbierto = signal(false);
  readonly anulacionAbierta = signal(false);
  medioPago: MedioPago | '' = '';
  motivo = '';
  erroresPago = signal<Record<string, string>>({});
  erroresAnulacion = signal<Record<string, string>>({});

  readonly puedePagar = computed(() => {
    const v = this.venta();
    return (
      !!v &&
      !this.pendiente() &&
      v.estado === 'PENDIENTE' &&
      v.estadoOperacion === 'CONFIRMADA' &&
      !this.procesando()
    );
  });

  readonly puedeAnular = computed(() => {
    const v = this.venta();
    return (
      !!v &&
      !this.pendiente() &&
      v.estado === 'PENDIENTE' &&
      v.estadoOperacion === 'CONFIRMADA' &&
      !this.procesando()
    );
  });

  private abortador: AbortController | null = null;

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    this.id.set(idParam ? Number(idParam) : null);
    void this.cargar();
  }

  ngOnDestroy(): void {
    this.abortador?.abort();
  }

  async cargar(): Promise<void> {
    const id = this.id();
    if (id === null) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await this.ventasService.buscarVenta(id);
      this.venta.set(res.data);
      this.etag.set(res.etag);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.venta.set(null);
    } finally {
      this.loading.set(false);
    }
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    if (err?.reloadable) {
      void this.cargar();
      return;
    }
    this.error.set(null);
  }

  sinRegistrar(valor: string | null | undefined): string {
    return valor ? valor : 'No registrado';
  }

  abrirPago(): void {
    if (!this.puedePagar()) return;
    this.erroresPago.set({});
    this.medioPago = '';
    this.pagoAbierto.set(true);
  }

  cerrarPago(): void {
    if (this.procesando()) return;
    this.pagoAbierto.set(false);
  }

  abrirAnulacion(): void {
    if (!this.puedeAnular()) return;
    this.erroresAnulacion.set({});
    this.motivo = '';
    this.anulacionAbierta.set(true);
  }

  cerrarAnulacion(): void {
    if (this.procesando()) return;
    this.anulacionAbierta.set(false);
  }

  campoPagoInvalido(): boolean {
    return !!this.erroresPago()['medioPago'];
  }

  campoAnulacionInvalido(): boolean {
    return !!this.erroresAnulacion()['motivo'];
  }

  async confirmarPago(event: Event): Promise<void> {
    event.preventDefault();
    const v = this.venta();
    if (!v || this.procesando()) return;
    if (!this.medioPago) {
      this.erroresPago.set({ medioPago: 'Selecciona el medio de pago' });
      return;
    }
    this.procesando.set(true);
    this.error.set(null);
    try {
      const res = await this.ventasService.pagarVenta(v.idVenta, this.medioPago, this.ifMatch());
      this.venta.set(res.venta);
      this.pagoAbierto.set(false);
      if (res.kind === 'pendiente') {
        this.iniciarPolling(
          res.location,
          res.retryAfterMs,
          'El pago se está aplicando; el estado se actualizará solo.'
        );
      } else {
        this.etag.set(etagDeVersion(res.venta.version));
        this.toast.show('Pagado');
      }
    } catch (err) {
      this.error.set(parseApiError(err));
      this.pagoAbierto.set(false);
    } finally {
      this.procesando.set(false);
    }
  }

  async confirmarAnulacion(event: Event): Promise<void> {
    event.preventDefault();
    const v = this.venta();
    if (!v || this.procesando()) return;
    const motivo = this.motivo.trim();
    if (!motivo) {
      this.erroresAnulacion.set({ motivo: 'Indica el motivo de la anulación' });
      return;
    }
    if (motivo.length > 300) {
      this.erroresAnulacion.set({ motivo: 'Máximo 300 caracteres' });
      return;
    }
    this.procesando.set(true);
    this.error.set(null);
    try {
      const res = await this.ventasService.anularVenta(v.idVenta, motivo, this.ifMatch());
      this.venta.set(res.venta);
      this.anulacionAbierta.set(false);
      if (res.kind === 'pendiente') {
        this.iniciarPolling(
          res.location,
          res.retryAfterMs,
          'La anulación se está aplicando; el stock volverá a la sede al confirmar.'
        );
      } else {
        this.etag.set(etagDeVersion(res.venta.version));
        this.toast.show('Venta anulada');
      }
    } catch (err) {
      this.error.set(parseApiError(err));
      this.anulacionAbierta.set(false);
    } finally {
      this.procesando.set(false);
    }
  }

  private ifMatch(): string {
    return this.etag() ?? etagDeVersion(this.venta()?.version ?? 1);
  }

  private iniciarPolling(location: string, retryAfterMs: number, detalle: string): void {
    this.abortador?.abort();
    const abortador = new AbortController();
    this.abortador = abortador;
    this.pendiente.set(true);
    this.detallePendiente.set(detalle);
    this.actualizadoEn.set(null);
    void this.ventasService
      .seguirOperacion(
        location,
        retryAfterMs,
        (avance) => {
          this.actualizadoEn.set(avance.actualizadoEn);
          if (avance.venta) this.venta.set(avance.venta);
        },
        abortador.signal
      )
      .then(async (ultimo) => {
        if (abortador.signal.aborted || !ultimo) return;
        await this.cargar();
        this.pendiente.set(false);
        const v = this.venta();
        if (v?.estado === 'PAGADO') this.toast.show('Pagado');
        else if (v?.estadoOperacion === 'CANCELADA') this.toast.show('Venta anulada');
      })
      .catch((err) => {
        if (abortador.signal.aborted) return;
        this.pendiente.set(false);
        this.error.set(parseApiError(err));
      });
  }
}
