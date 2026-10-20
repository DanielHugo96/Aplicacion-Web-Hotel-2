import { Component, computed, effect, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { Piso } from '../../../core/models/catalogo.models';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PaginationComponent } from '../../../ui/pagination/pagination.component';
import { ConfirmDialogComponent } from '../../../ui/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-pisos-list',
  standalone: true,
  imports: [ErrorBannerComponent, PaginationComponent, ConfirmDialogComponent],
  templateUrl: './pisos-list.component.html',
  styleUrl: './pisos-list.component.css',
})
export class PisosListComponent {
  private readonly catalogo = inject(CatalogoService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  readonly sedesContext = inject(SedeContextService);

  readonly esAdmin = computed(() => this.session.rol() === 'ADMIN');
  readonly sedes = computed(() => this.sedesContext.sedes());
  readonly sedeSeleccionada = computed(() => this.sedesContext.selectedId());
  readonly pisos = signal<Piso[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly total = signal(0);

  readonly baja = signal<Piso | null>(null);
  readonly bajaBusy = signal(false);
  readonly bajaError = signal<ApiUiError | null>(null);

  constructor() {
    effect(() => {
      const idHotel = this.sedeSeleccionada();
      if (!idHotel) {
        this.pisos.set([]);
        this.loading.set(false);
        return;
      }
      void this.cargar(0, idHotel);
    });
  }

  async cargar(page = this.page(), idHotel = this.sedeSeleccionada()): Promise<void> {
    if (!idHotel) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await this.catalogo.listarPisos(page, this.size(), idHotel);
      this.pisos.set(res.data);
      this.page.set(res.page);
      this.size.set(res.size);
      this.total.set(res.totalElements);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.pisos.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  cambiarSede(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.sedesContext.seleccionar(value ? Number(value) : null);
  }

  nuevo(): void {
    this.router.navigateByUrl('/app/pisos/nuevo');
  }

  editar(piso: Piso): void {
    this.router.navigateByUrl(`/app/pisos/${piso.idPiso}/editar`);
  }

  sedeNombre(idHotel: number): string {
    return this.sedes().find((s) => s.idHotel === idHotel)?.nombre ?? `Sede ${idHotel}`;
  }

  async prepararBaja(piso: Piso): Promise<void> {
    this.bajaError.set(null);
    try {
      const res = await this.catalogo.buscarPiso(piso.idPiso);
      this.baja.set(res.data);
    } catch (err) {
      this.error.set(parseApiError(err));
    }
  }

  async confirmarBaja(): Promise<void> {
    const piso = this.baja();
    if (!piso) return;
    this.bajaBusy.set(true);
    this.bajaError.set(null);
    try {
      await this.catalogo.eliminarPiso(piso.idPiso);
      this.baja.set(null);
      this.toast.show('Cambios guardados');
      await this.cargar();
    } catch (err) {
      this.bajaError.set(parseApiError(err));
      this.baja.set(null);
    } finally {
      this.bajaBusy.set(false);
    }
  }

  cerrarBaja(): void {
    this.baja.set(null);
    this.bajaError.set(null);
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    this.cargar();
  }
}
