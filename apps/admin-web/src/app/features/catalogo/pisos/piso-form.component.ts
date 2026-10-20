import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { Piso } from '../../../core/models/catalogo.models';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';

@Component({
  selector: 'app-piso-form',
  standalone: true,
  imports: [FormsModule, RouterLink, ErrorBannerComponent],
  templateUrl: './piso-form.component.html',
  styleUrl: './piso-form.component.css',
})
export class PisoFormComponent implements OnInit {
  private readonly catalogo = inject(CatalogoService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly toast = inject(ToastService);
  readonly sedesContext = inject(SedeContextService);

  readonly esAdmin = computed(() => this.session.rol() === 'ADMIN');

  readonly sedes = computed(() => this.sedesContext.sedes());
  readonly id = signal<number | null>(null);
  readonly esEdicion = computed(() => this.id() !== null);
  readonly cargando = signal(true);
  readonly guardando = signal(false);
  readonly error = signal<ApiUiError | null>(null);
  readonly piso = signal<Piso | null>(null);

  idHotel: number | null = null;
  descripcion = '';
  estado = true;
  errores: Record<string, string> = {};

  readonly actionLabel = computed(() => {
    const err = this.error();
    if (!err) return null;
    if (err.relogin) return 'Iniciar sesión de nuevo';
    if (err.reloadable) return 'Recargar';
    if (err.retryable) return 'Reintentar';
    return null;
  });

  async ngOnInit(): Promise<void> {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (!idParam) {
      this.idHotel = this.sedesContext.selectedId();
      this.cargando.set(false);
      return;
    }
    this.id.set(Number(idParam));
    try {
      const res = await this.catalogo.buscarPiso(this.id()!);
      this.piso.set(res.data);
      this.idHotel = res.data.idHotel;
      this.descripcion = res.data.descripcion;
      this.estado = res.data.estado;
    } catch (err) {
      this.error.set(parseApiError(err));
    } finally {
      this.cargando.set(false);
    }
  }

  sedeNombre(): string {
    if (!this.idHotel) return 'Sin sede';
    return this.sedes().find((s) => s.idHotel === this.idHotel)?.nombre ?? `Sede ${this.idHotel}`;
  }

  campoInvalido(campo: string): boolean {
    return !!this.errores[campo];
  }

  mensajeCampo(campo: string): string {
    return this.errores[campo] ?? '';
  }

  private validar(): boolean {
    const e: Record<string, string> = {};
    if (!this.idHotel) e['idHotel'] = 'Selecciona la sede';
    if (!this.descripcion.trim() || this.descripcion.length > 80) {
      e['descripcion'] = this.descripcion.length > 80 ? 'Máximo 80 caracteres' : 'Campo obligatorio';
    }
    this.errores = e;
    return Object.keys(e).length === 0;
  }

  async onSubmit(event: Event): Promise<void> {
    event.preventDefault();
    this.error.set(null);
    if (!this.validar()) return;
    this.guardando.set(true);
    try {
      if (this.esEdicion()) {
        // La sede no se cambia por PUT: solo descripción y estado.
        await this.catalogo.actualizarPiso(this.id()!, {
          descripcion: this.descripcion.trim(),
          estado: this.estado,
        });
      } else {
        await this.catalogo.registrarPiso({
          idHotel: this.idHotel!,
          descripcion: this.descripcion.trim(),
        });
      }
      this.toast.show('Cambios guardados');
      await this.router.navigateByUrl('/app/pisos');
    } catch (err) {
      const apiErr = parseApiError(err);
      this.error.set(apiErr);
      const e: Record<string, string> = { ...this.errores };
      for (const [campo, motivo] of Object.entries(apiErr.fields)) e[campo] = motivo;
      if (apiErr.code === 'DUPLICATE_RESOURCE') {
        e['descripcion'] = 'Ya existe un piso con esa descripción';
      }
      this.errores = e;
    } finally {
      this.guardando.set(false);
    }
  }

  accionError(): void {
    const err = this.error();
    if (!err) return;
    if (err.relogin) {
      this.session.logout();
      return;
    }
    if (err.reloadable) {
      this.ngOnInit();
      return;
    }
    this.onSubmit(new Event('submit'));
  }
}
