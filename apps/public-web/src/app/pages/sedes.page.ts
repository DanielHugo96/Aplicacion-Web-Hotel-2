import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { UiError } from '../core/api-error';
import { CatalogService } from '../core/catalog.service';
import { HotelPublico, MetaPage } from '../core/catalog.models';
import {
  CatalogImageComponent,
  IMAGEN_FALLBACK,
} from '../shared/catalog-image.component';
import { ErrorStateComponent } from '../shared/error-state.component';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-sedes-page',
  imports: [RouterLink, CatalogImageComponent, ErrorStateComponent],
  templateUrl: './sedes.page.html',
  styleUrl: './sedes.page.css',
})
export class SedesPage {
  private readonly catalog = inject(CatalogService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly hoteles = signal<HotelPublico[]>([]);
  protected readonly meta = signal<MetaPage | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<UiError | null>(null);
  protected readonly pagina = signal(0);
  protected readonly tamano = PAGE_SIZE;
  protected readonly IMAGEN_FALLBACK = IMAGEN_FALLBACK;

  protected readonly paginas = () => {
    const total = this.meta()?.totalElements ?? 0;
    return Math.max(1, Math.ceil(total / this.tamano));
  };

  constructor() {
    this.cargar(0);
  }

  protected cargar(page: number): void {
    this.cargando.set(true);
    this.error.set(null);
    this.catalog
      .listarHoteles(page, this.tamano)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.hoteles.set(response.data);
          this.meta.set(response.meta);
          this.pagina.set(response.meta.page);
          this.cargando.set(false);
        },
        error: (error: UiError) => {
          this.hoteles.set([]);
          this.meta.set(null);
          this.error.set(error);
          this.cargando.set(false);
        },
      });
  }

  protected anterior(): void {
    if (this.pagina() > 0) {
      this.cargar(this.pagina() - 1);
    }
  }

  protected siguiente(): void {
    if (this.pagina() + 1 < this.paginas()) {
      this.cargar(this.pagina() + 1);
    }
  }
}
