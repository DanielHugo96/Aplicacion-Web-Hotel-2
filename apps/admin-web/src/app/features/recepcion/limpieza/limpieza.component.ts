import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { EstadoLimpieza, Limpieza } from '../../../core/models/recepcion.models';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PaginationComponent } from '../../../ui/pagination/pagination.component';

/**
 * `/app/limpieza`: tablero por sede y estado. «Completar limpieza» usa el
 * `cleaningCycleId` y la `version` de la tarea como `If-Match`; un 412/428
 * recarga el listado sin reabrir la tarea.
 */
@Component({
  selector: 'app-limpieza',
  standalone: true,
  imports: [FormsModule, ErrorBannerComponent, PaginationComponent],
  templateUrl: './limpieza.component.html',
  styleUrl: './limpieza.component.css',
})
export class LimpiezaComponent {
  private readonly recepcion = inject(RecepcionService);
  private readonly session = inject(SessionService);
  private readonly toast = inject(ToastService);
  readonly sedesContext = inject(SedeContextService);

  readonly sedeSeleccionada = computed(() => this.sedesContext.selectedId());
  readonly sedeNombre = computed(() => this.sedesContext.selected()?.nombre ?? null);
  readonly tareas = signal<Limpieza[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly total = signal(0);
  readonly estado = signal<EstadoLimpieza | null>('PENDIENTE');
  readonly completando = signal<string | null>(null);

  constructor() {
    effect(() => {
      const idHotel = this.sedeSeleccionada();
      untracked(() => {
        if (!idHotel) {
          this.tareas.set([]);
          this.total.set(0);
          this.loading.set(false);
          return;
        }
        void this.cargar(0, idHotel);
      });
    });
  }

  async cargar(page = this.page(), idHotel = this.sedeSeleccionada()): Promise<void> {
    if (!idHotel) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await this.recepcion.listarLimpiezas(idHotel, this.estado(), page, this.size());
      this.tareas.set(res.data);
      this.page.set(res.page);
      this.size.set(res.size);
      this.total.set(res.totalElements);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.tareas.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  aplicarFiltro(): void {
    void this.cargar(0);
  }

  etiquetaEstado(estado: EstadoLimpieza): string {
    return estado === 'PENDIENTE' ? 'Pendiente' : 'Completada';
  }

  claseEstado(estado: EstadoLimpieza): string {
    return estado === 'PENDIENTE' ? 'pill-atencion' : 'pill-ok';
  }

  async completar(tarea: Limpieza): Promise<void> {
    if (this.completando()) return;
    this.completando.set(tarea.cleaningCycleId);
    this.error.set(null);
    try {
      const res = await this.recepcion.completarLimpieza(
        tarea.idHabitacion,
        tarea.cleaningCycleId,
        tarea.version
      );
      if (this.estado() === 'PENDIENTE') {
        this.tareas.update((lista) =>
          lista.filter((t) => t.cleaningCycleId !== tarea.cleaningCycleId)
        );
        this.total.update((n) => Math.max(0, n - 1));
      } else {
        this.tareas.update((lista) =>
          lista.map((t) =>
            t.cleaningCycleId === tarea.cleaningCycleId ? res.limpieza : t
          )
        );
      }
      this.toast.show('Limpieza completada');
    } catch (err) {
      const apiError = parseApiError(err);
      this.error.set(apiError);
      if (apiError.code === 'PRECONDITION_FAILED' || apiError.code === 'PRECONDITION_REQUIRED') {
        // La tarea cambió: recargar el listado con la versión nueva y dejar el
        // aviso visible para que se sepa por qué cambió la tarjeta.
        await this.cargar(this.page());
        this.error.set(apiError);
      }
    } finally {
      this.completando.set(null);
    }
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    void this.cargar(this.page());
  }

  actionLabel(): string {
    const err = this.error();
    return err?.reloadable ? 'Recargar' : 'Reintentar';
  }
}
