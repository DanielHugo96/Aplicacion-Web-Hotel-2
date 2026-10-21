/**
 * Modelos de reportes del panel: mismos nombres, campos y obligatoriedad que
 * los schemas de `docs/contracts/openapi/admin-web.yaml` (Dashboard,
 * ReporteBajoStockRow, ReporteVentasRow, ReporteOcupacionRow,
 * ReporteCobrosRow y ApiMetaReporte).
 *
 * El front no recalcula importes ni métricas: muestra lo que devuelve el
 * backend con su meta de consistencia eventual.
 */

export type ConsistenciaReporte = 'EVENTUAL';
export type MedioPagoReporte = 'EFECTIVO' | 'TARJETA' | 'OTRO';

export interface MetaReporte {
  updatedAt: string | null;
  consistency: ConsistenciaReporte;
  degraded: boolean;
  bootstrapComplete: boolean;
  correlationId: string;
  page?: number;
  size?: number;
  totalElements?: number;
}

export interface DashboardReporte {
  habitacionesOcupadas: number;
  habitacionesDisponibles: number;
  productosBajoStock: number;
  ingresosHoy: number;
  moneda: 'PEN';
}

export interface ReporteVentasRow {
  idProducto: number;
  nombreProducto: string;
  cantidadTotal: number;
  totalIngresado: number;
}

export interface ReporteOcupacionRow {
  idHabitacion: number;
  numeroHabitacion: string;
  descripcionCategoria: string;
  vecesAlquilada: number;
}

export interface ReporteCobrosRow {
  idRecepcion: number;
  numeroHabitacion: string;
  nombreCliente: string;
  totalAlojamiento: number;
  totalConsumos: number;
  totalGeneral: number;
  /** `null` = NO_REGISTRADO (histórico migrado sin dato). */
  medioPagoAdelanto: MedioPagoReporte | null;
  medioPagoCierre: MedioPagoReporte | null;
  fechaCierre: string;
}

export interface ReporteBajoStockRow {
  idProducto: number;
  nombreProducto: string;
  cantidad: number;
  precio: number;
  estado: boolean;
}

/** Respuesta de un reporte con su meta EVENTUAL y su paginación `L`. */
export interface PaginaReporte<T> {
  data: T[];
  meta: MetaReporte;
}

/** Respuesta del dashboard: un objeto con su meta EVENTUAL. */
export interface RespuestaDashboard {
  data: DashboardReporte | null;
  meta: MetaReporte;
}

export type ModoReporte = 'ventas' | 'ocupacion' | 'cobros' | 'bajoStock';
