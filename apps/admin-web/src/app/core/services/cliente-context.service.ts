import { computed, inject, Injectable, signal } from '@angular/core';
import { ApiUiError, parseApiError } from '../api-error';
import { Recepcion } from '../models/cliente.models';
import { ClienteService } from './cliente.service';
import { SessionService } from './session.service';

/**
 * Contexto del área de cliente: lee las estadías propias usando el `sub` del
 * JWT (nunca un id elegido por el usuario) y expone la estadía ACTIVA que
 * comparten mi estadía, catálogo, carrito y mis ventas.
 */
@Injectable({ providedIn: 'root' })
export class ClienteContextService {
  private readonly cliente = inject(ClienteService);
  private readonly session = inject(SessionService);

  readonly cargando = signal(false);
  readonly error = signal<ApiUiError | null>(null);
  readonly estadias = signal<Recepcion[]>([]);
  readonly idCliente = this.session.idCliente;

  readonly activa = computed(
    () => this.estadias().find((e) => e.estadoEstadia === 'ACTIVA') ?? null
  );

  private cargadaPara: number | null = null;

  async cargar(forzar = false): Promise<void> {
    const idCliente = this.idCliente();
    if (!idCliente) {
      this.error.set({
        code: 'UNAUTHENTICATED',
        message: 'Tu sesión expiró. Vuelve a iniciar sesión.',
        action: 'Iniciar sesión de nuevo.',
        fields: {},
        correlationId: '',
        retryable: false,
        reloadable: false,
        relogin: true,
        retryAfterMs: null,
      });
      return;
    }
    if (this.cargando()) return;
    if (!forzar && this.cargadaPara === idCliente) return;

    this.cargando.set(true);
    this.error.set(null);
    try {
      const pagina = await this.cliente.listarEstadiasPropias(idCliente, 0, 100);
      const ordenadas = [...pagina.data].sort((a, b) =>
        (b.fechaEntrada ?? '').localeCompare(a.fechaEntrada ?? '')
      );
      this.estadias.set(ordenadas);
      this.cargadaPara = idCliente;
    } catch (err) {
      this.error.set(parseApiError(err));
    } finally {
      this.cargando.set(false);
    }
  }

  /** Descarta lo cargado (p. ej. al cerrar sesión). */
  limpiar(): void {
    this.estadias.set([]);
    this.error.set(null);
    this.cargadaPara = null;
  }
}
