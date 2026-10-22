import { Component, computed, effect, HostListener, inject, OnDestroy, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { Categoria, EstadoFisico, Habitacion, ImagenHabitacion } from '../../../core/models/catalogo.models';
import {
  OperacionHabitacion,
  ResultadoOperacionHabitacion,
  TipoOperacionHabitacion,
} from '../../../core/models/recepcion.models';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PaginationComponent } from '../../../ui/pagination/pagination.component';
import { ConfirmDialogComponent } from '../../../ui/confirm-dialog/confirm-dialog.component';
import { PendingBannerComponent } from '../../../ui/pending-banner/pending-banner.component';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { ImagenPanelComponent } from '../../../shared/imagen-panel.component';

@Component({
  selector: 'app-habitaciones-list',
  standalone: true,
  imports: [
    FormsModule,
    ErrorBannerComponent,
    PaginationComponent,
    ConfirmDialogComponent,
    PendingBannerComponent,
    MoneyPipe,
    ImagenPanelComponent,
  ],
  templateUrl: './habitaciones-list.component.html',
  styleUrl: './habitaciones-list.component.css',
})
export class HabitacionesListComponent implements OnDestroy {
  private readonly catalogo = inject(CatalogoService);
  private readonly recepcion = inject(RecepcionService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  readonly sedesContext = inject(SedeContextService);

  readonly esAdmin = computed(() => this.session.rol() === 'ADMIN');
  readonly sedes = computed(() => this.sedesContext.sedes());
  readonly sedeSeleccionada = computed(() => this.sedesContext.selectedId());
  readonly habitaciones = signal<Habitacion[]>([]);
  readonly categorias = signal<Categoria[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly total = signal(0);
  filtroCategoria: number | null = null;

  // Diálogo de etiquetas de estado físico (solo ADMIN).
  readonly etiquetasOpen = signal(false);
  readonly estados = signal<EstadoFisico[]>([]);
  readonly etiquetaEditando = signal<EstadoFisico | null>(null);
  readonly etiquetaDescripcion = signal('');
  readonly etiquetaGuardando = signal(false);
  readonly etiquetaError = signal<ApiUiError | null>(null);

  // Operaciones administrativas de habitación (pasos 11 a 13).
  readonly operacionError = signal<ApiUiError | null>(null);
  readonly dialogo = signal<TipoOperacionHabitacion | null>(null);
  readonly habitacionOperando = signal<Habitacion | null>(null);
  readonly motivo = signal('');
  readonly motivoError = signal<string | null>(null);
  readonly confirmando = signal(false);
  readonly guardandoOperacion = signal<number | null>(null);
  readonly pendiente = signal<{
    tipo: TipoOperacionHabitacion;
    detalle: string;
    actualizadoEn: Date | null;
  } | null>(null);
  private pollAbort: AbortController | null = null;

  constructor() {
    effect(() => {
      const idHotel = this.sedeSeleccionada();
      untracked(() => {
        if (!idHotel) {
          this.habitaciones.set([]);
          this.loading.set(false);
          return;
        }
        void this.cargar(0, idHotel);
      });
    });
    void this.cargarCategorias();
  }

  async cargarCategorias(): Promise<void> {
    try {
      const res = await this.catalogo.listarCategorias(0, 100);
      this.categorias.set(res.data);
    } catch {
      // El filtro de categoría es opcional; el listado reporta su propio error.
    }
  }

  async cargar(page = this.page(), idHotel = this.sedeSeleccionada()): Promise<void> {
    if (!idHotel) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await this.catalogo.listarHabitaciones(page, this.size(), idHotel, this.filtroCategoria);
      this.habitaciones.set(res.data);
      this.page.set(res.page);
      this.size.set(res.size);
      this.total.set(res.totalElements);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.habitaciones.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  cambiarSede(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.sedesContext.seleccionar(value ? Number(value) : null);
  }

  aplicarFiltro(): void {
    this.cargar(0);
  }

  limpiarFiltro(): void {
    this.filtroCategoria = null;
    this.cargar(0);
  }

  nuevo(): void {
    this.router.navigateByUrl('/app/habitaciones/nuevo');
  }

  editar(habitacion: Habitacion): void {
    this.router.navigateByUrl(`/app/habitaciones/${habitacion.idHabitacion}/editar`);
  }

  /** Ruta del detalle con la galería completa (paso 8). */
  verDetalle(habitacion: Habitacion): void {
    this.router.navigateByUrl(`/app/habitaciones/${habitacion.idHabitacion}`);
  }

  /** Galería ordenada por `orden`, como la guarda el contrato. */
  galeria(h: Habitacion): ImagenHabitacion[] {
    return [...(h.urlsImagenes ?? [])].sort((a, b) => Number(a.orden) - Number(b.orden));
  }

  /** Miniatura del listado: la primera imagen de la galería. */
  primeraImagen(h: Habitacion): ImagenHabitacion | null {
    return this.galeria(h)[0] ?? null;
  }

  claseFisica(h: Habitacion): string {
    switch (h.estadoFisico?.codigo) {
      case 'LISTA':
        return 'pill-ok';
      case 'EN_LIMPIEZA':
        return 'pill-proceso';
      case 'MANTENIMIENTO':
        return 'pill-atencion';
      default:
        return 'pill-archivado';
    }
  }

  claseOcupacion(h: Habitacion): string {
    switch (h.ocupacion?.estadoEstadia) {
      case 'OCUPADA':
        return 'pill-atencion';
      case 'DESCONOCIDA':
        return 'pill-archivado';
      case 'LIBRE':
      default:
        return 'pill-archivado';
    }
  }

  textoOcupacion(h: Habitacion): string {
    switch (h.ocupacion?.estadoEstadia) {
      case 'OCUPADA':
        return 'Ocupada';
      case 'LIBRE':
        return 'Libre';
      case 'DESCONOCIDA':
      default:
        return 'Sin datos';
    }
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    if (err?.reloadable) {
      this.cargar();
      return;
    }
    this.cargar();
  }

  async abrirEtiquetas(): Promise<void> {
    this.etiquetaError.set(null);
    this.etiquetaEditando.set(null);
    try {
      this.estados.set(await this.catalogo.listarEstadosFisicos());
      this.etiquetasOpen.set(true);
    } catch (err) {
      this.error.set(parseApiError(err));
    }
  }

  cerrarEtiquetas(): void {
    if (this.etiquetaGuardando()) return;
    this.etiquetasOpen.set(false);
    this.etiquetaEditando.set(null);
  }

  editarEtiqueta(estado: EstadoFisico): void {
    this.etiquetaEditando.set(estado);
    this.etiquetaDescripcion.set(estado.descripcion);
  }

  cancelarEtiqueta(): void {
    this.etiquetaEditando.set(null);
    this.etiquetaDescripcion.set('');
  }

  async guardarEtiqueta(): Promise<void> {
    const estado = this.etiquetaEditando();
    if (!estado) return;
    const descripcion = this.etiquetaDescripcion().trim();
    if (!descripcion || descripcion.length > 80) return;
    this.etiquetaGuardando.set(true);
    this.etiquetaError.set(null);
    try {
      // Buscar el estado da el ETag vigente para el If-Match del PUT.
      await this.catalogo.buscarEstadoFisico(estado.idEstadoHabitacion);
      const res = await this.catalogo.actualizarEstadoFisico(estado.idEstadoHabitacion, descripcion);
      this.estados.update((lista) =>
        lista.map((e) => (e.idEstadoHabitacion === estado.idEstadoHabitacion ? res.data : e))
      );
      this.etiquetaEditando.set(null);
      this.toast.show('Cambios guardados');
    } catch (err) {
      this.etiquetaError.set(parseApiError(err));
    } finally {
      this.etiquetaGuardando.set(false);
    }
  }

  // ------------------------------------------------ operaciones (pasos 11-13)

  puedeIniciarMantenimiento(h: Habitacion): boolean {
    return (
      this.esAdmin() &&
      !this.pendiente() &&
      h.estado &&
      !h.blockId &&
      h.estadoFisico?.codigo !== 'MANTENIMIENTO'
    );
  }

  puedeDarDeBaja(h: Habitacion): boolean {
    return this.esAdmin() && !this.pendiente() && h.estado && !h.blockId;
  }

  puedeCompletarMantenimiento(h: Habitacion): boolean {
    return (
      this.esAdmin() &&
      !this.pendiente() &&
      !!h.blockId &&
      h.estadoFisico?.codigo === 'MANTENIMIENTO'
    );
  }

  puedeReactivar(h: Habitacion): boolean {
    return this.esAdmin() && !this.pendiente() && !!h.blockId && !h.estado;
  }

  abrirMantenimiento(h: Habitacion): void {
    if (!this.puedeIniciarMantenimiento(h)) return;
    this.operacionError.set(null);
    this.habitacionOperando.set(h);
    this.motivo.set('');
    this.motivoError.set(null);
    this.dialogo.set('MANTENIMIENTO');
  }

  abrirBaja(h: Habitacion): void {
    if (!this.puedeDarDeBaja(h)) return;
    this.operacionError.set(null);
    this.habitacionOperando.set(h);
    this.motivo.set('');
    this.motivoError.set(null);
    this.dialogo.set('BAJA');
  }

  cerrarDialogo(): void {
    if (this.confirmando()) return;
    this.dialogo.set(null);
    this.habitacionOperando.set(null);
    this.motivoError.set(null);
  }

  async confirmarMantenimiento(): Promise<void> {
    const h = this.habitacionOperando();
    if (!h || this.confirmando()) return;
    const motivo = this.motivo().trim();
    if (!motivo || motivo.length > 300) {
      this.motivoError.set('Indica el motivo');
      return;
    }
    this.confirmando.set(true);
    this.operacionError.set(null);
    try {
      const res = await this.recepcion.iniciarMantenimiento(h.idHabitacion, motivo, h.version);
      this.procesarResultado(res, 'MANTENIMIENTO', 'Mantenimiento iniciado');
    } catch (err) {
      this.manejarErrorOperacion(err);
    } finally {
      this.confirmando.set(false);
    }
  }

  async confirmarBaja(): Promise<void> {
    const h = this.habitacionOperando();
    if (!h || this.confirmando()) return;
    this.confirmando.set(true);
    this.operacionError.set(null);
    try {
      const res = await this.recepcion.darDeBaja(h.idHabitacion, h.version);
      this.procesarResultado(res, 'BAJA', 'Habitación dada de baja');
    } catch (err) {
      this.manejarErrorOperacion(err);
    } finally {
      this.confirmando.set(false);
    }
  }

  async completarMantenimiento(h: Habitacion): Promise<void> {
    if (!h.blockId || this.guardandoOperacion() !== null) return;
    this.guardandoOperacion.set(h.idHabitacion);
    this.operacionError.set(null);
    try {
      const res = await this.recepcion.completarMantenimiento(
        h.idHabitacion,
        h.blockId,
        h.version
      );
      this.reemplazarFila(res.data);
      this.toast.show('Mantenimiento completado');
    } catch (err) {
      this.manejarErrorOperacion(err);
    } finally {
      this.guardandoOperacion.set(null);
    }
  }

  async reactivar(h: Habitacion): Promise<void> {
    if (!h.blockId || this.guardandoOperacion() !== null) return;
    this.guardandoOperacion.set(h.idHabitacion);
    this.operacionError.set(null);
    try {
      const res = await this.recepcion.reactivar(h.idHabitacion, h.blockId, h.version);
      this.reemplazarFila(res.data);
      this.toast.show('Habitación reactivada');
    } catch (err) {
      this.manejarErrorOperacion(err);
    } finally {
      this.guardandoOperacion.set(null);
    }
  }

  operationActionLabel(): string {
    return this.operacionError()?.relogin ? 'Iniciar sesión de nuevo' : 'Recargar';
  }

  accionOperacionError(): void {
    const err = this.operacionError();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    void this.cargar();
  }

  ngOnDestroy(): void {
    this.pollAbort?.abort();
  }

  private procesarResultado(
    res: ResultadoOperacionHabitacion,
    tipo: TipoOperacionHabitacion,
    ok: string
  ): void {
    this.dialogo.set(null);
    this.habitacionOperando.set(null);
    if (res.kind === 'confirmada') {
      this.reemplazarFila(res.habitacion);
      this.toast.show(ok);
      return;
    }
    void this.iniciarPolling(res.location, res.retryAfterMs, tipo);
  }

  private async iniciarPolling(
    location: string,
    retryAfterMs: number,
    tipo: TipoOperacionHabitacion
  ): Promise<void> {
    this.pollAbort?.abort();
    const controller = new AbortController();
    this.pollAbort = controller;
    this.pendiente.set({
      tipo,
      detalle:
        tipo === 'MANTENIMIENTO'
          ? 'El mantenimiento se está aplicando en la habitación.'
          : 'La baja de la habitación se está aplicando.',
      actualizadoEn: null,
    });
    try {
      const fin = await this.recepcion.seguirOperacion(
        location,
        retryAfterMs,
        (avance) =>
          this.pendiente.update((p) =>
            p ? { ...p, actualizadoEn: avance.actualizadoEn } : p
          ),
        controller.signal
      );
      if (!fin || controller.signal.aborted) return;
      this.pendiente.set(null);
      if (fin.operacion.estado === 'RECHAZADA') {
        this.operacionError.set(this.errorDeCodigo('GATE_NOT_AVAILABLE'));
      } else {
        this.toast.show(
          tipo === 'MANTENIMIENTO' ? 'Mantenimiento aplicado' : 'Baja aplicada'
        );
      }
      await this.cargar();
    } catch (err) {
      if (controller.signal.aborted) return;
      this.pendiente.set(null);
      this.operacionError.set(parseApiError(err));
      await this.cargar();
    }
  }

  private manejarErrorOperacion(err: unknown): void {
    const apiError = parseApiError(err);
    this.operacionError.set(apiError);
    this.dialogo.set(null);
    this.habitacionOperando.set(null);
    if (apiError.code === 'PRECONDITION_FAILED' || apiError.code === 'PRECONDITION_REQUIRED') {
      void this.cargar();
    }
  }

  private reemplazarFila(actualizada: Habitacion): void {
    this.habitaciones.update((lista) =>
      lista.map((h) => (h.idHabitacion === actualizada.idHabitacion ? actualizada : h))
    );
  }

  private errorDeCodigo(code: string): ApiUiError {
    return parseApiError({
      error: {
        message: '',
        error: { code, fields: {} },
        meta: { correlationId: '' },
      },
    });
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Escape') return;
    if (this.etiquetasOpen()) {
      event.preventDefault();
      this.cerrarEtiquetas();
      return;
    }
    if (this.dialogo()) {
      event.preventDefault();
      this.cerrarDialogo();
    }
  }
}
