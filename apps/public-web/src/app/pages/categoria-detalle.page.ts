import { CurrencyPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { UiError, validationError } from '../core/api-error';
import { CatalogService } from '../core/catalog.service';
import { CategoriaPublica } from '../core/catalog.models';
import { DemoImageComponent } from '../shared/demo-image.component';
import { ErrorStateComponent } from '../shared/error-state.component';

@Component({
  selector: 'app-categoria-detalle-page',
  imports: [RouterLink, DemoImageComponent, ErrorStateComponent, CurrencyPipe],
  templateUrl: './categoria-detalle.page.html',
  styleUrl: './categoria-detalle.page.css',
})
export class CategoriaDetallePage {
  private readonly route = inject(ActivatedRoute);
  private readonly catalog = inject(CatalogService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly categoria = signal<CategoriaPublica | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<UiError | null>(null);
  protected readonly slug = signal('');

  protected readonly personas = (capacidad: number) =>
    capacidad === 1 ? 'Hasta 1 persona' : `Hasta ${capacidad} personas`;

  constructor() {
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const slug = params.get('slug') ?? '';
        const idTexto = params.get('idCategoria') ?? '';
        const idCategoria = Number(idTexto);
        this.slug.set(slug);
        this.categoria.set(null);
        this.error.set(null);

        if (!slug) {
          this.error.set(
            validationError('slug', 'La sede indicada no es válida.'),
          );
          this.cargando.set(false);
          return;
        }

        if (!Number.isInteger(idCategoria) || idCategoria < 1) {
          this.error.set(
            validationError(
              'idCategoria',
              'El tipo de habitación no es válido.',
            ),
          );
          this.cargando.set(false);
          return;
        }

        this.cargar(slug, idCategoria);
      });
  }

  protected cargar(slug: string, idCategoria: number): void {
    this.cargando.set(true);
    this.error.set(null);
    this.catalog
      .obtenerCategoria(slug, idCategoria)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.categoria.set(response.data);
          this.cargando.set(false);
        },
        error: (error: UiError) => {
          this.error.set(error);
          this.cargando.set(false);
        },
      });
  }

  protected reintentar(): void {
    const idCategoria = Number(this.route.snapshot.paramMap.get('idCategoria'));
    this.cargar(this.slug(), idCategoria);
  }

  protected imagen(categoria: CategoriaPublica): string[] {
    return categoria.imagenes.length > 0
      ? categoria.imagenes.map((imagen) => imagen.url)
      : ['/assets/demo/placeholder.svg'];
  }

  protected alt(categoria: CategoriaPublica, indice: number): string {
    return (
      categoria.imagenes[indice]?.alt ?? 'Habitación de demostración'
    );
  }
}
