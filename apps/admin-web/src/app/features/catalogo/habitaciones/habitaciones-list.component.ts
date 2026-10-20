import { Component, computed, effect, HostListener, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { Categoria, EstadoFisico, Habitacion } from '../../../core/models/catalogo.models';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PaginationComponent } from '../../../ui/pagination/pagination.component';
import { MoneyPipe } from '../../../core/pipes/money.pipe';

@Component({
  selector: 'app-habitaciones-list',
  standalone: true,
  imports: [FormsModule, ErrorBannerComponent, PaginationComponent, MoneyPipe],
  templateUrl: './habitaciones-list.component.html',
  styleUrl: './habitaciones-list.component.css',
})
export class HabitacionesListComponent {
  private readonly catalogo = inject(CatalogoService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  readonly sedesContext = inject(SedeContextService);

  readonly esAdmin = computed(() => this.session.rol() === 'ADMIN');
  readonly sedes = computed(() => this.sedesContext.sedes());
  readonly sedeSeleccionada = computed(() => this.sedesContext.selectedId());
  readonly habitaciones = signal<Habitacion[]>([]);
  readonly categorias = signal<Categoria[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly total = signal(0);
  filtroCategoria: number | null = null;

  // Diálogo de etiquetas de estado físico (solo ADMIN).
  readonly etiquetasOpen = signal(false);
  readonly estados = signal<EstadoFisico[]>([]);
  readonly etiquetaEditando = signal<EstadoFisico | null>(null);
  readonly etiquetaDescripcion = signal('');
  readonly etiquetaGuardando = signal(false);
  readonly etiquetaError = signal<ApiUiError | null>(null);

  constructor() {
    effect(() => {
      const idHotel = this.sedeSeleccionada();
      if (!idHotel) {
        this.habitaciones.set([]);
        this.loading.set(false);
        return;
      }
      void this.cargar(0, idHotel);
    });
    void this.cargarCategorias();
  }

  async cargarCategorias(): Promise<void> {
    try {
      const res = await this.catalogo.listarCategorias(0, 100);
      this.categorias.set(res.data);
    } catch {
      // El filtro de categoría es opcional; el listado reporta su propio error.
    }
  }

  async cargar(page = this.page(), idHotel = this.sedeSeleccionada()): Promise<void> {
    if (!idHotel) return;
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await this.catalogo.listarHabitaciones(page, this.size(), idHotel, this.filtroCategoria);
      this.habitaciones.set(res.data);
      this.page.set(res.page);
      this.size.set(res.size);
      this.total.set(res.totalElements);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.habitaciones.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  cambiarSede(event: Event): void {
    const value = (event.target as HTMLSelectElement).value;
    this.sedesContext.seleccionar(value ? Number(value) : null);
  }

  aplicarFiltro(): void {
    this.cargar(0);
  }

  limpiarFiltro(): void {
    this.filtroCategoria = null;
    this.cargar(0);
  }

  nuevo(): void {
    this.router.navigateByUrl('/app/habitaciones/nuevo');
  }

  editar(habitacion: Habitacion): void {
    this.router.navigateByUrl(`/app/habitaciones/${habitacion.idHabitacion}/editar`);
  }

  claseFisica(h: Habitacion): string {
    switch (h.estadoFisico?.codigo) {
      case 'LISTA':
        return 'pill-ok';
      case 'EN_LIMPIEZA':
        return 'pill-proceso';
      case 'MANTENIMIENTO':
        return 'pill-atencion';
      default:
        return 'pill-archivado';
    }
  }

  claseOcupacion(h: Habitacion): string {
    switch (h.ocupacion?.estadoEstadia) {
      case 'OCUPADA':
        return 'pill-atencion';
      case 'DESCONOCIDA':
        return 'pill-archivado';
      case 'LIBRE':
      default:
        return 'pill-archivado';
    }
  }

  textoOcupacion(h: Habitacion): string {
    switch (h.ocupacion?.estadoEstadia) {
      case 'OCUPADA':
        return 'Ocupada';
      case 'LIBRE':
        return 'Libre';
      case 'DESCONOCIDA':
      default:
        return 'Sin datos';
    }
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    if (err?.reloadable) {
      this.cargar();
      return;
    }
    this.cargar();
  }

  async abrirEtiquetas(): Promise<void> {
    this.etiquetaError.set(null);
    this.etiquetaEditando.set(null);
    try {
      this.estados.set(await this.catalogo.listarEstadosFisicos());
      this.etiquetasOpen.set(true);
    } catch (err) {
      this.error.set(parseApiError(err));
    }
  }

  cerrarEtiquetas(): void {
    if (this.etiquetaGuardando()) return;
    this.etiquetasOpen.set(false);
    this.etiquetaEditando.set(null);
  }

  editarEtiqueta(estado: EstadoFisico): void {
    this.etiquetaEditando.set(estado);
    this.etiquetaDescripcion.set(estado.descripcion);
  }

  cancelarEtiqueta(): void {
    this.etiquetaEditando.set(null);
    this.etiquetaDescripcion.set('');
  }

  async guardarEtiqueta(): Promise<void> {
    const estado = this.etiquetaEditando();
    if (!estado) return;
    const descripcion = this.etiquetaDescripcion().trim();
    if (!descripcion || descripcion.length > 80) return;
    this.etiquetaGuardando.set(true);
    this.etiquetaError.set(null);
    try {
      // Buscar el estado da el ETag vigente para el If-Match del PUT.
      await this.catalogo.buscarEstadoFisico(estado.idEstadoHabitacion);
      const res = await this.catalogo.actualizarEstadoFisico(estado.idEstadoHabitacion, descripcion);
      this.estados.update((lista) =>
        lista.map((e) => (e.idEstadoHabitacion === estado.idEstadoHabitacion ? res.data : e))
      );
      this.etiquetaEditando.set(null);
      this.toast.show('Cambios guardados');
    } catch (err) {
      this.etiquetaError.set(parseApiError(err));
    } finally {
      this.etiquetaGuardando.set(false);
    }
  }

  @HostListener('document:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (this.etiquetasOpen() && event.key === 'Escape') {
      event.preventDefault();
      this.cerrarEtiquetas();
    }
  }
}
