import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { Producto } from '../../../core/models/cliente.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { ClienteContextService } from '../../../core/services/cliente-context.service';
import { ClienteService } from '../../../core/services/cliente.service';
import { ToastService } from '../../../core/services/toast.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PaginationComponent } from '../../../ui/pagination/pagination.component';

/**
 * `/mi-estadia/catalogo`: catálogo privado de la sede de la estadía ACTIVA.
 * Precio y stock son informativos y autoritativos del backend; el front no los
 * recalcula ni usa datos del panel como respaldo.
 */
@Component({
  selector: 'app-catalogo-cliente',
  standalone: true,
  imports: [RouterLink, ErrorBannerComponent, MoneyPipe, PaginationComponent],
  templateUrl: './catalogo-cliente.component.html',
  styleUrl: './catalogo-cliente.component.css',
})
export class CatalogoClienteComponent implements OnInit {
  private readonly contexto = inject(ClienteContextService);
  private readonly cliente = inject(ClienteService);
  private readonly toast = inject(ToastService);

  readonly cargando = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly productos = signal<Producto[]>([]);
  readonly pagina = signal(0);
  readonly total = signal(0);
  readonly agregando = signal<number | null>(null);
  readonly size = 12;

  async ngOnInit(): Promise<void> {
    await this.cargar(0);
  }

  get sinEstadia(): boolean {
    return this.contexto.activa() === null;
  }

  async cargar(page = 0): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    await this.contexto.cargar();
    const errorContexto = this.contexto.error();
    if (errorContexto) {
      this.error.set(errorContexto);
      this.cargando.set(false);
      return;
    }
    const activa = this.contexto.activa();
    if (!activa) {
      this.productos.set([]);
      this.total.set(0);
      this.cargando.set(false);
      return;
    }
    try {
      const res = await this.cliente.listarCatalogo(activa.idRecepcion, page, this.size);
      this.productos.set(res.data);
      this.pagina.set(res.page);
      this.total.set(res.totalElements);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.productos.set([]);
    } finally {
      this.cargando.set(false);
    }
  }

  cambiarPagina(page: number): void {
    void this.cargar(page);
  }

  async agregar(producto: Producto): Promise<void> {
    const activa = this.contexto.activa();
    if (!activa || this.agregando() !== null) return;
    this.agregando.set(producto.idProducto);
    this.error.set(null);
    try {
      await this.cliente.agregarAlCarrito(activa.idRecepcion, producto.idProducto, 1);
      this.toast.show('Agregado al carrito');
    } catch (err) {
      this.error.set(parseApiError(err));
    } finally {
      this.agregando.set(null);
    }
  }
}
