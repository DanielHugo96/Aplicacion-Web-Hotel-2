import { CurrencyPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { UiError, validationError } from '../core/api-error';
import { CatalogService } from '../core/catalog.service';
import {
  CategoriaPublica,
  HotelPublico,
  MetaPage,
} from '../core/catalog.models';
import { DemoImageComponent } from '../shared/demo-image.component';
import { ErrorStateComponent } from '../shared/error-state.component';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-sede-detalle-page',
  imports: [
    RouterLink,
    DemoImageComponent,
    ErrorStateComponent,
    CurrencyPipe,
  ],
  templateUrl: './sede-detalle.page.html',
  styleUrl: './sede-detalle.page.css',
})
export class SedeDetallePage {
  private readonly route = inject(ActivatedRoute);
  private readonly catalog = inject(CatalogService);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly hotel = signal<HotelPublico | null>(null);
  protected readonly categorias = signal<CategoriaPublica[]>([]);
  protected readonly meta = signal<MetaPage | null>(null);

  protected readonly cargandoHotel = signal(true);
  protected readonly cargandoCategorias = signal(false);
  protected readonly errorHotel = signal<UiError | null>(null);
  protected readonly errorCategorias = signal<UiError | null>(null);
  protected readonly errorCapacidad = signal<UiError | null>(null);

  protected readonly pagina = signal(0);
  protected readonly capacidad = signal<number | undefined>(undefined);
  protected readonly capacidadTexto = signal('');
  protected readonly capacidadAplicada = signal<number | undefined>(undefined);
  protected readonly tamano = PAGE_SIZE;

  private slug = '';

  protected readonly paginas = () => {
    const total = this.meta()?.totalElements ?? 0;
    return Math.max(1, Math.ceil(total / this.tamano));
  };

  protected readonly personas = (capacidad: number) =>
    capacidad === 1 ? 'Hasta 1 persona' : `Hasta ${capacidad} personas`;

  constructor() {
    this.route.paramMap
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const slug = params.get('slug') ?? '';
        if (!slug || slug === this.slug) {
          return;
        }
        this.slug = slug;
        this.hotel.set(null);
        this.categorias.set([]);
        this.meta.set(null);
        this.pagina.set(0);
        this.capacidadAplicada.set(undefined);
        this.capacidadTexto.set('');
        this.cargarHotel();
      });
  }

  protected cargarHotel(): void {
    this.cargandoHotel.set(true);
    this.errorHotel.set(null);
    this.catalog
      .obtenerHotel(this.slug)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.hotel.set(response.data);
          this.cargandoHotel.set(false);
          this.cargarCategorias(0, this.capacidadAplicada());
        },
        error: (error: UiError) => {
          this.errorHotel.set(error);
          this.cargandoHotel.set(false);
        },
      });
  }

  protected cargarCategorias(
    page: number,
    capacidad = this.capacidadAplicada(),
  ): void {
    this.cargandoCategorias.set(true);
    this.errorCategorias.set(null);
    this.catalog
      .listarCategorias(this.slug, page, this.tamano, capacidad)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.categorias.set(response.data);
          this.meta.set(response.meta);
          this.pagina.set(response.meta.page);
          this.cargandoCategorias.set(false);
        },
        error: (error: UiError) => {
          this.categorias.set([]);
          this.meta.set(null);
          this.errorCategorias.set(error);
          this.cargandoCategorias.set(false);
        },
      });
  }

  protected aplicarFiltro(event: Event): void {
    event.preventDefault();
    const texto = this.capacidadTexto().trim();
    this.errorCapacidad.set(null);

    if (texto === '') {
      this.capacidadAplicada.set(undefined);
      this.cargarCategorias(0, undefined);
      return;
    }

    const valor = Number(texto);
    if (!Number.isInteger(valor) || valor < 1 || valor > 10) {
      this.errorCapacidad.set(
        validationError('capacidad', 'La capacidad debe estar entre 1 y 10.'),
      );
      return;
    }

    this.capacidadAplicada.set(valor);
    this.cargarCategorias(0, valor);
  }

  protected limpiarFiltro(): void {
    this.capacidadTexto.set('');
    this.capacidadAplicada.set(undefined);
    this.errorCapacidad.set(null);
    this.cargarCategorias(0, undefined);
  }

  protected anterior(): void {
    if (this.pagina() > 0) {
      this.cargarCategorias(this.pagina() - 1);
    }
  }

  protected siguiente(): void {
    if (this.pagina() + 1 < this.paginas()) {
      this.cargarCategorias(this.pagina() + 1);
    }
  }

  protected imagenPrincipal(categoria: CategoriaPublica): string {
    return categoria.imagenes[0]?.url ?? '/assets/demo/placeholder.svg';
  }

  protected altPrincipal(categoria: CategoriaPublica): string {
    return categoria.imagenes[0]?.alt ?? 'Habitación de demostración';
  }
}
