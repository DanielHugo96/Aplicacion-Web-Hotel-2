import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { CarritoItem } from '../../../core/models/cliente.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { PeTimestampPipe } from '../../../core/pipes/timestamp.pipe';
import { ClienteContextService } from '../../../core/services/cliente-context.service';
import { ClienteService } from '../../../core/services/cliente.service';
import { ToastService } from '../../../core/services/toast.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';

interface ErrorLinea {
  message: string;
  action: string;
  correlationId: string;
}

interface Pendiente {
  idVenta: number;
  location: string;
  retryAfterMs: number;
  actualizadoEn: Date;
  estado: string | null;
  estadoOperacion: string | null;
}

/**
 * `/mi-estadia/carrito`: líneas de la estadía ACTIVA, stepper 1..9999 con
 * cambio de cantidad = eliminar + agregar, quitar línea y creación de venta
 * PENDIENTE. Tras un 202 el banner y el polling son explícitos: nunca hay
 * éxito optimista y las acciones que duplicarían la intención se deshabilitan.
 */
@Component({
  selector: 'app-carrito-cliente',
  standalone: true,
  imports: [RouterLink, ErrorBannerComponent, MoneyPipe, PeTimestampPipe],
  templateUrl: './carrito.component.html',
  styleUrl: './carrito.component.css',
})
export class CarritoComponent implements OnInit, OnDestroy {
  private readonly contexto = inject(ClienteContextService);
  private readonly cliente = inject(ClienteService);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);

  readonly items = signal<CarritoItem[]>([]);
  readonly cargando = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly erroresLinea = signal<Record<number, ErrorLinea>>({});
  readonly errorCantidad = signal<string | null>(null);
  readonly ocupada = signal<number | null>(null);
  readonly confirmando = signal(false);
  readonly pendiente = signal<Pendiente | null>(null);
  readonly resultado = signal<'rechazada' | null>(null);

  private readonly abort = new AbortController();

  async ngOnInit(): Promise<void> {
    await this.cargar();
  }

  ngOnDestroy(): void {
    this.abort.abort();
  }

  get sinEstadia(): boolean {
    return this.contexto.activa() === null;
  }

  async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    this.erroresLinea.set({});
    this.errorCantidad.set(null);
    await this.contexto.cargar();
    const errorContexto = this.contexto.error();
    if (errorContexto) {
      this.error.set(errorContexto);
      this.cargando.set(false);
      return;
    }
    const activa = this.contexto.activa();
    if (!activa) {
      this.items.set([]);
      this.cargando.set(false);
      return;
    }
    try {
      this.items.set(await this.cliente.listarCarrito(activa.idRecepcion));
    } catch (err) {
      this.error.set(parseApiError(err));
      this.items.set([]);
    } finally {
      this.cargando.set(false);
    }
  }

  errorDe(idProducto: number): ErrorLinea | null {
    return this.erroresLinea()[idProducto] ?? null;
  }

  estaOcupada(idProducto: number): boolean {
    return this.ocupada() === idProducto;
  }

  async fijarCantidad(item: CarritoItem, nueva: number): Promise<void> {
    const activa = this.contexto.activa();
    if (!activa || this.ocupada() !== null || this.confirmando() || this.pendiente()) return;
    if (!Number.isFinite(nueva) || nueva < 1 || nueva > 9999) {
      this.errorCantidad.set('La cantidad debe estar entre 1 y 9999.');
      return;
    }
    if (nueva === item.cantidad) return;

    this.ocupada.set(item.idProducto);
    this.errorCantidad.set(null);
    this.limpiarErrorLinea(item.idProducto);
    try {
      await this.cliente.quitarDelCarrito(activa.idRecepcion, item.idProducto);
      const actualizado = await this.cliente.agregarAlCarrito(
        activa.idRecepcion,
        item.idProducto,
        nueva
      );
      this.items.update((items) =>
        items.map((i) => (i.idProducto === item.idProducto ? actualizado : i))
      );
    } catch (err) {
      this.aplicarError(err, item.idProducto);
      await this.recargarSilencioso();
    } finally {
      this.ocupada.set(null);
    }
  }

  async cambiarCantidad(item: CarritoItem, delta: number): Promise<void> {
    await this.fijarCantidad(item, item.cantidad + delta);
  }

  async onCantidadInput(item: CarritoItem, event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const valor = Number(input.value);
    if (!Number.isFinite(valor) || valor < 1 || valor > 9999) {
      input.value = String(item.cantidad);
      this.errorCantidad.set('La cantidad debe estar entre 1 y 9999.');
      return;
    }
    await this.fijarCantidad(item, valor);
  }

  async quitar(item: CarritoItem): Promise<void> {
    const activa = this.contexto.activa();
    if (!activa || this.ocupada() !== null || this.confirmando() || this.pendiente()) return;
    this.ocupada.set(item.idProducto);
    this.limpiarErrorLinea(item.idProducto);
    try {
      await this.cliente.quitarDelCarrito(activa.idRecepcion, item.idProducto);
      this.items.update((items) => items.filter((i) => i.idProducto !== item.idProducto));
    } catch (err) {
      this.aplicarError(err, item.idProducto);
    } finally {
      this.ocupada.set(null);
    }
  }

  async confirmar(): Promise<void> {
    const activa = this.contexto.activa();
    if (
      !activa ||
      this.items().length === 0 ||
      this.confirmando() ||
      this.pendiente() ||
      this.ocupada() !== null
    ) {
      return;
    }
    this.confirmando.set(true);
    this.error.set(null);
    this.erroresLinea.set({});
    this.errorCantidad.set(null);
    this.resultado.set(null);

    const body = {
      idRecepcion: activa.idRecepcion,
      estado: 'PENDIENTE' as const,
      detalles: this.items().map((i) => ({ idProducto: i.idProducto, cantidad: i.cantidad })),
    };

    try {
      const resultado = await this.cliente.crearVenta(body);
      if (resultado.kind === 'confirmada') {
        this.toast.show('Venta registrada');
        this.items.set([]);
        await this.router.navigateByUrl('/mis-ventas');
        return;
      }
      this.pendiente.set({
        idVenta: resultado.venta.idVenta,
        location: resultado.location,
        retryAfterMs: resultado.retryAfterMs,
        actualizadoEn: new Date(),
        estado: null,
        estadoOperacion: resultado.venta.estadoOperacion,
      });
      await this.seguirSondeo(resultado.location, resultado.retryAfterMs);
    } catch (err) {
      this.aplicarError(err, null);
    } finally {
      this.confirmando.set(false);
    }
  }

  /** Reanuda el sondeo si la red falló mientras la operación seguía en curso. */
  async reanudar(): Promise<void> {
    const p = this.pendiente();
    if (!p) return;
    this.error.set(null);
    await this.seguirSondeo(p.location, 0);
  }

  async copiar(texto: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(texto);
      this.toast.show('Copiado');
    } catch {
      // El navegador puede bloquear el portapapeles; el texto sigue visible.
    }
  }

  private async seguirSondeo(location: string, retryAfterMs: number): Promise<void> {
    try {
      const avance = await this.cliente.seguirOperacion(
        location,
        retryAfterMs,
        (a) => {
          this.pendiente.update((p) =>
            p
              ? {
                  ...p,
                  actualizadoEn: a.actualizadoEn,
                  estado: a.operacion?.estado ?? p.estado,
                  estadoOperacion: a.venta?.estadoOperacion ?? p.estadoOperacion,
                }
              : p
          );
        },
        this.abort.signal
      );
      if (!avance || this.abort.signal.aborted) return;

      const estadoOperacion = avance.venta?.estadoOperacion ?? null;
      this.pendiente.set(null);
      if (estadoOperacion === 'CONFIRMADA') {
        this.toast.show('Venta registrada');
        this.items.set([]);
        await this.router.navigateByUrl('/mis-ventas');
      } else {
        this.resultado.set('rechazada');
      }
    } catch (err) {
      this.error.set(parseApiError(err));
    }
  }

  private aplicarError(err: unknown, idProducto: number | null): void {
    const api = parseApiError(err);
    if (api.code === 'STOCK_INSUFFICIENT' && this.items().length === 1) {
      const unico = this.items()[0].idProducto;
      this.erroresLinea.update((m) => ({
        ...m,
        [unico]: {
          message: api.message,
          action: api.action,
          correlationId: api.correlationId,
        },
      }));
      return;
    }
    if (api.code === 'VALIDATION_ERROR' && api.fields['cantidad']) {
      this.errorCantidad.set(api.fields['cantidad']);
      this.error.set(api);
      return;
    }
    const campoProducto = Number(api.fields['idProducto']);
    const idLinea =
      idProducto ??
      (Number.isFinite(campoProducto) && campoProducto > 0 ? campoProducto : null);
    if (idLinea !== null) {
      this.erroresLinea.update((m) => ({
        ...m,
        [idLinea]: {
          message: api.message,
          action: api.action,
          correlationId: api.correlationId,
        },
      }));
      return;
    }
    if (api.fields['cantidad']) {
      this.errorCantidad.set(api.fields['cantidad']);
      return;
    }
    this.error.set(api);
  }

  private limpiarErrorLinea(idProducto: number): void {
    this.erroresLinea.update((m) => {
      if (!(idProducto in m)) return m;
      const copia = { ...m };
      delete copia[idProducto];
      return copia;
    });
  }

  private async recargarSilencioso(): Promise<void> {
    const activa = this.contexto.activa();
    if (!activa) return;
    try {
      this.items.set(await this.cliente.listarCarrito(activa.idRecepcion));
    } catch {
      // El error del cambio de cantidad ya está visible en la línea.
    }
  }
}
