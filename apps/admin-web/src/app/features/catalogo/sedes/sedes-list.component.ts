import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { Hotel } from '../../../core/models/catalogo.models';
import { ImagenPanelComponent } from '../../../shared/imagen-panel.component';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PaginationComponent } from '../../../ui/pagination/pagination.component';
import { ConfirmDialogComponent } from '../../../ui/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-sedes-list',
  standalone: true,
  imports: [ErrorBannerComponent, PaginationComponent, ConfirmDialogComponent, ImagenPanelComponent],
  templateUrl: './sedes-list.component.html',
  styleUrl: './sedes-list.component.css',
})
export class SedesListComponent implements OnInit {
  private readonly catalogo = inject(CatalogoService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly esAdmin = computed(() => this.session.rol() === 'ADMIN');
  readonly sedes = signal<Hotel[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly total = signal(0);

  readonly baja = signal<Hotel | null>(null);
  readonly bajaBusy = signal(false);
  readonly bajaError = signal<ApiUiError | null>(null);

  ngOnInit(): void {
    void this.cargar();
  }

  async cargar(page = this.page()): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await this.catalogo.listarSedes(page, this.size());
      this.sedes.set(res.data);
      this.page.set(res.page);
      this.size.set(res.size);
      this.total.set(res.totalElements);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.sedes.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  nueva(): void {
    this.router.navigateByUrl('/app/sedes/nuevo');
  }

  editar(sede: Hotel): void {
    this.router.navigateByUrl(`/app/sedes/${sede.idHotel}/editar`);
  }

  async prepararBaja(sede: Hotel): Promise<void> {
    this.bajaError.set(null);
    try {
      const res = await this.catalogo.buscarSede(sede.idHotel);
      this.baja.set(res.data);
    } catch (err) {
      this.error.set(parseApiError(err));
    }
  }

  async confirmarBaja(): Promise<void> {
    const sede = this.baja();
    if (!sede) return;
    this.bajaBusy.set(true);
    this.bajaError.set(null);
    try {
      await this.catalogo.eliminarSede(sede.idHotel);
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
