import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { CatalogoService } from '../../../core/services/catalogo.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { TipoPersona } from '../../../core/models/catalogo.models';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';

/**
 * Catálogo fijo de roles: solo se edita la etiqueta. No hay botones ni
 * llamadas de crear/eliminar; el backend lo bloquearía con FIXED_ROLE_CATALOG.
 */
@Component({
  selector: 'app-tipos-persona',
  standalone: true,
  imports: [FormsModule, ErrorBannerComponent],
  templateUrl: './tipos-persona.component.html',
  styleUrl: './tipos-persona.component.css',
})
export class TiposPersonaComponent implements OnInit {
  private readonly catalogo = inject(CatalogoService);
  private readonly session = inject(SessionService);
  private readonly toast = inject(ToastService);

  readonly esAdmin = computed(() => this.session.rol() === 'ADMIN');
  readonly tipos = signal<TipoPersona[]>([]);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly editando = signal<number | null>(null);
  readonly descripcion = signal('');
  readonly guardando = signal(false);

  ngOnInit(): void {
    void this.cargar();
  }

  async cargar(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.tipos.set(await this.catalogo.listarTiposPersona());
    } catch (err) {
      this.error.set(parseApiError(err));
      this.tipos.set([]);
    } finally {
      this.loading.set(false);
    }
  }

  editar(tipo: TipoPersona): void {
    this.editando.set(tipo.idTipoPersona);
    this.descripcion.set(tipo.descripcion);
  }

  cancelar(): void {
    this.editando.set(null);
    this.descripcion.set('');
  }

  async guardar(tipo: TipoPersona): Promise<void> {
    const descripcion = this.descripcion().trim();
    if (!descripcion) return;
    this.guardando.set(true);
    this.error.set(null);
    try {
      // Se busca el recurso para obtener el ETag vigente antes del PUT.
      await this.catalogo.buscarTipoPersona(tipo.idTipoPersona);
      await this.catalogo.actualizarTipoPersona(tipo.idTipoPersona, descripcion);
      this.toast.show('Cambios guardados');
      this.cancelar();
      await this.cargar();
    } catch (err) {
      this.error.set(parseApiError(err));
    } finally {
      this.guardando.set(false);
    }
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    if (err?.reloadable) {
      this.cancelar();
      this.cargar();
      return;
    }
    this.cargar();
  }
}
