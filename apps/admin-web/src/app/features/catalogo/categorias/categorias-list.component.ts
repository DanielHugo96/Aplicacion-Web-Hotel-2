import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { Categoria } from '../../../core/models/catalogo.models';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PaginationComponent } from '../../../ui/pagination/pagination.component';
import { ConfirmDialogComponent } from '../../../ui/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-categorias-list',
  standalone: true,
  imports: [ErrorBannerComponent, PaginationComponent, ConfirmDialogComponent],
  templateUrl: './categorias-list.component.html',
  styleUrl: './categorias-list.component.css',
})
export class CategoriasListComponent implements OnInit {
  private readonly catalogo = inject(CatalogoService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly esAdmin = computed(() => this.session.rol() === 'ADMIN');
  readonly categorias = signal<Categoria[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly total = signal(0);

  readonly baja = signal<Categoria | null>(null);
  readonly bajaBusy = signal(false);
  readonly bajaError = signal<ApiUiError | null>(null);

  ngOnInit(): void {
    void this.cargar();
  }

  async cargar(page = this.page()): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await this.catalogo.listarCategorias(page, this.size());
      this.categorias.set(res.data);
      this.page.set(res.page);
      this.size.set(res.size);
      this.total.set(res.totalElements);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.categorias.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  nueva(): void {
    this.router.navigateByUrl('/app/categorias/nuevo');
  }

  editar(categoria: Categoria): void {
    this.router.navigateByUrl(`/app/categorias/${categoria.idCategoria}/editar`);
  }

  async prepararBaja(categoria: Categoria): Promise<void> {
    this.bajaError.set(null);
    try {
      const res = await this.catalogo.buscarCategoria(categoria.idCategoria);
      this.baja.set(res.data);
    } catch (err) {
      this.error.set(parseApiError(err));
    }
  }

  async confirmarBaja(): Promise<void> {
    const categoria = this.baja();
    if (!categoria) return;
    this.bajaBusy.set(true);
    this.bajaError.set(null);
    try {
      await this.catalogo.eliminarCategoria(categoria.idCategoria);
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
