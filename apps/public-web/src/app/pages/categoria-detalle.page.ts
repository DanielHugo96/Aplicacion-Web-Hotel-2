import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { UiError, validationError } from '../core/api-error';
import { CatalogService } from '../core/catalog.service';
import { CategoriaPublica, ImagenPublica } from '../core/catalog.models';
import {
  CatalogImageComponent,
  IMAGEN_FALLBACK,
} from '../shared/catalog-image.component';
import { ErrorStateComponent } from '../shared/error-state.component';
import { MonedaPePipe } from '../shared/moneda-pe.pipe';

/** La galería de la categoría muestra como máximo cuatro imágenes. */
const MAX_GALERIA = 4;

/** Fallback cuando el contrato no entrega ninguna imagen utilizable. */
const IMAGEN_VACIA: ImagenPublica = {
  url: IMAGEN_FALLBACK,
  alt: 'Imagen no disponible del tipo de habitación',
};

@Component({
  selector: 'app-categoria-detalle-page',
  imports: [RouterLink, CatalogImageComponent, ErrorStateComponent, MonedaPePipe],
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
  protected readonly indicePrincipal = signal(0);
  protected readonly MAX_GALERIA = MAX_GALERIA;

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
        this.indicePrincipal.set(0);

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
          this.indicePrincipal.set(0);
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

  /** Hasta cuatro imágenes con `alt` propio; si no hay ninguna, el fallback. */
  protected galeria(categoria: CategoriaPublica): ImagenPublica[] {
    const propias = categoria.imagenes
      .filter((imagen) => imagen.url)
      .slice(0, MAX_GALERIA);
    return propias.length > 0 ? propias : [IMAGEN_VACIA];
  }

  protected principal(categoria: CategoriaPublica): ImagenPublica {
    const imagenes = this.galeria(categoria);
    return imagenes[this.indicePrincipal()] ?? imagenes[0];
  }

  protected hayMiniaturas(categoria: CategoriaPublica): boolean {
    return this.galeria(categoria).length > 1;
  }

  protected seleccionar(indice: number): void {
    this.indicePrincipal.set(indice);
  }

  protected esFallback(categoria: CategoriaPublica): boolean {
    return categoria.imagenes.filter((imagen) => imagen.url).length === 0;
  }
}
