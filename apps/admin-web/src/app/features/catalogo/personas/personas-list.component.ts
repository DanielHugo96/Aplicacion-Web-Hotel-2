import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { Persona, TipoPersona } from '../../../core/models/catalogo.models';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PaginationComponent } from '../../../ui/pagination/pagination.component';
import { ConfirmDialogComponent } from '../../../ui/confirm-dialog/confirm-dialog.component';

@Component({
  selector: 'app-personas-list',
  standalone: true,
  imports: [
    FormsModule,
    ErrorBannerComponent,
    PaginationComponent,
    ConfirmDialogComponent,
  ],
  templateUrl: './personas-list.component.html',
  styleUrl: './personas-list.component.css',
})
export class PersonasListComponent implements OnInit {
  private readonly catalogo = inject(CatalogoService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  readonly sedesContext = inject(SedeContextService);

  readonly rol = this.session.rol;
  readonly esAdmin = computed(() => this.rol() === 'ADMIN');
  readonly esEmpleado = computed(() => this.rol() === 'EMPLEADO');

  readonly personas = signal<Persona[]>([]);
  readonly tipos = signal<TipoPersona[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly page = signal(0);
  readonly size = signal(20);
  readonly total = signal(0);

  filtroTipo: number | null = null;
  filtroDocumento = '';

  readonly baja = signal<Persona | null>(null);
  readonly bajaBusy = signal(false);
  readonly bajaError = signal<ApiUiError | null>(null);

  ngOnInit(): void {
    void this.cargarTipos();
    void this.cargar();
  }

  async cargarTipos(): Promise<void> {
    try {
      const tipos = await this.catalogo.listarTiposPersona();
      this.tipos.set(tipos);
      if (this.esEmpleado()) {
        this.filtroTipo = tipos.find((t) => t.codigo === 'CLIENTE')?.idTipoPersona ?? null;
      }
    } catch {
      // El listado de personas sigue siendo la fuente principal de error.
    }
  }

  async cargar(page = this.page()): Promise<void> {
    if (this.esEmpleado() && !this.filtroDocumento.trim()) {
      this.personas.set([]);
      this.total.set(0);
      this.loading.set(false);
      this.error.set(null);
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await this.catalogo.listarPersonas(page, this.size(), {
        idTipoPersona: this.filtroTipo,
        documento: this.filtroDocumento,
      });
      this.personas.set(res.data);
      this.page.set(res.page);
      this.size.set(res.size);
      this.total.set(res.totalElements);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.personas.set([]);
      this.total.set(0);
    } finally {
      this.loading.set(false);
    }
  }

  buscar(): void {
    this.cargar(0);
  }

  limpiar(): void {
    this.filtroTipo = this.esEmpleado()
      ? this.tipos().find((t) => t.codigo === 'CLIENTE')?.idTipoPersona ?? null
      : null;
    this.filtroDocumento = '';
    this.cargar(0);
  }

  cambiarPagina(page: number): void {
    this.cargar(page);
  }

  nuevo(): void {
    this.router.navigateByUrl('/app/personas/nuevo');
  }

  editar(persona: Persona): void {
    this.router.navigateByUrl(`/app/personas/${persona.idPersona}/editar`);
  }

  async prepararBaja(persona: Persona): Promise<void> {
    this.bajaError.set(null);
    try {
      const res = await this.catalogo.buscarPersona(persona.idPersona);
      this.baja.set(res.data);
    } catch (err) {
      this.error.set(parseApiError(err));
    }
  }

  async confirmarBaja(): Promise<void> {
    const persona = this.baja();
    if (!persona) return;
    this.bajaBusy.set(true);
    this.bajaError.set(null);
    try {
      await this.catalogo.eliminarPersona(persona.idPersona);
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

  recargar(): void {
    this.cargar();
  }

  sedesTexto(persona: Persona): string {
    if (persona.sedes.length === 0) return 'Global';
    const nombres = persona.sedes
      .map((id) => this.sedesContext.sedes().find((s) => s.idHotel === id)?.nombre ?? `Sede ${id}`)
      .filter(Boolean);
    return nombres.join(', ');
  }

  estadoTexto(persona: Persona): string {
    return persona.estado ? 'Activo' : 'Inactivo';
  }

  estadoClase(persona: Persona): string {
    return persona.estado ? 'pill-ok' : 'pill-archivado';
  }
}
