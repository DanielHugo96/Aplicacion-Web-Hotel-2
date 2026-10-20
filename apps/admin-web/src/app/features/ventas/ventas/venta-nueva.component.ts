import {
  Component,
  computed,
  effect,
  inject,
  OnDestroy,
  OnInit,
  signal,
  untracked,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ApiUiError, parseApiError } from '../../../core/api-error';
import { EstadoVenta, MedioPago, CarritoItem, Producto } from '../../../core/models/ventas.models';
import { Recepcion } from '../../../core/models/recepcion.models';
import { MoneyPipe } from '../../../core/pipes/money.pipe';
import { RecepcionService } from '../../../core/services/recepcion.service';
import { SedeContextService } from '../../../core/services/sede-context.service';
import { SessionService } from '../../../core/services/session.service';
import { ToastService } from '../../../core/services/toast.service';
import { VentasService } from '../../../core/services/ventas.service';
import { ErrorBannerComponent } from '../../../ui/error-banner/error-banner.component';
import { PendingBannerComponent } from '../../../ui/pending-banner/pending-banner.component';

const MAX_PRODUCTOS = 50;
const MAX_CANTIDAD = 9999;

/**
 * `/app/ventas/nueva` (A/E): elige una estadía ACTIVA de la sede (o llega por
 * `idRecepcion` desde el detalle), carga el carrito y el catálogo de esa
 * estadía, agrega/quita líneas con el contrato y confirma con un cuerpo JSON
 * cerrado (`idRecepcion`, `estado`, `medioPago` opcional y `detalles`). No
 * envía precios, subtotales, totales, `idHotel`, `idCliente` ni
 * `estadoOperacion`: los calcula el backend.
 *
 * Cambiar la cantidad de una línea es quitar y volver a agregar, como fija la
 * spec. Un 202 abre el banner persistente y sondea hasta el terminal; nunca se
 * muestra éxito optimista.
 */
@Component({
  selector: 'app-venta-nueva',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    ErrorBannerComponent,
    PendingBannerComponent,
    MoneyPipe,
  ],
  templateUrl: './venta-nueva.component.html',
})
export class VentaNuevaComponent implements OnInit, OnDestroy {
  private readonly ventasService = inject(VentasService);
  private readonly recepcionService = inject(RecepcionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly session = inject(SessionService);
  private readonly toast = inject(ToastService);
  readonly sedesContext = inject(SedeContextService);

  readonly sedeSeleccionada = computed(() => this.sedesContext.selectedId());
  readonly estadias = signal<Recepcion[]>([]);
  readonly idRecepcion = signal<number | null>(null);
  readonly carrito = signal<CarritoItem[]>([]);
  readonly catalogo = signal<Producto[]>([]);
  readonly cargandoEstadias = signal(true);
  readonly cargandoCarrito = signal(false);
  readonly cargandoCatalogo = signal(false);
  readonly guardando = signal(false);
  readonly error = signal<ApiUiError | null>(null);
  readonly errorCatalogo = signal<ApiUiError | null>(null);
  readonly erroresLinea = signal<Record<number, string>>({});
  readonly erroresCampo = signal<Record<string, string>>({});
  readonly pendiente = signal(false);
  readonly detallePendiente = signal('');
  readonly actualizadoEn = signal<Date | null>(null);

  estado: EstadoVenta = 'PENDIENTE';
  medioPago: MedioPago | '' = '';

  readonly puedeConfirmar = computed(
    () =>
      !!this.idRecepcion() &&
      this.carrito().length > 0 &&
      !this.guardando() &&
      !this.pendiente()
  );

  private abortador: AbortController | null = null;

  constructor() {
    effect(() => {
      const idHotel = this.sedeSeleccionada();
      untracked(() => {
        if (!idHotel) {
          this.estadias.set([]);
          this.idRecepcion.set(null);
          this.carrito.set([]);
          this.catalogo.set([]);
          this.cargandoEstadias.set(false);
          return;
        }
        void this.cargarEstadias(idHotel);
      });
    });
  }

  ngOnInit(): void {
    const porQuery = this.route.snapshot.queryParamMap.get('idRecepcion');
    if (porQuery) this.idRecepcion.set(Number(porQuery));
  }

  ngOnDestroy(): void {
    this.abortador?.abort();
  }

  async cargarEstadias(idHotel = this.sedeSeleccionada()): Promise<void> {
    if (!idHotel) return;
    this.cargandoEstadias.set(true);
    this.error.set(null);
    try {
      const res = await this.recepcionService.listarEstadias(idHotel, 0, 100, 'ACTIVA');
      this.estadias.set(res.data);
      const actual = this.idRecepcion();
      const elegida =
        actual !== null && res.data.some((e) => e.idRecepcion === actual)
          ? actual
          : (res.data[0]?.idRecepcion ?? null);
      if (elegida !== null) await this.seleccionarEstadia(elegida, false);
      else {
        this.idRecepcion.set(null);
        this.carrito.set([]);
        this.catalogo.set([]);
      }
    } catch (err) {
      this.error.set(parseApiError(err));
      this.estadias.set([]);
    } finally {
      this.cargandoEstadias.set(false);
    }
  }

  async seleccionarEstadia(idRecepcion: number, recargar = true): Promise<void> {
    this.idRecepcion.set(idRecepcion);
    this.carrito.set([]);
    this.catalogo.set([]);
    this.erroresLinea.set({});
    this.erroresCampo.set({});
    if (recargar) await this.cargarCarritoYCatalogo();
    else await this.cargarCarritoYCatalogo();
  }

  cambiarEstadia(event: Event): void {
    const value = Number((event.target as HTMLSelectElement).value);
    if (value) void this.seleccionarEstadia(value);
  }

  async cargarCarritoYCatalogo(): Promise<void> {
    const idRecepcion = this.idRecepcion();
    if (!idRecepcion) return;
    await Promise.allSettled([this.cargarCarrito(idRecepcion), this.cargarCatalogo(idRecepcion)]);
  }

  async cargarCarrito(idRecepcion = this.idRecepcion()): Promise<void> {
    if (!idRecepcion) return;
    this.cargandoCarrito.set(true);
    this.error.set(null);
    try {
      this.carrito.set(await this.ventasService.listarCarrito(idRecepcion));
    } catch (err) {
      this.error.set(parseApiError(err));
      this.carrito.set([]);
    } finally {
      this.cargandoCarrito.set(false);
    }
  }

  async cargarCatalogo(idRecepcion = this.idRecepcion()): Promise<void> {
    if (!idRecepcion) return;
    this.cargandoCatalogo.set(true);
    this.errorCatalogo.set(null);
    try {
      const res = await this.ventasService.listarCatalogo(idRecepcion, 0, 100);
      this.catalogo.set(res.data);
    } catch (err) {
      this.errorCatalogo.set(parseApiError(err));
      this.catalogo.set([]);
    } finally {
      this.cargandoCatalogo.set(false);
    }
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    if (this.idRecepcion()) void this.cargarCarrito();
    else void this.cargarEstadias();
  }

  accionErrorCatalogo(): void {
    void this.cargarCatalogo();
  }

  lineaConProducto(idProducto: number): CarritoItem | undefined {
    return this.carrito().find((c) => c.idProducto === idProducto);
  }

  async agregar(p: Producto): Promise<void> {
    const idRecepcion = this.idRecepcion();
    if (!idRecepcion || this.guardando() || this.pendiente()) return;
    this.error.set(null);
    this.erroresLinea.set({});
    const existente = this.lineaConProducto(p.idProducto);
    if (!existente && this.carrito().length >= MAX_PRODUCTOS) {
      this.erroresCampo.set({ carrito: 'Máximo 50 productos distintos' });
      return;
    }
    try {
      await this.ventasService.agregarAlCarrito(idRecepcion, p.idProducto, 1);
      await this.cargarCarrito(idRecepcion);
    } catch (err) {
      this.error.set(parseApiError(err));
    }
  }

  async quitar(item: CarritoItem): Promise<void> {
    const idRecepcion = this.idRecepcion();
    if (!idRecepcion || this.guardando() || this.pendiente()) return;
    this.error.set(null);
    try {
      await this.ventasService.quitarDelCarrito(idRecepcion, item.idProducto);
      await this.cargarCarrito(idRecepcion);
    } catch (err) {
      this.error.set(parseApiError(err));
    }
  }

  async cambiarCantidad(item: CarritoItem, cantidad: number): Promise<void> {
    const idRecepcion = this.idRecepcion();
    if (!idRecepcion || this.pendiente()) return;
    const valor = Number(cantidad);
    if (!Number.isInteger(valor) || valor < 1 || valor > MAX_CANTIDAD) {
      this.erroresLinea.set({ [item.idProducto]: 'Cantidad inválida' });
      return;
    }
    if (valor === item.cantidad) return;
    this.erroresLinea.set({});
    this.error.set(null);
    try {
      await this.ventasService.quitarDelCarrito(idRecepcion, item.idProducto);
      await this.ventasService.agregarAlCarrito(idRecepcion, item.idProducto, valor);
      await this.cargarCarrito(idRecepcion);
    } catch (err) {
      this.error.set(parseApiError(err));
    }
  }

  campoInvalido(campo: string): boolean {
    return !!this.erroresCampo()[campo];
  }

  mensajeCampo(campo: string): string {
    return this.erroresCampo()[campo] ?? '';
  }

  async confirmar(event: Event): Promise<void> {
    event.preventDefault();
    const idRecepcion = this.idRecepcion();
    if (!idRecepcion || !this.puedeConfirmar()) return;
    const errores: Record<string, string> = {};
    if (this.carrito().length === 0) errores['carrito'] = 'Agrega al menos una línea';
    if (this.estado === 'PAGADO' && !this.medioPago) {
      errores['medioPago'] = 'Selecciona el medio de pago';
    }
    this.erroresCampo.set(errores);
    if (Object.keys(errores).length > 0) return;

    this.guardando.set(true);
    this.error.set(null);
    this.erroresLinea.set({});
    try {
      const res = await this.ventasService.crearVenta({
        idRecepcion,
        estado: this.estado,
        ...(this.estado === 'PAGADO' ? { medioPago: this.medioPago as MedioPago } : {}),
        detalles: this.carrito().map((c) => ({ idProducto: c.idProducto, cantidad: c.cantidad })),
      });
      if (res.kind === 'pendiente') {
        this.iniciarPolling(res.location, res.retryAfterMs, res.venta.idVenta);
      } else {
        this.toast.show('Venta registrada');
        await this.router.navigateByUrl(`/app/ventas/${res.venta.idVenta}`);
      }
    } catch (err) {
      const api = parseApiError(err);
      this.error.set(api);
      this.marcarLineas(api);
    } finally {
      this.guardando.set(false);
    }
  }

  private marcarLineas(api: ApiUiError): void {
    const marcadas: Record<number, string> = {};
    for (const [campo, mensaje] of Object.entries(api.fields)) {
      const porIndice = /detalles[\[.](\d+)/.exec(campo);
      if (porIndice) {
        const item = this.carrito()[Number(porIndice[1])];
        if (item) marcadas[item.idProducto] = mensaje;
        continue;
      }
      if (campo === 'idProducto') {
        const id = Number(api.fields[campo]);
        if (Number.isFinite(id)) marcadas[id] = mensaje;
        continue;
      }
      if (campo === 'cantidad') {
        for (const item of this.carrito()) marcadas[item.idProducto] = mensaje;
        continue;
      }
      this.erroresCampo.update((actual) => ({ ...actual, [campo]: mensaje }));
    }
    this.erroresLinea.set(marcadas);
  }

  private iniciarPolling(location: string, retryAfterMs: number, idVenta: number): void {
    this.abortador?.abort();
    const abortador = new AbortController();
    this.abortador = abortador;
    this.pendiente.set(true);
    this.detallePendiente.set('La venta se está confirmando; el estado se actualizará solo.');
    this.actualizadoEn.set(null);
    void this.ventasService
      .seguirOperacion(
        location,
        retryAfterMs,
        (avance) => {
          this.actualizadoEn.set(avance.actualizadoEn);
        },
        abortador.signal
      )
      .then(async (ultimo) => {
        if (abortador.signal.aborted || !ultimo) return;
        this.pendiente.set(false);
        this.toast.show('Venta registrada');
        await this.router.navigateByUrl(`/app/ventas/${ultimo.venta?.idVenta ?? idVenta}`);
      })
      .catch((err) => {
        if (abortador.signal.aborted) return;
        this.pendiente.set(false);
        this.error.set(parseApiError(err));
      });
  }
}
