import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { Habitacion, ImagenHabitacion } from '../../../core/models/catalogo.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { SessionService } from '../../../core/services/session.service';
import { ImagenPanelComponent } from '../../../shared/imagen-panel.component';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';

/**
 * `/app/habitaciones/:id` (A/E): detalle de la habitación con su galería
 * completa en el orden guardado. Es la pantalla que [`08` §6](../../../../../docs/especificacion/08-admin-web.md#6-imágenes-en-el-panel)
 * llama «Habitación (detalle)»: relación fija por imagen, `alt` propio del
 * contrato y la principal con prioridad alta (nunca `loading="lazy"`).
 *
 * Sin galería, la pantalla lo dice y no deja un hueco gris sin explicación; el
 * `alt` de cada imagen sale de `urlsImagenes[].alt`, nunca de la URL.
 */
@Component({
  selector: 'app-habitacion-detalle',
  standalone: true,
  imports: [RouterLink, ErrorBannerComponent, ImagenPanelComponent, MoneyPipe],
  templateUrl: './habitacion-detalle.component.html',
  styleUrl: './habitacion-detalle.component.css',
})
export class HabitacionDetalleComponent implements OnInit {
  private readonly catalogo = inject(CatalogoService);
  private readonly session = inject(SessionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly id = signal<number | null>(null);
  readonly habitacion = signal<Habitacion | null>(null);
  readonly cargando = signal(true);
  readonly error = signal<ApiUiError | null>(null);

  /** Galería completa ordenada por `orden`, como la guardó el contrato. */
  readonly galeria = computed<ImagenHabitacion[]>(() =>
    [...(this.habitacion()?.urlsImagenes ?? [])].sort(
      (a, b) => Number(a.orden) - Number(b.orden)
    )
  );

  readonly titulo = computed(() => {
    const h = this.habitacion();
    return h ? `Habitación ${h.numero}` : 'Habitación';
  });

  async ngOnInit(): Promise<void> {
    const idParam = this.route.snapshot.paramMap.get('id');
    const id = Number(idParam);
    if (!idParam || Number.isNaN(id)) {
      this.error.set(parseApiError(new Error('Habitación no encontrada')));
      this.cargando.set(false);
      return;
    }
    this.id.set(id);
    await this.cargar();
  }

  async cargar(): Promise<void> {
    const id = this.id();
    if (id === null) return;
    this.cargando.set(true);
    this.error.set(null);
    try {
      const res = await this.catalogo.buscarHabitacion(id);
      this.habitacion.set(res.data);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.habitacion.set(null);
    } finally {
      this.cargando.set(false);
    }
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    void this.cargar();
  }

  actionLabel(): string | null {
    const err = this.error();
    if (!err) return null;
    if (err.relogin) return 'Iniciar sesión de nuevo';
    if (err.retryable || !err.reloadable) return 'Reintentar';
    return 'Recargar';
  }

  editar(): void {
    const id = this.id();
    if (id === null) return;
    void this.router.navigateByUrl(`/app/habitaciones/${id}/editar`);
  }
}
