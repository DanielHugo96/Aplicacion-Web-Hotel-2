import { Component, EventEmitter, Input, Output, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { Recepcion } from '../../../core/models/recepcion.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { ConfirmDialogComponent } from '../../../ui/confirm-dialog/confirm-dialog.component';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
/**
 * Bloque de cierre dentro de `/app/recepcion/:id`: estado del cierre vigente con
 * su `closureId`, `lastErrorCode` y cálculo congelado, más las acciones de A/E
 * (cotizar y cerrar) y de A (anular check-in y archivar). No duplica la lectura
 * base del detalle: recibe la estadía y el ETag que ya cargó la pantalla.
 *
 * Un 412/428 recarga el detalle sin sobrescribir; un 409 usa el mensaje del
 * catálogo y deja el estado como estaba.
 */
@Component({
  selector: 'app-cierre-panel',
  standalone: true,
  imports: [
    RouterLink,
    ErrorBannerComponent,
    ConfirmDialogComponent,
    MoneyPipe,
  ],
  templateUrl: './cierre-panel.component.html',
  styleUrl: './cierre-panel.component.css',
})
export class CierrePanelComponent {
  private readonly recepcionService = inject(RecepcionService);
  private readonly session = inject(SessionService);
  private readonly toast = inject(ToastService);

  @Input({ required: true }) recepcion!: Recepcion;
  @Input() etag: string | null = null;

  /** Pide al detalle recargar; el 412 no sobrescribe datos locales. */
  @Output() recargar = new EventEmitter<void>();

  readonly error = signal<ApiUiError | null>(null);
  readonly procesando = signal(false);

  readonly anularAbierto = signal(false);
  readonly archivarAbierto = signal(false);
  readonly motivoAnulacion = signal('');
  readonly errorMotivo = signal<string | null>(null);

  get esAdmin(): boolean {
    return this.session.getRol() === 'ADMIN';
  }

  get puedeAnular(): boolean {
    return (
      this.esAdmin &&
      this.recepcion.estadoEstadia === 'ACTIVA' &&
      !this.recepcion.archivada &&
      !this.procesando()
    );
  }

  get puedeArchivar(): boolean {
    return (
      this.esAdmin &&
      this.recepcion.estadoEstadia === 'CERRADA' &&
      !this.recepcion.archivada &&
      !this.procesando()
    );
  }

  get tieneCierre(): boolean {
    return this.recepcion.cierre !== null;
  }

  etiquetaCierre(estado: NonNullable<Recepcion['cierre']>['estado']): string {
    switch (estado) {
      case 'VALIDANDO':
        return 'Validando';
      case 'CONFIRMANDO':
        return 'Confirmando';
      case 'COMPLETADO':
        return 'Completado';
      case 'RECHAZADO':
        return 'Rechazado';
    }
  }

  claseCierre(estado: NonNullable<Recepcion['cierre']>['estado']): string {
    switch (estado) {
      case 'COMPLETADO':
        return 'pill pill-ok';
      case 'RECHAZADO':
        return 'pill pill-error';
      default:
        return 'pill pill-proceso';
    }
  }

  abrirAnulacion(): void {
    if (!this.puedeAnular) return;
    this.motivoAnulacion.set('');
    this.errorMotivo.set(null);
    this.error.set(null);
    this.anularAbierto.set(true);
  }

  cerrarAnulacion(): void {
    if (this.procesando()) return;
    this.anularAbierto.set(false);
  }

  abrirArchivar(): void {
    if (!this.puedeArchivar) return;
    this.error.set(null);
    this.archivarAbierto.set(true);
  }

  cerrarArchivar(): void {
    if (this.procesando()) return;
    this.archivarAbierto.set(false);
  }

  async confirmarAnulacion(): Promise<void> {
    if (this.procesando()) return;
    const motivo = this.motivoAnulacion().trim();
    if (!motivo) {
      this.errorMotivo.set('Indica el motivo de la anulación');
      return;
    }
    if (motivo.length > 300) {
      this.errorMotivo.set('Máximo 300 caracteres');
      return;
    }
    this.procesando.set(true);
    this.error.set(null);
    try {
      await this.recepcionService.anularCheckin(
        this.recepcion.idRecepcion,
        motivo,
        this.ifMatch()
      );
      this.anularAbierto.set(false);
      this.toast.show('Check-in anulado');
      this.recargar.emit();
    } catch (err) {
      this.procesando.set(false);
      this.anularAbierto.set(false);
      this.aplicarError(err);
      return;
    }
    this.procesando.set(false);
  }

  async confirmarArchivar(): Promise<void> {
    if (this.procesando()) return;
    this.procesando.set(true);
    this.error.set(null);
    try {
      await this.recepcionService.archivarRecepcion(this.recepcion.idRecepcion, this.ifMatch());
      this.archivarAbierto.set(false);
      this.toast.show('Estadía archivada');
      this.recargar.emit();
    } catch (err) {
      this.procesando.set(false);
      this.archivarAbierto.set(false);
      this.aplicarError(err);
      return;
    }
    this.procesando.set(false);
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    this.error.set(null);
    if (err?.reloadable) this.recargar.emit();
  }

  actionLabel(): string | null {
    const err = this.error();
    if (!err) return null;
    if (err.reloadable) return 'Recargar';
    if (err.retryable) return 'Reintentar';
    return null;
  }

  /** Un 412/428 deja el ETag viejo: hay que releer antes de reintentar. */
  private aplicarError(err: unknown): void {
    const apiError = parseApiError(err);
    this.error.set(apiError);
    // La acción esperada de 412/428 es recargar con el ETag nuevo, y la de
    // STAY_NOT_ACTIVE/CLOSURE_IN_PROGRESS es recargar el estado de la estadía:
    // el panel solo emite la recarga, que la pantalla padre resuelve.
    if (
      apiError.code === 'PRECONDITION_FAILED' ||
      apiError.code === 'PRECONDITION_REQUIRED' ||
      apiError.code === 'STAY_NOT_ACTIVE' ||
      apiError.code === 'CLOSURE_IN_PROGRESS'
    ) {
      this.recargar.emit();
    }
  }

  private ifMatch(): string {
    return this.etag ?? `"${this.recepcion.version}"`;
  }
}
