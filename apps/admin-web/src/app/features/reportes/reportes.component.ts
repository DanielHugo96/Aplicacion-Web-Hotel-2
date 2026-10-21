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
import { ActivatedRoute } from '@angular/router';
import { ApiUiError, parseApiError } from '../../core/api-error';
import {
  MetaReporte,
  ModoReporte,
  ReporteBajoStockRow,
  ReporteCobrosRow,
  ReporteOcupacionRow,
  ReporteVentasRow,
} from '../../core/models/reportes.models';
import { PeTimestampPipe } from '../../core/pipes/timestamp.pipe';
import { ReportesService } from '../../core/services/reportes.service';
import { SedeContextService } from '../../core/services/sede-context.service';
import { SessionService } from '../../core/services/session.service';
import { ErrorBannerComponent } from '../../ui/error-banner/error-banner.component';
import { PaginationComponent } from '../../ui/pagination/pagination.component';
import { MetaReporteComponent } from './meta-reporte.component';
import {
  ErroresRango,
  hoyLima,
  rangoIso,
  sumarDias,
  validarFechas,
} from './reportes.filtros';
import {
  ColumnaReporte,
  FilaReporte,
  descargar,
  generarExcel,
  generarPdf,
} from './reportes.export';

type TipoColumna = 'texto' | 'entero' | 'money' | 'fecha' | 'pill';

interface ColumnaVista extends ColumnaReporte {
  tipo: TipoColumna;
}

interface TablaVista {
  columnas: ColumnaVista[];
  filas: FilaReporte[];
}

const TABS: ReadonlyArray<{ modo: ModoReporte; etiqueta: string }> = [
  { modo: 'ventas', etiqueta: 'Ventas' },
  { modo: 'ocupacion', etiqueta: 'Ocupación' },
  { modo: 'cobros', etiqueta: 'Cobros' },
  { modo: 'bajoStock', etiqueta: 'Bajo stock' },
];

const MONEDA = new Intl.NumberFormat('es-PE', {
  style: 'currency',
  currency: 'PEN',
  currencyDisplay: 'symbol',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

/** Mismo formato que `MoneyPipe` (`S/ 1,234.50`); nunca recalcula el importe. */
function formatoMoneda(valor: number): string {
  return MONEDA.format(valor);
}

const TITULOS: Record<ModoReporte, string> = {
  ventas: 'Ventas por producto',
  ocupacion: 'Ocupación por habitación',
  cobros: 'Cobros por cierre',
  bajoStock: 'Productos bajo stock',
};

/** Rangos: solo los tres reportes con fecha la exigen; bajo stock usa `limite`. */
const CON_FECHAS: ReadonlyArray<ModoReporte> = ['ventas', 'ocupacion', 'cobros'];

/**
 * `/app/reportes` (A todas, E asignada): ventas, ocupación, cobros y bajo
 * stock desde el Gateway.
 *
 * Los filtros se construyen en hora de Lima (`reportes.filtros.ts`): `inicio`
 * es medianoche del primer día y `fin` medianoche del día siguiente a `hasta`,
 * de modo que el rango semiabierto `[inicio, fin)` incluye el último día
 * elegido y no depende de la zona de la máquina. Los importes y cifras se
 * muestran tal como llegan; el front no recalcula nada. Sin filas se ve
 * «Sin resultados» (nunca 404) y la meta `EVENTUAL` se muestra con
 * `app-meta-reporte`.
 */
@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [
    FormsModule,
    ErrorBannerComponent,
    PaginationComponent,
    MetaReporteComponent,
    PeTimestampPipe,
  ],
  templateUrl: './reportes.component.html',
  styleUrl: './reportes.component.css',
})
export class ReportesComponent implements OnInit, OnDestroy {
  private readonly reportes = inject(ReportesService);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  readonly sedesContext = inject(SedeContextService);

  readonly tabs = TABS;
  readonly titulos = TITULOS;
  readonly esAdmin = computed(() => this.session.rol() === 'ADMIN');
  readonly sedes = computed(() => this.sedesContext.sedes());
  readonly sedeSeleccionada = computed(() => this.sedesContext.selectedId());
  readonly conFechas = computed(() => CON_FECHAS.includes(this.tab()));

  readonly tab = signal<ModoReporte>('ventas');
  readonly ventas = signal<ReporteVentasRow[]>([]);
  readonly ocupacion = signal<ReporteOcupacionRow[]>([]);
  readonly cobros = signal<ReporteCobrosRow[]>([]);
  readonly bajoStock = signal<ReporteBajoStockRow[]>([]);
  readonly meta = signal<MetaReporte | null>(null);
  readonly loading = signal(true);
  readonly error = signal<ApiUiError | null>(null);
  readonly erroresFiltro = signal<ErroresRango>({});
  readonly page = signal(0);
  readonly size = signal(20);
  readonly total = signal(0);
  readonly esperandoReintento = signal(false);
  readonly exportando = signal(false);

  inicio = sumarDias(hoyLima(), -30);
  hasta = hoyLima();
  limite = 5;

  private temporizadorReintento: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // El selector de sede del topbar manda: cada cambio vuelve a la página 0.
    effect(() => {
      const idHotel = this.sedeSeleccionada();
      untracked(() => {
        if (!idHotel) {
          this.limpiarFilas();
          this.meta.set(null);
          this.total.set(0);
          this.loading.set(false);
          return;
        }
        this.page.set(0);
        this.recargar();
      });
    });
  }

  ngOnInit(): void {
    const pedido = this.route.snapshot.queryParamMap.get('reporte');
    if (pedido && TABS.some((t) => t.modo === pedido)) {
      this.tab.set(pedido as ModoReporte);
    }
  }

  ngOnDestroy(): void {
    if (this.temporizadorReintento) clearTimeout(this.temporizadorReintento);
  }

  cambiarTab(modo: ModoReporte): void {
    if (this.tab() === modo) return;
    this.tab.set(modo);
    this.error.set(null);
    this.erroresFiltro.set({});
    this.page.set(0);
    this.recargar();
  }

  /**
   * `ngModelChange` con el valor `''` (consolidado de A). Se usa `ngModel` y no
   * `[value]` porque el `<select>` se pinta antes que sus `@if`/`@for`, y con
   * `[value]` el control quedaba mostrando la primera opción aunque la consulta
   * ya se hubiera hecho con la sede seleccionada.
   */
  cambiarSedeValor(valor: string | number | null): void {
    this.sedesContext.seleccionar(valor === '' || valor === null ? null : Number(valor));
  }

  aplicar(): void {
    this.page.set(0);
    this.recargar();
  }

  cambiarPagina(page: number): void {
    this.page.set(page);
    void this.cargar();
  }

  accionError(): void {
    const err = this.error();
    if (err?.relogin) {
      this.session.logout();
      return;
    }
    if (this.esperandoReintento()) return;
    void this.cargar();
  }

  /** Botón de exportación: solo con filas y sin carga en curso. */
  puedeExportar(): boolean {
    return this.tabla().filas.length > 0 && !this.loading() && !this.exportando();
  }

  async exportar(formato: 'pdf' | 'excel'): Promise<void> {
    if (!this.puedeExportar()) return;
    this.exportando.set(true);
    try {
      const tabla = this.tabla();
      const opciones = {
        titulo: TITULOS[this.tab()],
        subtitulo: this.subtituloExportacion(),
        columnas: tabla.columnas.map((c) => ({
          clave: c.clave,
          titulo: c.titulo,
          numerica: c.numerica,
          ancho: c.ancho,
        })),
        filas: tabla.filas,
      };
      const blob = formato === 'pdf' ? generarPdf(opciones) : generarExcel(opciones);
      const extension = formato === 'pdf' ? 'pdf' : 'xls';
      descargar(blob, `reporte-${this.tab()}-${this.hasta}.${extension}`);
    } finally {
      this.exportando.set(false);
    }
  }

  etiquetaOcupacion(veces: number | null | undefined): string {
    if (veces === null || veces === undefined) return 'Sin datos';
    return veces > 0 ? 'Ocupada' : 'Libre';
  }

  claseOcupacion(veces: number | null | undefined): string {
    if (veces === null || veces === undefined) return 'pill pill-archivado';
    return veces > 0 ? 'pill pill-ok' : 'pill pill-archivado';
  }

  etiquetaMedioPago(medio: string | null | undefined): string {
    return medio ?? 'No registrado';
  }

  etiquetaEstado(estado: boolean): string {
    return estado ? 'Activo' : 'Dado de baja';
  }

  tieneErroresFiltro(): boolean {
    return Object.keys(this.erroresFiltro()).length > 0;
  }

  /**
   * Los cuatro reportes de lista exigen `idHotel` (`reporting/api.md` § REST
   * legacy); solo el dashboard admite el consolidado de A. Este aviso distingue
   * «no tengo sede asignada» de «he elegido el consolidado y aquí no aplica».
   */
  mensajeSinSede(): string {
    return this.esAdmin()
      ? 'Elige una sede para consultar este reporte'
      : 'Sin sede asignada';
  }

  /** Tabla visible según la pestaña, con sus columnas y filas ya normalizadas. */
  readonly tabla = computed<TablaVista>(() => {
    switch (this.tab()) {
      case 'ventas':
        return {
          columnas: [
            { clave: 'nombreProducto', titulo: 'Producto', tipo: 'texto', ancho: 220 },
            { clave: 'cantidadTotal', titulo: 'Cantidad', tipo: 'entero', numerica: true, ancho: 90 },
            {
              clave: 'totalIngresado',
              titulo: 'Total ingresado',
              tipo: 'money',
              numerica: true,
              ancho: 130,
            },
          ],
          filas: this.ventas().map((f) => ({
            nombreProducto: f.nombreProducto,
            cantidadTotal: f.cantidadTotal,
            totalIngresado: this.moneda(f.totalIngresado),
          })),
        };
      case 'ocupacion':
        return {
          columnas: [
            { clave: 'numeroHabitacion', titulo: 'Habitación', tipo: 'texto', ancho: 90 },
            {
              clave: 'descripcionCategoria',
              titulo: 'Categoría',
              tipo: 'texto',
              ancho: 160,
            },
            {
              clave: 'vecesAlquilada',
              titulo: 'Veces alquilada',
              tipo: 'entero',
              numerica: true,
              ancho: 110,
            },
            { clave: 'uso', titulo: 'Uso en el rango', tipo: 'pill', ancho: 110 },
          ],
          filas: this.ocupacion().map((f) => ({
            numeroHabitacion: f.numeroHabitacion,
            descripcionCategoria: f.descripcionCategoria,
            vecesAlquilada: f.vecesAlquilada,
            uso: this.etiquetaOcupacion(f.vecesAlquilada),
          })),
        };
      case 'cobros':
        return {
          columnas: [
            { clave: 'idRecepcion', titulo: 'Recepción', tipo: 'entero', numerica: true, ancho: 80 },
            { clave: 'numeroHabitacion', titulo: 'Habitación', tipo: 'texto', ancho: 80 },
            { clave: 'nombreCliente', titulo: 'Cliente', tipo: 'texto', ancho: 170 },
            {
              clave: 'totalAlojamiento',
              titulo: 'Alojamiento',
              tipo: 'money',
              numerica: true,
              ancho: 100,
            },
            {
              clave: 'totalConsumos',
              titulo: 'Consumos',
              tipo: 'money',
              numerica: true,
              ancho: 90,
            },
            {
              clave: 'totalGeneral',
              titulo: 'Total',
              tipo: 'money',
              numerica: true,
              ancho: 90,
            },
            {
              clave: 'medioPagoAdelanto',
              titulo: 'Pago adelanto',
              tipo: 'texto',
              ancho: 100,
            },
            { clave: 'medioPagoCierre', titulo: 'Pago cierre', tipo: 'texto', ancho: 100 },
            { clave: 'fechaCierre', titulo: 'Cierre', tipo: 'fecha', ancho: 120 },
          ],
          filas: this.cobros().map((f) => ({
            idRecepcion: f.idRecepcion,
            numeroHabitacion: f.numeroHabitacion,
            nombreCliente: f.nombreCliente,
            totalAlojamiento: this.moneda(f.totalAlojamiento),
            totalConsumos: this.moneda(f.totalConsumos),
            totalGeneral: this.moneda(f.totalGeneral),
            medioPagoAdelanto: this.etiquetaMedioPago(f.medioPagoAdelanto),
            medioPagoCierre: this.etiquetaMedioPago(f.medioPagoCierre),
            fechaCierre: f.fechaCierre,
          })),
        };
      default:
        return {
          columnas: [
            { clave: 'nombreProducto', titulo: 'Producto', tipo: 'texto', ancho: 240 },
            { clave: 'cantidad', titulo: 'Cantidad', tipo: 'entero', numerica: true, ancho: 90 },
            { clave: 'precio', titulo: 'Precio', tipo: 'money', numerica: true, ancho: 100 },
            { clave: 'estado', titulo: 'Estado', tipo: 'texto', ancho: 110 },
          ],
          filas: this.bajoStock().map((f) => ({
            nombreProducto: f.nombreProducto,
            cantidad: f.cantidad,
            precio: this.moneda(f.precio),
            estado: this.etiquetaEstado(f.estado),
          })),
        };
    }
  });

  private moneda(valor: number): string {
    return formatoMoneda(valor);
  }

  private subtituloExportacion(): string {
    const sede =
      this.sedes().find((s) => s.idHotel === this.sedeSeleccionada())?.nombre ??
      (this.esAdmin() ? 'Consolidado de todas las sedes' : 'Sede asignada');
    if (this.tab() === 'bajoStock') return `${sede} · límite ${this.limite}`;
    return `${sede} · del ${this.inicio} al ${this.hasta}`;
  }

  /** Valida y carga la pestaña actual con la página 0. */
  private recargar(): void {
    if (this.tab() === 'bajoStock') {
      this.erroresFiltro.set({});
      void this.cargar();
      return;
    }
    const errores = validarFechas(this.inicio, this.hasta, this.tab() === 'ocupacion');
    this.erroresFiltro.set(errores);
    if (Object.keys(errores).length > 0) {
      this.limpiarFilas();
      this.meta.set(null);
      this.total.set(0);
      this.loading.set(false);
      return;
    }
    void this.cargar();
  }

  /** Lectura real de la pestaña y la página actuales. */
  private async cargar(): Promise<void> {
    if (!this.sedeSeleccionada()) {
      this.limpiarFilas();
      this.meta.set(null);
      this.total.set(0);
      this.loading.set(false);
      return;
    }
    if (this.tab() !== 'bajoStock') {
      const errores = validarFechas(this.inicio, this.hasta, this.tab() === 'ocupacion');
      if (Object.keys(errores).length > 0) {
        this.erroresFiltro.set(errores);
        this.loading.set(false);
        return;
      }
    }
    const sede = this.sedeSeleccionada()!;
    const page = this.page();
    this.loading.set(true);
    this.error.set(null);
    this.esperandoReintento.set(false);
    try {
      const rango = this.tab() === 'bajoStock' ? null : rangoIso(this.inicio, this.hasta);
      if (this.tab() === 'ventas') {
        const res = await this.reportes.ventas(
          sede,
          rango!.inicio,
          rango!.fin,
          page,
          this.size()
        );
        this.ventas.set(res.data);
        this.aplicarMeta(res.meta, page);
      } else if (this.tab() === 'ocupacion') {
        const res = await this.reportes.ocupacion(
          sede,
          rango!.inicio,
          rango!.fin,
          page,
          this.size()
        );
        this.ocupacion.set(res.data);
        this.aplicarMeta(res.meta, page);
      } else if (this.tab() === 'cobros') {
        const res = await this.reportes.cobros(
          sede,
          rango!.inicio,
          rango!.fin,
          page,
          this.size()
        );
        this.cobros.set(res.data);
        this.aplicarMeta(res.meta, page);
      } else {
        const res = await this.reportes.bajoStock(sede, this.limite, page, this.size());
        this.bajoStock.set(res.data);
        this.aplicarMeta(res.meta, page);
      }
    } catch (err) {
      const api = parseApiError(err);
      this.error.set(api);
      this.limpiarFilas();
      this.meta.set(null);
      this.total.set(0);
      if (api.retryAfterMs && api.retryAfterMs > 0) {
        this.esperarReintento(api.retryAfterMs);
      }
    } finally {
      this.loading.set(false);
    }
  }

  private aplicarMeta(meta: MetaReporte, page: number): void {
    this.meta.set(meta);
    this.page.set(meta.page ?? page);
    this.size.set(meta.size ?? this.size());
    this.total.set(meta.totalElements ?? 0);
  }

  private limpiarFilas(): void {
    this.ventas.set([]);
    this.ocupacion.set([]);
    this.cobros.set([]);
    this.bajoStock.set([]);
  }

  /** `Retry-After` del 503: el botón de reintento espera sin disparar la GET. */
  private esperarReintento(ms: number): void {
    if (this.temporizadorReintento) clearTimeout(this.temporizadorReintento);
    this.esperandoReintento.set(true);
    this.temporizadorReintento = setTimeout(() => {
      this.esperandoReintento.set(false);
      this.temporizadorReintento = null;
    }, ms);
  }
}
