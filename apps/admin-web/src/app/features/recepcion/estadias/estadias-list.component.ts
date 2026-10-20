import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { EstadoEstadia, Recepcion } from '../../../core/models/recepcion.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { PeDatePipe } from '../../../core/pipes/date.pipe';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PaginationComponent } from '../../../ui/pagination/pagination.component';

/**
 * `/app/recepcion`: estadías de la sede del selector del shell. `page`/`size`
 * salen de `meta`; `size` inicia en 20. La sede es filtro de trabajo, no
 * autorización: el backend revalida.
 */
@Component({
  selector: 'app-estadias-list',
  standalone: true,
  imports: [FormsModule, RouterLink, ErrorBannerComponent, PaginationComponent, MoneyPipe, PeDatePipe],
  templateUrl: './estadias-list.component.html',
  styleUrl: './estadias-list.component.css',
})
export class EstadiasListComponent {
  private readonly recepcion = inject(RecepcionService);
  private readonly session = inject(SessionService);
  readonly sedesContext = inject(SedeContextService);

  readonly sedeSeleccionada = computed(() => this.sedesContext.selectedId());
  readonly sedeNombre = computed(() => this.sedesContext.selected()?.nombre ?? null);
  readonly estadias = signal<Recepcion[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly total = signal(0);
  readonly estado = signal<EstadoEstadia | null>(null);

  constructor() {
    effect(() => {
      const idHotel = this.sedeSeleccionada();
      untracked(() => {
        if (!idHotel) {
          this.estadias.set([]);
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
      const res = await this.recepcion.listarEstadias(idHotel, page, this.size(), this.estado());
      this.estadias.set(res.data);
      this.page.set(res.page);
      this.size.set(res.size);
      this.total.set(res.totalElements);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.estadias.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  aplicarFiltro(): void {
    void this.cargar(0);
  }

  limpiarFiltro(): void {
    this.estado.set(null);
    void this.cargar(0);
  }

  etiquetaEstado(estado: EstadoEstadia): string {
    switch (estado) {
      case 'ACTIVA':
        return 'Activa';
      case 'CERRANDO':
        return 'Cierre en curso';
      case 'CERRADA':
        return 'Cerrada';
    }
  }

  claseEstado(estado: EstadoEstadia): string {
    switch (estado) {
      case 'ACTIVA':
        return 'pill-ok';
      case 'CERRANDO':
        return 'pill-atencion';
      case 'CERRADA':
        return 'pill-archivado';
    }
  }

  /** Solo una ACTIVA vencida lleva la etiqueta; el backend calcula `vencida`. */
  esVencida(estadia: Recepcion): boolean {
    return estadia.vencida === true && estadia.estadoEstadia === 'ACTIVA';
  }

  actionLabel(): string {
    const err = this.error();
    if (!err) return 'Reintentar';
    return err.reloadable ? 'Recargar' : 'Reintentar';
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    void this.cargar(this.page());
  }
}
