import { Component, computed, effect, inject, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../core/api-error';
import { MetaReporte, RespuestaDashboard } from '../../core/models/reportes.models';
import { MoneyPipe } from '../../core/pipes/money.pipe';
import { ReportesService } from '../../core/services/reportes.service';
import { SedeContextService } from '../../core/services/sede-context.service';
import { SessionService } from '../../core/services/session.service';
import { ErrorBannerComponent } from '../../ui/error-banner/error-banner.component';
import { MetaReporteComponent } from '../reportes/meta-reporte.component';

/**
 * `/app/inicio` (A/E): KPIs del backend para la sede seleccionada.
 *
 * ADMIN puede leer el consolidado (sin `idHotel`) o una sede concreta;
 * EMPLEADO siempre su sede asignada. Los importes llegan calculados y se
 * muestran tal cual con `MoneyPipe`; el front no recalcula ninguna métrica.
 * La meta `EVENTUAL` se enseña con `app-meta-reporte` y el 503 usa el mensaje
 * del catálogo con reintento de la misma lectura.
 */
@Component({
  selector: 'app-inicio',
  standalone: true,
  imports: [FormsModule, RouterLink, ErrorBannerComponent, MetaReporteComponent, MoneyPipe],
  templateUrl: './inicio.component.html',
  styleUrl: './inicio.component.css',
})
export class InicioComponent {
  private readonly reportes = inject(ReportesService);
  private readonly session = inject(SessionService);
  readonly sedesContext = inject(SedeContextService);

  readonly esAdmin = computed(() => this.session.rol() === 'ADMIN');
  readonly sedes = computed(() => this.sedesContext.sedes());
  readonly sedeSeleccionada = computed(() => this.sedesContext.selectedId());

  readonly dashboard = signal<RespuestaDashboard['data']>(null);
  readonly meta = signal<MetaReporte | null>(null);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  /** `true` si ya se pidió al menos una lectura: distingue vacío real de carga. */
  readonly leido = signal(false);

  constructor() {
    effect(() => {
      const idHotel = this.sedeSeleccionada();
      const admin = this.esAdmin();
      untracked(() => {
        if (!admin && !idHotel) {
          this.dashboard.set(null);
          this.loading.set(false);
          return;
        }
        void this.cargar(admin ? idHotel : idHotel);
      });
    });
  }

  async cargar(idHotel: number | null = this.sedeSeleccionada()): Promise<void> {
    // E sin sede asignada no pide nada: el backend exige idHotel para su rol.
    if (!this.esAdmin() && !idHotel) {
      this.dashboard.set(null);
      this.meta.set(null);
      this.loading.set(false);
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    try {
      const res = await this.reportes.dashboard(this.esAdmin() ? idHotel : idHotel);
      this.dashboard.set(res.data);
      this.meta.set(res.meta);
      this.leido.set(true);
    } catch (err) {
      this.error.set(parseApiError(err));
      this.dashboard.set(null);
      this.meta.set(null);
    } finally {
      this.loading.set(false);
    }
  }

  /**
   * `ngModelChange` con el valor `''` (consolidado de A). Se usa `ngModel` y no
   * `[value]` porque el `<select>` se pinta antes que sus `@if`/`@for`, y con
   * `[value]` el control quedaba mostrando la primera opción mientras los KPIs
   * eran de la sede ya seleccionada.
   */
  cambiarSedeValor(valor: string | number | null): void {
    this.sedesContext.seleccionar(valor === '' || valor === null ? null : Number(valor));
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    void this.cargar();
  }

  /** Vacío exacto de `08-admin-web.md` §4: sin KPIs del backend para la sede. */
  get vacio(): boolean {
    return this.leido() && !this.error() && !this.loading() && this.dashboard() === null;
  }
}
