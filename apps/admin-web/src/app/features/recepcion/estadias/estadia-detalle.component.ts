import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { Recepcion } from '../../../core/models/recepcion.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { PeDatePipe } from '../../../core/pipes/date.pipe';
import { PeTimestampPipe } from '../../../core/pipes/timestamp.pipe';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { CierrePanelComponent } from '../cierre/cierre-panel.component';

interface ErroresSalida {
  fechaSalida?: string;
}

/**
 * `/app/recepcion/:id`: detalle con snapshots de cliente y habitación y
 * edición de `fechaSalida`/`observacion` con `If-Match` del ETag del detalle.
 * Un 412 recarga el detalle sin sobrescribir; un 409 pinta el mensaje del
 * catálogo y la acción esperada.
 */
@Component({
  selector: 'app-estadia-detalle',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    ErrorBannerComponent,
    CierrePanelComponent,
    MoneyPipe,
    PeDatePipe,
    PeTimestampPipe,
  ],
  templateUrl: './estadia-detalle.component.html',
  styleUrl: './estadia-detalle.component.css',
})
export class EstadiaDetalleComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly recepcion = inject(RecepcionService);
  private readonly session = inject(SessionService);
  private readonly toast = inject(ToastService);

  readonly estadia = signal<Recepcion | null>(null);
  readonly etag = signal<string | null>(null);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);

  readonly editando = signal(false);
  readonly fechaSalida = signal('');
  readonly observacion = signal('');
  readonly guardando = signal(false);
  readonly errorSalida = signal<ApiUiError | null>(null);
  readonly errores = signal<ErroresSalida>({});

  private readonly id = Number(this.route.snapshot.paramMap.get('id'));
  readonly idRecepcion = computed(() => this.estadia()?.idRecepcion ?? this.id);

  async ngOnInit(): Promise<void> {
    await this.cargar();
  }

  async cargar(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await this.recepcion.buscarEstadia(this.id);
      this.estadia.set(res.data);
      this.etag.set(res.etag);
      this.reiniciarFormulario(res.data);
    } catch (err) {
      this.estadia.set(null);
      this.etag.set(null);
      this.error.set(parseApiError(err));
    } finally {
      this.loading.set(false);
    }
  }

  abrirEdicion(): void {
    const e = this.estadia();
    if (!e) return;
    this.reiniciarFormulario(e);
    this.errorSalida.set(null);
    this.errores.set({});
    this.editando.set(true);
  }

  cancelarEdicion(): void {
    if (this.guardando()) return;
    this.editando.set(false);
    this.errores.set({});
    this.errorSalida.set(null);
  }

  validar(): boolean {
    const e = this.estadia();
    const errores: ErroresSalida = {};
    const fecha = this.fechaSalida().trim();
    if (!fecha) {
      errores.fechaSalida = 'La fecha de salida no puede ser anterior a hoy';
    } else if (e) {
      if (fecha < e.fechaEntrada) {
        errores.fechaSalida = 'La fecha de salida no puede ser anterior a la entrada.';
      } else {
        const noches = nochesEntre(e.fechaEntrada, fecha);
        if (noches < 1) {
          errores.fechaSalida = 'La fecha de salida no puede ser anterior a hoy';
        } else if (redondear(noches * e.precioHabitacion) < e.adelanto) {
          errores.fechaSalida =
            'La nueva fecha deja el alojamiento por debajo del adelanto registrado.';
        }
      }
    }
    const observacion = this.observacion().trim();
    this.errores.set(errores);
    return Object.keys(errores).length === 0 && observacion.length <= 500;
  }

  async guardar(): Promise<void> {
    if (this.guardando()) return;
    const e = this.estadia();
    const etag = this.etag();
    if (!e || !etag) return;
    if (!this.validar()) return;

    this.guardando.set(true);
    this.errorSalida.set(null);
    try {
      const observacion = this.observacion().trim();
      const res = await this.recepcion.actualizarSalida(
        e.idRecepcion,
        {
          fechaSalida: this.fechaSalida().trim(),
          ...(observacion ? { observacion } : {}),
        },
        etag
      );
      this.estadia.set(res.data);
      this.etag.set(res.etag);
      this.reiniciarFormulario(res.data);
      this.editando.set(false);
      this.toast.show('Cambios guardados');
    } catch (err) {
      const apiError = parseApiError(err);
      this.errorSalida.set(apiError);
      if (apiError.code === 'PRECONDITION_FAILED' || apiError.code === 'PRECONDITION_REQUIRED') {
        // 412/428: recargar el detalle con el ETag nuevo; no sobrescribir.
        await this.cargar();
      } else if (apiError.fields['fechaSalida']) {
        this.errores.set({ fechaSalida: apiError.message });
      }
    } finally {
      this.guardando.set(false);
    }
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    void this.cargar();
  }

  accionErrorSalida(): void {
    const err = this.errorSalida();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    if (err?.retryable) {
      void this.guardar();
      return;
    }
    if (err?.reloadable) {
      this.cerrarYRecargar();
      return;
    }
    this.errorSalida.set(null);
  }

  etiquetaEstado(estado: Recepcion['estadoEstadia']): string {
    switch (estado) {
      case 'ACTIVA':
        return 'Activa';
      case 'CERRANDO':
        return 'Cierre en curso';
      case 'CERRADA':
        return 'Cerrada';
    }
  }

  claseEstado(estado: Recepcion['estadoEstadia']): string {
    switch (estado) {
      case 'ACTIVA':
        return 'pill-ok';
      case 'CERRANDO':
        return 'pill-atencion';
      case 'CERRADA':
        return 'pill-archivado';
    }
  }

  esVencida(e: Recepcion): boolean {
    return e.vencida === true && e.estadoEstadia === 'ACTIVA';
  }

  medioPagoEtiqueta(medio: Recepcion['medioPagoAdelanto']): string {
    return medio ?? 'No registrado';
  }

  actionLabelSalida(): string | null {
    const err = this.errorSalida();
    if (!err) return null;
    if (err.retryable) return 'Reintentar';
    if (err.reloadable) return 'Recargar';
    return null;
  }

  contadorObservacion(): string {
    return `${this.observacion().length}/500`;
  }

  private reiniciarFormulario(e: Recepcion): void {
    this.fechaSalida.set(e.fechaSalida);
    this.observacion.set(e.observacion ?? '');
  }

  private cerrarYRecargar(): void {
    this.editando.set(false);
    this.errorSalida.set(null);
    void this.cargar();
  }
}

function fechaIso(d: Date): string {
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  const dia = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mes}-${dia}`;
}

function nochesEntre(entrada: string, salida: string): number {
  const a = new Date(`${entrada}T00:00:00`);
  const b = new Date(`${salida}T00:00:00`);
  return Math.round((b.getTime() - a.getTime()) / 86_400_000);
}

function redondear(valor: number): number {
  return Math.round((valor + Number.EPSILON) * 100) / 100;
}
