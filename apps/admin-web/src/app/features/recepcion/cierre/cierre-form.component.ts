import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { API_BASE_URL, API_PATHS } from '../../../core/api.config';
import {
  Cotizacion,
  MedioPago,
  Recepcion,
  SalidaInput,
} from '../../../core/models/recepcion.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { PeDatePipe } from '../../../core/pipes/date.pipe';
import { PeTimestampPipe } from '../../../core/pipes/timestamp.pipe';
import { RecepcionService, etagDeVersion } from '../../../core/services/recepcion.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PendingBannerComponent } from '../../../ui/pending-banner/pending-banner.component';

interface ErroresCierre {
  costoPenalidad?: string;
  motivoPenalidad?: string;
  montoRecibido?: string;
  medioPago?: string;
}

const MENSAJES = {
  penalidadExcede: 'La penalidad no puede superar el alojamiento',
  penalidadNegativa: 'La penalidad no puede ser negativa',
  motivoRequerido: 'Indica el motivo de la penalidad',
  motivoLargo: 'Máximo 300 caracteres',
  montoNegativo: 'El monto no puede ser negativo',
  montoRequerido: 'Ingresa el monto recibido',
  montoNoCoincide: 'El monto no coincide con el calculado. Revisa la nueva cotización.',
  medioPago: 'Selecciona el medio de pago',
} as const;

/**
 * `/app/recepcion/:id/cierre` (A/E): cotización informativa
 * (`GET /api/recepcion/{id}/cotizacion-salida`) y cierre
 * (`POST /api/recepcion/registrar-salida`).
 *
 * El backend calcula todos los importes: el formulario solo envía penalidad,
 * motivo, «Monto recibido ahora» y medio de pago. Un 202 no es éxito: abre el
 * banner «cierre pendiente, no volver a cobrar» y sondea `Location` según
 * `Retry-After`. Un 409 `AMOUNT_MISMATCH` muestra la cotización nueva recibida y
 * permite confirmarla como intención nueva (clave nueva).
 */
@Component({
  selector: 'app-cierre-form',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    ErrorBannerComponent,
    PendingBannerComponent,
    MoneyPipe,
    PeDatePipe,
    PeTimestampPipe,
  ],
  templateUrl: './cierre-form.component.html',
  styleUrl: './cierre-form.component.css',
})
export class CierreFormComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly recepcionService = inject(RecepcionService);
  private readonly session = inject(SessionService);
  private readonly toast = inject(ToastService);

  private readonly id = Number(this.route.snapshot.paramMap.get('id'));

  readonly estadia = signal<Recepcion | null>(null);
  readonly etag = signal<string | null>(null);
  readonly cotizacion = signal<Cotizacion | null>(null);
  readonly loading = signal(true);
  readonly recotizando = signal(false);
  readonly guardando = signal(false);
  readonly error = signal<ApiUiError | null>(null);
  readonly errores = signal<ErroresCierre>({});

  /** 409 AMOUNT_MISMATCH: nueva cotización y `closureId` recibidos del backend. */
  readonly cotizacionRechazada = signal<Cotizacion | null>(null);
  readonly closureIdRechazado = signal<string | null>(null);

  readonly pendiente = signal(false);
  readonly actualizadoEn = signal<Date | null>(null);
  readonly detallePendiente = signal('');

  readonly costoPenalidad = signal(0);
  readonly motivoPenalidad = signal('');
  readonly montoRecibido = signal<number | null>(null);
  readonly medioPago = signal<MedioPago | ''>('');

  readonly puedeCerrar = computed(() => {
    const e = this.estadia();
    return !!e && e.estadoEstadia === 'ACTIVA' && !this.pendiente() && !this.guardando();
  });

  private abortador: AbortController | null = null;

  ngOnInit(): void {
    void this.cargar();
  }

  ngOnDestroy(): void {
    this.abortador?.abort();
  }

  async cargar(preservarError = false): Promise<void> {
    const errorPrevio = this.error();
    this.loading.set(true);
    if (!preservarError) this.error.set(null);
    try {
      const res = await this.recepcionService.buscarEstadia(this.id);
      this.estadia.set(res.data);
      this.etag.set(res.etag);
      if (res.data.estadoEstadia === 'CERRANDO') {
        this.iniciarPolling(
          this.locationDelCierre(res.data),
          2000,
          'El cierre está en curso; no vuelvas a cobrar.'
        );
      }
      await this.recotizar();
    } catch (err) {
      this.estadia.set(null);
      this.error.set(parseApiError(err));
    } finally {
      if (preservarError && errorPrevio) this.error.set(errorPrevio);
      this.loading.set(false);
    }
  }

  /** Recotiza por query; solo cambia el desglose, no el resto del detalle. */
  async recotizar(): Promise<void> {
    if (!this.estadia()) return;
    this.recotizando.set(true);
    try {
      const cotizacion = await this.recepcionService.cotizarSalida(
        this.id,
        this.costoPenalidad()
      );
      this.cotizacion.set(cotizacion);
    } catch (err) {
      this.error.set(parseApiError(err));
    } finally {
      this.recotizando.set(false);
    }
  }

  async cambiarPenalidad(valor: number | string): Promise<void> {
    const numero = Number(valor);
    this.costoPenalidad.set(Number.isFinite(numero) ? numero : 0);
    await this.recotizar();
  }

  cambiarMonto(valor: number | string): void {
    if (valor === '' || valor === null || valor === undefined) {
      this.montoRecibido.set(null);
      return;
    }
    const numero = Number(valor);
    this.montoRecibido.set(Number.isFinite(numero) ? numero : null);
  }

  usarMontoCalculado(): void {
    const cotizacion = this.cotizacion();
    if (cotizacion) this.montoRecibido.set(cotizacion.cobroAhora);
  }

  validar(): boolean {
    const e = this.estadia();
    const errores: ErroresCierre = {};
    const penalidad = this.costoPenalidad();
    if (!Number.isFinite(penalidad) || penalidad < 0) {
      errores.costoPenalidad = MENSAJES.penalidadNegativa;
    } else if (e && penalidad > e.precioInicial) {
      errores.costoPenalidad = MENSAJES.penalidadExcede;
    }

    const motivo = this.motivoPenalidad().trim();
    if (penalidad > 0) {
      if (!motivo) errores.motivoPenalidad = MENSAJES.motivoRequerido;
      else if (motivo.length > 300) errores.motivoPenalidad = MENSAJES.motivoLargo;
    }

    const monto = this.montoRecibido();
    if (monto === null || monto === undefined || Number.isNaN(Number(monto))) {
      errores.montoRecibido = MENSAJES.montoRequerido;
    } else if (Number(monto) < 0) {
      errores.montoRecibido = MENSAJES.montoNegativo;
    }

    if (!this.medioPago()) errores.medioPago = MENSAJES.medioPago;

    this.errores.set(errores);
    return Object.keys(errores).length === 0;
  }

  async cerrar(): Promise<void> {
    if (this.guardando() || this.pendiente()) return;
    const e = this.estadia();
    if (!e) return;
    if (!this.validar()) return;

    this.guardando.set(true);
    this.error.set(null);
    this.cotizacionRechazada.set(null);
    this.closureIdRechazado.set(null);
    try {
      const body: SalidaInput = {
        idRecepcion: e.idRecepcion,
        idHabitacion: e.idHabitacion,
        costoPenalidad: this.costoPenalidad(),
        montoRecibido: Number(this.montoRecibido()),
        medioPago: this.medioPago() as MedioPago,
        ...(this.costoPenalidad() > 0
          ? { motivoPenalidad: this.motivoPenalidad().trim() }
          : {}),
      };
      const res = await this.recepcionService.registrarSalida(body);
      this.estadia.set(res.recepcion);
      if (res.kind === 'pendiente') {
        this.iniciarPolling(
          res.location,
          res.retryAfterMs,
          'cierre pendiente, no volver a cobrar'
        );
      } else {
        this.etag.set(etagDeVersion(res.recepcion.version));
        this.toast.show('Estadía cerrada');
        await this.router.navigate(['/app/recepcion', e.idRecepcion]);
      }
    } catch (err) {
      this.aplicarError(err);
    } finally {
      this.guardando.set(false);
    }
  }

  /** Confirma la cotización nueva del 409 como intención nueva (clave nueva). */
  async confirmarConNuevaCotizacion(): Promise<void> {
    const nueva = this.cotizacionRechazada();
    if (!nueva) return;
    this.cotizacion.set(nueva);
    this.montoRecibido.set(nueva.cobroAhora);
    this.cotizacionRechazada.set(null);
    this.closureIdRechazado.set(null);
    this.error.set(null);
    await this.cerrar();
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    this.error.set(null);
    if (err?.reloadable) void this.cargar();
  }

  actionLabel(): string | null {
    const err = this.error();
    if (!err) return null;
    if (err.reloadable) return 'Recargar';
    if (err.retryable) return 'Reintentar';
    return null;
  }

  contadorMotivo(): string {
    return `${this.motivoPenalidad().length}/300`;
  }

  private aplicarError(err: unknown): void {
    const apiError = parseApiError(err);
    if (apiError.code === 'AMOUNT_MISMATCH') {
      const data = (err as any)?.error?.data as
        | { cotizacion?: Cotizacion; closureId?: string }
        | undefined;
      if (data?.cotizacion) {
        this.cotizacionRechazada.set(data.cotizacion);
        this.closureIdRechazado.set(data.closureId ?? null);
      }
    }
    this.error.set(apiError);
    // 412/428 y las acciones esperadas de 409 que cambian el estado de la
    // estadía releen el recurso conservando el mensaje del catálogo:
    // STAY_NOT_ACTIVE → recargar el estado; CLOSURE_IN_PROGRESS → consultar la
    // estadía antes de reintentar. AMOUNT_MISMATCH no recarga: muestra la
    // cotización nueva que ya viene en el cuerpo.
    if (
      apiError.code === 'PRECONDITION_FAILED' ||
      apiError.code === 'PRECONDITION_REQUIRED' ||
      apiError.code === 'STAY_NOT_ACTIVE' ||
      apiError.code === 'CLOSURE_IN_PROGRESS'
    ) {
      void this.cargar(true);
    }
  }

  private locationDelCierre(e: Recepcion): string {
    return `${API_BASE_URL}${API_PATHS.recepcionBuscar(e.idRecepcion)}`;
  }

  private iniciarPolling(location: string, retryAfterMs: number, detalle: string): void {
    this.abortador?.abort();
    const abortador = new AbortController();
    this.abortador = abortador;
    this.pendiente.set(true);
    this.detallePendiente.set(detalle);
    this.actualizadoEn.set(null);
    void this.recepcionService
      .seguirCierre(
        location,
        retryAfterMs,
        (avance) => {
          this.actualizadoEn.set(avance.actualizadoEn);
          this.estadia.set(avance.recepcion);
        },
        abortador.signal
      )
      .then((ultimo) => {
        if (abortador.signal.aborted || !ultimo) return;
        this.pendiente.set(false);
        if (ultimo.etag) this.etag.set(ultimo.etag);
        const e = ultimo.recepcion;
        if (e.estadoEstadia === 'CERRADA') {
          this.toast.show('Estadía cerrada');
          void this.router.navigate(['/app/recepcion', e.idRecepcion]);
        }
      })
      .catch((err) => {
        if (abortador.signal.aborted) return;
        this.pendiente.set(false);
        this.error.set(parseApiError(err));
      });
  }
}
